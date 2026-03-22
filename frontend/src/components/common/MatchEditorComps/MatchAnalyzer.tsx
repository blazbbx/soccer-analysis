import React, { useState } from "react";
import { Box, Button, CircularProgress, Typography } from "@mui/material";
import { UploadDialog } from "./UploadDialog";
import { VideoPlayer } from "./VideoPlayer";
import { useGetMatch, useInitiateUpload } from "../../../api/generated/match-controller/match-controller";
import { MatchResponse } from "../../../api/generated/model/matchResponse";
import axios from "axios";

export const MatchAnalyzer = () => {
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [activeMatchId, setActiveMatchId] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);

  const { mutate: initiateUpload, isPending: isInitiating } =
    useInitiateUpload();

  const { data: axiosResponse } = useGetMatch(activeMatchId ?? "", {
    query: {
      enabled: !!activeMatchId, // Csak akkor indul el, ha van ID
      refetchInterval: (query) => {        
        // A generált típus miatt a query.state.data formátuma: { data: Blob, status: 200 }
        const match = query.state.data as unknown as MatchResponse;

        if (!match) return 5000;

        // Leállítjuk a pollingot, ha kész vagy hibára futott
        return match.encodingStatus === "COMPLETED" ||
          match.encodingStatus === "FAILED"
          ? false
          : 5000;
      },
    },    
  });

  const matchData = axiosResponse as unknown as MatchResponse;


  const handleVideoUpload = (file: File) => {
    setUploadProgress(0);
    
    initiateUpload(
      {
        data: {
          originalFilename: file.name,
          // TODO: egyéb mezők...
        },
      },
      {        
        onSuccess: (response) => {
          // A válasz struktúrája { data: Blob, status: 200 }, így a .data-ból szedjük ki a mezőket
          const { uploadUrl, matchId } = response as unknown as any; 
          
          setActiveMatchId(matchId);
          setIsUploadOpen(false);

          const performUpload = async () => {
            try {
              await axios.put(uploadUrl, file, {
                headers: { "Content-Type": file.type },
                onUploadProgress: (progressEvent) => {
                  const percentCompleted = Math.round(
                    (progressEvent.loaded * 100) / progressEvent.total!
                  );
                  setUploadProgress(percentCompleted);
                },
              });
              console.log("MinIO feltöltés kész! A backend webhookkal indítja a feldolgozást.");
            } catch (err) {
              console.error("Hiba a MinIO feltöltésnél", err);
              setUploadProgress(null);
            }
          };

          performUpload();
        },
      }
    );
  };

  const isUploadingToMinio = uploadProgress !== null && uploadProgress < 100;
  const isProcessingOnBackend = uploadProgress === 100 && matchData?.encodingStatus !== "COMPLETED";
  const isVideoReady = matchData?.encodingStatus === "COMPLETED" && matchData?.hlsManifestUrl;

  return (
    <Box sx={{ p: 4 }}>
      <Typography variant="h4" gutterBottom>
        Mérkőzés Elemző
      </Typography>

      <Button
        variant="contained"
        onClick={() => setIsUploadOpen(true)}
        sx={{ mb: 4 }}
        disabled={isUploadingToMinio || isProcessingOnBackend}
      >
        Mérkőzés Feltöltése
      </Button>

      <UploadDialog
        open={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onUpload={handleVideoUpload}
        isUploading={isInitiating}
      />

      {/* Állapotjelzések */}
      <Box sx={{ mb: 4 }}>
        {isUploadingToMinio && (
          <Typography variant="body1" color="primary">
            Videó feltöltése a tárhelyre: {uploadProgress}%
          </Typography>
        )}

        {isProcessingOnBackend && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <CircularProgress size={24} />
            <Typography variant="body1" color="text.secondary">
              A videó megérkezett! Kódolás folyamatban a szerveren (ez percekig is eltarthat)...
            </Typography>
          </Box>
        )}

        {matchData?.encodingStatus === "FAILED" && (
          <Typography variant="body1" color="error">
            Hiba történt a videó feldolgozása során!
          </Typography>
        )}
      </Box>

      {/* Lejátszó */}
      {isVideoReady && (
        <Box sx={{ mt: 4 }}>
          <Typography variant="h6" gutterBottom>
            Feltöltött videó előnézete:
          </Typography>
          <VideoPlayer videoUrl={matchData.hlsManifestUrl!} />
        </Box>
      )}
    </Box>
  );
};
