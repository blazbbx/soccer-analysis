import { Box, useTheme } from "@mui/material";
import { useState, useCallback } from "react";
import { VideoPlayer } from "./Videoplayer/VideoPlayer";
import { PitchView2D } from "./Videoplayer/PitchView2D";
import { PlaybackControls } from "./Videoplayer/PlaybackControls";
import { TimelineBar } from "./Videoplayer/TimelineBar";
import { LabelsTrack } from "./Videoplayer/LabelsTrack";
import { EventsSideBar } from "./LeftPanel/EventsSideBar";
import type { MatchResponse } from "../../../api/generated/model";
import { EditorTopBar } from "./TopBar/EditorTopBar";
import { useVideoPlayer } from "../../../context/VideoPlayerContext";
import { useRecording } from "../../../context/RecordingContext";
import { DrawingToolsPanel } from "./RightPanel/DrawingToolsPanel";
import { ClipsSidebar } from "./RightPanel/ClipsSidebar";
import { useTrackingData } from "../hooks/VideoEdit/useTrackingData";
import { useAuth } from "../../../context/AuthContext";
import { ROLES } from "../../../types/roles";
import { SaveClipDialog } from "./SaveClipDialog";
import { useClipUpload } from "../../../services/recordingService";

export const MatchAnalyzerEditor = ({
  matchData,
}: {
  matchData: MatchResponse;
}) => {
  const { setIsPlaying } = useVideoPlayer();
  const { isRecording, setFollowPlayerMode, pendingRecording, setPendingRecording } = useRecording();
  const theme = useTheme();
  const { user } = useAuth();
  const isEditor = user?.role === ROLES.COACH || user?.role === ROLES.ADMIN;
  const [show2DView, setShow2DView] = useState(false);

  const { uploadClip, isUploading } = useClipUpload();

  const handleSaveClip = async (name: string) => {
    if (!pendingRecording || !matchData.id) return;
    await uploadClip({
      overlayBlob: pendingRecording.overlayBlob,
      audioBlob: pendingRecording.audioBlob,
      syncData: pendingRecording.syncData,
      name,
      matchId: matchData.id,
    });
    setPendingRecording(null);
  };

  const { frameMap, videoFps } = useTrackingData(matchData.trackingDataUrl);

  const handleToggle2DView = useCallback(() => {
    setIsPlaying(false);
    if (!show2DView) setFollowPlayerMode(false);
    setShow2DView((v) => !v);
  }, [show2DView, setIsPlaying, setFollowPlayerMode]);

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
        isEditor={isEditor}
        show2DView={show2DView}
        onToggle2DView={handleToggle2DView}
      />
      <Box sx={{ display: "flex", flex: 1, overflow: "hidden" }}>
        <EventsSideBar isEditor={isEditor} />
        <Box
          component="main"
          sx={{
            flexGrow: 1,
            p: 1,
            overflowY: "auto",
            backgroundColor: theme.palette.background.paper,
          }}
        >
          <Box sx={{ maxWidth: "1200px", margin: "0 auto" }}>
            {matchData.hlsManifestUrl && (
              <Box sx={{ borderRadius: "4px 4px 0 0", overflow: "hidden" }}>
                <Box sx={{ display: show2DView ? "none" : "block" }}>
                  <VideoPlayer
                    videoUrl={matchData.hlsManifestUrl}
                    frameMap={frameMap}
                    videoFps={videoFps}
                    isHidden={show2DView}
                    show2DView={show2DView}
                  />
                </Box>
                {show2DView && <PitchView2D frameMap={frameMap} videoFps={videoFps} />}
              </Box>
            )}

            <Box sx={{ borderTop: "none" }}><PlaybackControls /></Box>
            <Box sx={{ borderTop: "none" }}><TimelineBar /></Box>
            <LabelsTrack isEditor={isEditor} />
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
          {(isEditor && isRecording)
            ? <DrawingToolsPanel />
            : <ClipsSidebar matchId={matchData.id!} isEditor={isEditor} />
          }
        </Box>
      </Box>

      <SaveClipDialog
        open={pendingRecording !== null}
        loading={isUploading}
        onSave={handleSaveClip}
        onClose={() => setPendingRecording(null)}
      />
    </Box>
  );
};
