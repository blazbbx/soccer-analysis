import { useParams } from "react-router-dom";
import { Box, CircularProgress, Typography } from "@mui/material";
import { MatchAnalyzerEditor } from "./MatchAnalyzerEditor/MatchAnalyzerEditor";
import { useGetMatch } from "../../api/generated/match-controller/match-controller";
import { type MatchResponse } from "../../api/generated/model/matchResponse";
import { VideoPlayerProvider } from "../../context/VideoPlayerContext";
import { RecordingProvider } from "../../context/RecordingContext";

export const MatchAnalyzer = () => {
  const { id } = useParams<{ id: string }>();

  const { data: axiosResponse, isLoading } = useGetMatch(id ?? "", {
    query: {
      enabled: !!id,
    },
  });

  const matchData = axiosResponse as unknown as MatchResponse;

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
      sx={{ p: 0, height: "100vh", display: "flex", flexDirection: "column" }}
    >
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

      {/* Match Analyzer Editor */}
      {isVideoReady && (
        <Box
          sx={{
            flexGrow: 1,
            display: "flex",
            flexDirection: "column",
            minHeight: 0,
          }}
        >
          <VideoPlayerProvider>            
            <RecordingProvider>
              <MatchAnalyzerEditor matchData={matchData} />
            </RecordingProvider>            
          </VideoPlayerProvider>
        </Box>
      )}
    </Box>
  );
};
