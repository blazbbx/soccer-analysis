import { useParams, useNavigate } from "react-router-dom";
import { Box, Button, CircularProgress, Typography } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { VideoPlayer } from "./VideoPlayer";
import { useGetMatch, getGetMatchQueryKey } from "../../../api/generated/match-controller/match-controller";
import { type MatchResponse } from "../../../api/generated/model/matchResponse";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { api } from "../../../api/axiosInstance";
import { getSubscribeUrl } from "../../../api/generated/notification-controller/notification-controller";

export const MatchAnalyzer = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: axiosResponse, isLoading } = useGetMatch(id ?? "", {
    query: {
      enabled: !!id,
    },
  });

  const matchData = axiosResponse as unknown as MatchResponse;

  //TODO: Kivenni innen, a matches oldalon kezelni a feldolgozást
  /*
  useEffect(() => {
    if (!id) return;

    const relativeUrl = getSubscribeUrl(id);
    const baseUrl = api.defaults.baseURL || "http://localhost:8080";
    const fullUrl = `${baseUrl}${relativeUrl}`;

    const eventSource = new EventSource(fullUrl);
    eventSource.onmessage = (event) => {
      console.log("SSE esemény érkezett:", event.data);

      try {
        const data = JSON.parse(event.data);

        if (data.status === "COMPLETED" || data.status === "FAILED") {
          queryClient.invalidateQueries({ queryKey: getGetMatchQueryKey(id) });

          eventSource.close();
        }
      } catch (err) {
        console.error("Hiba az SSE üzenet feldolgozásakor", err);
      }
    };

    eventSource.onerror = (error) => {
      console.error("SSE kapcsolat megszakadt", error);
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [id, queryClient]);
  */

  const isProcessingOnBackend =
    matchData?.encodingStatus &&
    matchData.encodingStatus !== "COMPLETED" &&
    matchData.encodingStatus !== "FAILED";
  const isFailed = matchData?.encodingStatus === "FAILED";
  const isVideoReady =
    matchData?.encodingStatus === "COMPLETED" && matchData?.hlsManifestUrl;

  if (isLoading && !matchData) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box
      sx={{ p: 4, height: "100%", display: "flex", flexDirection: "column" }}
    >
      <Box sx={{ display: "flex", alignItems: "center", mb: 4, gap: 2 }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate("/matches")}
          color="inherit"
        >
          Vissza a meccsekhez
        </Button>
        <Typography variant="h4">Mérkőzés Elemző</Typography>
      </Box>

      {/* Állapotjelzések */}
      <Box sx={{ mb: 4 }}>
        {isProcessingOnBackend && (
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 2,
              p: 2,
              bgcolor: "info.light",
              borderRadius: 1,
            }}
          >
            <CircularProgress size={24} />
            <Typography variant="body1">
              A videó feldolgozása folyamatban van a szerveren (ez percekig is
              eltarthat)...
            </Typography>
          </Box>
        )}

        {isFailed && (
          <Box sx={{ p: 2, bgcolor: "error.light", borderRadius: 1 }}>
            <Typography variant="body1" color="error.dark" fontWeight="bold">
              Hiba történt a videó feldolgozása során!
            </Typography>
          </Box>
        )}
      </Box>

      {/* Lejátszó */}
      {isVideoReady && (
        <Box
          sx={{
            flexGrow: 1,
            bgcolor: "background.paper",
            borderRadius: 1,
            overflow: "hidden",
          }}
        >
          <VideoPlayer videoUrl={matchData.hlsManifestUrl!} />
        </Box>
      )}
    </Box>
  );
};
