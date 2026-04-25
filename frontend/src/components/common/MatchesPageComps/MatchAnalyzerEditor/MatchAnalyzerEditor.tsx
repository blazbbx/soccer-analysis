import { Box, useTheme } from "@mui/material";
import { useState } from "react";
import { VideoPlayer } from "./Videoplayer/VideoPlayer";
import { PitchView2D } from "./Videoplayer/PitchView2D";
import { PlaybackControls } from "./Videoplayer/PlaybackControls";
import { TimelineBar } from "./Videoplayer/TimelineBar";
import { LabelsTrack } from "./Videoplayer/LabelsTrack";
import { EventsSideBar } from "./LeftPanel/EventsSideBar";
import type { MatchResponse } from "../../../../api/generated/model";
import { EditorTopBar } from "./TopBar/EditorTopBar";
import { useVideoPlayer } from "../../../../context/VideoPlayerContext";
import { useClip } from "../../../../context/ClipContext";
import { ClipsTrack } from "./Videoplayer/ClipsTrack";
import { ClipsSidebar } from "./RightPanel/ClipsSidebar";
import { DrawingToolsPanel } from "./RightPanel/DrawingToolsPanel";
import { useTrackingData } from "../../../../hooks/VideoEdit/useTrackingData";
import { useAuth } from "../../../../context/AuthContext";
import { ROLES } from "../../../../types/roles";

export const MatchAnalyzerEditor = ({
  matchData,
}: {
  matchData: MatchResponse;
}) => {
  const { currentTime, duration, setIsPlaying } = useVideoPlayer();
  const { clips, addClip, triggerShakeUnsaved, drawingClipId, setFollowPlayerMode } = useClip();
  const theme = useTheme();
  const { user } = useAuth();
  const isEditor = user?.role === ROLES.COACH || user?.role === ROLES.ADMIN;
  const [show2DView, setShow2DView] = useState(false);

  const { frameMap, videoFps } = useTrackingData(matchData.trackingDataUrl);
  
  const handleSnippetClick = () => {
    if (clips.some((c) => c.isEditing)) {
      triggerShakeUnsaved();
      return;
    }

    const start = currentTime;
    const end = duration > 0 ? Math.min(duration, currentTime + 10) : currentTime + 10;

    addClip({
      id: Math.random().toString(36).slice(2, 11),
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
          isEditor={isEditor}
          show2DView={show2DView}
          onToggle2DView={() => {
            setIsPlaying(false);
            if (!show2DView) setFollowPlayerMode(false);
            setShow2DView((v) => !v);
          }}
        />
        <Box sx={{ display: "flex", flex: 1, overflow: "hidden" }}>
          <EventsSideBar isEditor={isEditor} />
          <Box
            component="main"
            sx={{
              flexGrow: 1,
              p: 2,
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
              {matchData.hlsManifestUrl && (
                <Box sx={{ borderRadius: "4px 4px 0 0", overflow: "hidden" }}>
                  {/* VideoPlayer stays mounted so HLS/timeupdate keeps driving currentTime */}
                  <Box sx={{ display: show2DView ? "none" : "block" }}>
                    <VideoPlayer videoUrl={matchData.hlsManifestUrl} isEditor={isEditor} frameMap={frameMap} videoFps={videoFps} isHidden={show2DView} />
                  </Box>
                  {show2DView && <PitchView2D frameMap={frameMap} videoFps={videoFps} isEditor={isEditor} />}
                </Box>
              )}

              <Box sx={{ borderTop: "none" }}><PlaybackControls /></Box>
              <Box sx={{ borderTop: "none" }}><TimelineBar /></Box>
              <LabelsTrack isEditor={isEditor} />
              {isEditor && <Box sx={{ borderTop: "none", borderRadius: "0 0 4px 4px" }}><ClipsTrack /></Box>}
            </Box>
          </Box>

          {isEditor && (
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
              {drawingClipId !== null && <DrawingToolsPanel />}
              <ClipsSidebar />
            </Box>
          )}
        </Box>
      </Box>
  );
};
