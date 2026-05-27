import { useState } from "react";
import {
  Box,
  Typography,
  Stack,
  CircularProgress,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { ROLES } from "../../types/roles";


import {
  UploadDialog,
  type MatchUploadData,
} from "./Upload/UploadDialog";
import { MatchCard } from "./MatchCard";

import {
  useGetAllMatches,
} from "../../api/generated/match-controller/match-controller";
import {  useGetMyTeams } from "../../api/generated/teams/teams";
import { type MatchResponse } from "../../api/generated/model/matchResponse";
import { type TeamResponse } from "../../api/generated/model";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import { useMatchUploadFlow, type Corner } from "./hooks/useMatchUploadFlow";

export const Matches = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canUpload = user?.role === ROLES.ADMIN || user?.role === ROLES.COACH;
  const [isUploadOpen, setIsUploadOpen] = useState(false);

  const {
    uploadProgress,
    uploadPhase,
    fieldDetection,
    error,
    startUpload,
    confirmCorners,
    resumeFromAwaitingCorners,
    reset,
  } = useMatchUploadFlow();


  const { data: teamsData } = useGetMyTeams();
  const { data: matchesData, isLoading: isLoadingMatches } = useGetAllMatches();

  const teams = (teamsData as unknown as TeamResponse[]) || [];
  const matches = (matchesData as unknown as MatchResponse[]) || [];

  const isUploadButtonDisabled =
    uploadPhase === 'uploading' ||
    uploadPhase === 'initiating' ||
    uploadPhase === 'preprocessing' ||
    uploadPhase === 'awaiting-corners' ||
    uploadPhase === 'confirming';
  const isUploadingToMinio = uploadProgress !== null && uploadProgress < 100;

  const handleVideoUpload = async (data: MatchUploadData) => {
    try {
      await startUpload(data);
    } catch (err) {
      console.error('Upload failed:', err);
    }
  };

  // Errors are re-thrown so UploadDialog can keep itself open on failure; the hook
  // already logs the error and surfaces it via `error` state.
  const handleConfirmCorners = (corners: Corner[]) => confirmCorners(corners);

  const handleDialogClose = () => {
    setIsUploadOpen(false);
    // Forget any leftover field-detection state once the user explicitly closes the dialog.
    // The SSE stream is also closed inside reset() so we don't leak it.
    reset();
  };

  // "Field selection" chip on MatchCard — resume a previously-uploaded match that
  // stalled in AWAITING_CORNERS (closed dialog, lost connection, closed browser).
  const handleResumeCornerSelection = (match: MatchResponse) => {
    resumeFromAwaitingCorners({
      id: match.id,
      defishedImageUrl: match.defishedImageUrl,
      fieldCorners: match.fieldCorners,
    });
    setIsUploadOpen(true);
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
        {canUpload && (
          <FilledActionButton
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setIsUploadOpen(true)}
            disabled={isUploadButtonDisabled}
          >
            Mérkőzés feltöltése
          </FilledActionButton>
        )}
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

      {isLoadingMatches ? (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 6 }}>
          <CircularProgress />
        </Box>
      ) : matches.length === 0 ? (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 6 }}>
          <Typography variant="body1" color="text.secondary">
            Nincsenek elérhető mérkőzések.
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2} sx={{ mt: 2 }}>
          {matches.map((match: MatchResponse) => (
            <MatchCard
              key={match.id}
              match={match}
              onOpen={(matchId) => navigate(`/matches/${matchId}`)}
              onResumeCornerSelection={handleResumeCornerSelection}
            />
          ))}
        </Stack>
      )}

      <UploadDialog
        open={isUploadOpen}
        onClose={handleDialogClose}
        onUpload={handleVideoUpload}
        uploadPhase={uploadPhase}
        uploadProgress={uploadProgress}
        fieldDetection={fieldDetection}
        onConfirmCorners={handleConfirmCorners}
        error={error}
        teams={teams}
      />
    </Box>
  );
};
