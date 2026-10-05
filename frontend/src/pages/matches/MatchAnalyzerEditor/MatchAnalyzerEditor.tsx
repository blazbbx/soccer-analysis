import { Box, useTheme } from "@mui/material";
import { useMemo, useRef } from "react";
import { VideoView } from "./Videoplayer/VideoView";
import { PitchView } from "./Videoplayer/PitchView";
import { ClipRecorder } from "./Videoplayer/ClipRecorder";
import type { RecordingSurfaceHandle } from "./Videoplayer/recordingSurface";
import { PlaybackControls } from "./Videoplayer/PlaybackControls";
import { TimelineBar } from "./Videoplayer/TimelineBar";
import { LabelsTrack } from "./Videoplayer/LabelsTrack";
import { EventsSideBar } from "./LeftPanel/EventsSideBar";
import type { MatchResponse } from "../../../api/generated/model";
import { EditorTopBar } from "./TopBar/EditorTopBar";
import { useRecording } from "../../../context/RecordingContext";
import { useViewMode } from "../../../context/ViewModeContext";
import { DrawingToolsPanel } from "./RightPanel/DrawingToolsPanel";
import { ClipsSidebar } from "./RightPanel/ClipsSidebar";
import { useTrackingData } from "../hooks/VideoEdit/useTrackingData";
import { useLabelsAutoSave } from "../hooks/VideoEdit/useLabelsAutoSave";
import { useAuth } from "../../../context/AuthContext";
import { ROLES } from "../../../types/roles";
import { SaveClipDialog } from "./SaveClipDialog";
import { useClipUpload } from "../../../services/recordingService";

export const MatchAnalyzerEditor = ({
  matchData,
}: {
  matchData: MatchResponse;
}) => {
  const { pendingRecording, setPendingRecording } = useRecording();
  const { viewMode, toggleViewMode } = useViewMode();
  const theme = useTheme();
  const { user } = useAuth();
  const isEditor = user?.role === ROLES.COACH || user?.role === ROLES.ADMIN;
  const videoSurface = useRef<RecordingSurfaceHandle>(null);
  const pitchSurface = useRef<RecordingSurfaceHandle>(null);

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

  const { videoFps, isLoaded } = useTrackingData(matchData.trackingDataUrl);

  const { homeTeamColor, awayTeamColor, refereeColor } = matchData;
  const teamColors = useMemo(
    () => ({ home: homeTeamColor, away: awayTeamColor, referee: refereeColor }),
    [homeTeamColor, awayTeamColor, refereeColor],
  );
  useLabelsAutoSave(matchData.id!, videoFps, isEditor ? isLoaded : false);

  const showPitch = viewMode === "pitch";

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
        show2DView={showPitch}
        onToggle2DView={toggleViewMode}
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
                <VideoView
                  ref={videoSurface}
                  videoUrl={matchData.hlsManifestUrl}
                  isHidden={showPitch}
                />
                {showPitch && <PitchView ref={pitchSurface} teamColors={teamColors} />}
              </Box>
            )}

            <Box sx={{ borderTop: "none" }}><PlaybackControls /></Box>
            <Box sx={{ borderTop: "none" }}><TimelineBar /></Box>
            <LabelsTrack isEditor={isEditor} />
          </Box>
        </Box>

        {user?.role !== ROLES.FAN && (
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
            {isEditor && <DrawingToolsPanel />}
            <Box sx={{ flex: 1, minHeight: 0 }}>
              <ClipsSidebar matchId={matchData.id!} isEditor={isEditor} />
            </Box>
          </Box>
        )}
      </Box>

      <ClipRecorder videoSurface={videoSurface} pitchSurface={pitchSurface} videoFps={videoFps} />

      <SaveClipDialog
        open={pendingRecording !== null}
        loading={isUploading}
        onSave={handleSaveClip}
        onClose={() => setPendingRecording(null)}
      />
    </Box>
  );
};
