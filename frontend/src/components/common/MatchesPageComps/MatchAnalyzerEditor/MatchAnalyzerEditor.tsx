import { Box, useTheme } from "@mui/material";
import { VideoPlayer } from "./Videoplayer/VideoPlayer";
import { PlaybackControls } from "./Videoplayer/PlaybackControls";
import { TimelineBar } from "./Videoplayer/TimelineBar";
import { LabelsTrack } from "./Videoplayer/LabelsTrack";
import { EventsSideBar } from "./LeftPanel/EventsSideBar";
import type { MatchResponse } from "../../../../api/generated/model";
import { EditorTopBar } from "./TopBar/EditorTopBar";
import { useVideoPlayer } from "../../../../context/VideoPlayerContext";
import { ClipsTrack } from "./Videoplayer/ClipsTrack";
import { ClipsSidebar } from "./RightPanel/ClipsSidebar";
import { DrawingToolsPanel } from "./RightPanel/DrawingToolsPanel";
import { useTrackingData } from "../../../../hooks/VideoEdit/useTrackingData";

export const MatchAnalyzerEditor = ({
  matchData,
}: {
  matchData: MatchResponse;
}) => {
  const { currentTime, duration, addClip } = useVideoPlayer();
  const theme = useTheme();

  useTrackingData(matchData.trackingDataUrl);
  
  const handleSnippetClick = () => {
    const start = Math.max(0, currentTime - 5);
    const end = duration > 0 ? Math.min(duration, currentTime + 5) : currentTime + 5;

    addClip({
      id: Math.random().toString(36).substr(2, 9),
      name: "Új klip", 
      startTime: start,
      endTime: end,
      color: '#8b5cf6',
      isEditing: true
    });
  };

  return (   
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          width: "100%",
          height: "100%",
          backgroundColor: theme.palette.background.paper,
          overflow: "hidden",
        }}
      >
        <EditorTopBar
          homeTeam={matchData.homeTeamName}
          awayTeam={matchData.awayTeamName}
          date={matchData.matchDate}
          backPath="/matches"
          onSnippetClick={handleSnippetClick}
        />
        <Box sx={{ display: "flex", flex: 1, overflow: "hidden" }}>
          <EventsSideBar />
          <Box
            component="main"
            sx={{
              flexGrow: 1,              
              p: 3,
              overflowY: "auto",
              backgroundColor: theme.palette.background.paper,
            }}
          >
            <Box
              sx={{                
                maxWidth: "1200px",
                margin: "0 auto",              
              }}
            >
              {/* Felső rész: VideoPlayer (Fehér négyszög a képen) */}
              {matchData.hlsManifestUrl && (
                <Box sx={{ borderRadius: "4px 4px 0 0", overflow: "hidden" }}>
                  <VideoPlayer videoUrl={matchData.hlsManifestUrl} />
                </Box>
              )}

              <Box sx={{ borderTop: "none" }}><PlaybackControls /></Box>
              <Box sx={{ borderTop: "none" }}><TimelineBar /></Box>
              <Box sx={{ borderTop: "none" }}><LabelsTrack /></Box>
              <Box sx={{ borderTop: "none", borderRadius: "0 0 4px 4px" }}><ClipsTrack /></Box>
            </Box>
          </Box>

          <Box
          sx={{
            width: "280px",
            backgroundColor: theme.palette.background.paper,
            borderLeft: `1px solid ${theme.palette.divider}`,
            display: "flex",
            flexDirection: "column",
            height: "100%",
          }}
        >
          <DrawingToolsPanel />
          <ClipsSidebar />
        </Box>
        </Box>
      </Box>
  );
};
