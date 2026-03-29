import { useState } from "react";
import {
  Box,
  Typography,
  Button,
  Stack
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useNavigate } from "react-router-dom";
import axios from "axios";

// Komponensek
import {
  UploadDialog,
  type MatchUploadData,
} from "../components/common/MatchesPageComps/UploadDialog";
import { MatchCard } from "../components/common/MatchesPageComps/MatchCard";

import {
  useInitiateUpload,
  useGetAllMatches,
} from "../api/generated/match-controller/match-controller";
import { useGetAllTeams } from "../api/generated/teams/teams";
import { type MatchResponse } from "../api/generated/model/matchResponse";
import { type TeamResponse } from "../api/generated/model";
import { FilledActionButton } from "../components/common/ui/FilledActionButton";

export const Matches = () => {
  const navigate = useNavigate();
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);

  // API hívások a listák lekéréséhez
  const { data: teamsData } = useGetAllTeams();
  const { data: matchesData, refetch: refetchMatches } = useGetAllMatches();
  const { mutate: initiateUpload, isPending: isInitiating } =
    useInitiateUpload();

  const teams = (teamsData as unknown as TeamResponse[]) || [];
  const matches = (matchesData as unknown as MatchResponse[]) || [];

  const isUploadingToMinio = uploadProgress !== null && uploadProgress < 100;

  // Feltöltés indítása a Dialog-ból
  const handleVideoUpload = (data: MatchUploadData) => {
    setUploadProgress(0);

    initiateUpload(
      {
        data: {
          originalFilename: data.file.name,
          homeTeamId: data.homeTeamId,
          matchDate: data.matchDate ? `${data.matchDate}T00:00:00.000Z`: undefined
        },
      },
      {
        onSuccess: (response) => {
          const { uploadUrl } = response as unknown as any;

          setIsUploadOpen(false);

          const performUpload = async () => {
            try {
              await axios.put(uploadUrl, data.file, {
                headers: { "Content-Type": data.file.type },
                onUploadProgress: (progressEvent) => {
                  const percentCompleted = Math.round(
                    (progressEvent.loaded * 100) / progressEvent.total!,
                  );
                  setUploadProgress(percentCompleted);
                },
              });
              console.log(
                "MinIO feltöltés kész! A backend webhookkal indítja a feldolgozást.",
              );
              setUploadProgress(100);

              setTimeout(() => {
                refetchMatches();
                setUploadProgress(null);
              }, 2000);
            } catch (err) {
              console.error("Hiba a MinIO feltöltésnél", err);
              setUploadProgress(null);
            }
          };

          performUpload();
        },
        onError: (err) => {
          console.error("Hiba a feltöltés inicializálásakor:", err);
          setUploadProgress(null);
        },
      },
    );
  };

  return (
    <Box sx={{ p: 4 }}>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 4,
        }}
      >
        <Typography variant="h4" fontWeight="bold">
          Mérkőzések
        </Typography>
        <FilledActionButton
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setIsUploadOpen(true)}
          disabled={isUploadingToMinio || isInitiating}
        >
          Mérkőzés feltöltése
        </FilledActionButton>
      </Box>

      {/* Progress kijelzése, ha épp töltünk fel valamit */}
      {isUploadingToMinio && (
        <Box sx={{ mb: 4, p: 2, bgcolor: "primary.light", borderRadius: 1 }}>
          <Typography
            variant="body1"
            color="primary.contrastText"
            fontWeight="bold"
          >
            Videó feltöltése folyamatban... {uploadProgress}%
          </Typography>
        </Box>
      )}

      <Stack spacing={2} sx={{ mt: 2 }}>
        {matches.map((match: MatchResponse) => (
          <MatchCard
            key={match.id}
            match={match}
            onOpen={(matchId) => navigate(`/matches/${matchId}`)}
          />
        ))}
      </Stack>

      <UploadDialog
        open={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onUpload={handleVideoUpload}
        isUploading={isInitiating}
        teams={teams}
      />
    </Box>
  );
};
