import React, { useState, useCallback, useEffect } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  IconButton,
  CircularProgress,
  LinearProgress,
  TextField,
  MenuItem,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { useTranslation } from "react-i18next";
import { type FileRejection } from "react-dropzone";
import { UploadField } from "./UploadField";
import { CornerPreview } from "./CornerPreview";
import { GateList } from "./GateList";
import { JerseyColorEditor } from "./JerseyColorEditor";
import { allGatesComplete, gatesFromPayload, toConfirmedGates, type ConfirmedGatePayload, type GateDraft } from "./gateDraft";
import { type TeamResponse } from "../../../api/generated/model/teamResponse";
import {
  DEFAULT_JERSEY_COLORS,
  type Corner,
  type FieldDetectionPayload,
  type GoalDetectionState,
  type JerseyColors,
  type UploadPhase,
} from "../hooks/useMatchUploadFlow";

export interface MatchUploadData {
  file: File;
  homeTeamId: string;
  awayTeamName: string;
  matchDate: string;
}

// Colors the field detection did not suggest fall back to the shared default.
const colorsFromSuggestion = (
  suggested: FieldDetectionPayload["suggestedColors"],
): JerseyColors => ({
  home: suggested?.home ?? DEFAULT_JERSEY_COLORS.home,
  away: suggested?.away ?? DEFAULT_JERSEY_COLORS.away,
  referee: suggested?.referee ?? DEFAULT_JERSEY_COLORS.referee,
});

interface UploadDialogProps {
  open: boolean;
  onClose: () => void;
  onUpload: (data: MatchUploadData) => void;
  uploadPhase: UploadPhase;
  uploadProgress: number | null;
  fieldDetection: FieldDetectionPayload | null;
  goalDetection: GoalDetectionState;
  onConfirmCorners: (corners: Corner[], colors: JerseyColors, gates: ConfirmedGatePayload[]) => Promise<void>;
  error: string | null;
  teams: TeamResponse[];
}

export const UploadDialog = ({
  open,
  onClose,
  onUpload,
  uploadPhase,
  uploadProgress,
  fieldDetection,
  goalDetection,
  onConfirmCorners,
  error,
  teams,
}: UploadDialogProps) => {
  const { t } = useTranslation();

  const [selectedFile, setSelectedFile] = useState<File | null>(null);

  const [formError, setFormError] = useState<string | null>(null);

  const [homeTeamId, setHomeTeamId] = useState("");
  const [awayTeamName, setAwayTeamName] = useState("");
  const [matchDate, setMatchDate] = useState("");

  const [editedCorners, setEditedCorners] = useState<Corner[]>([]);

  // The goals the user sees and edits. They start from the goal worker's result; a result that arrives
  // after the user already has goals of their own (drawn by hand) does not overwrite them.
  const [gates, setGates] = useState<GateDraft[]>([]);
  const [placingGateId, setPlacingGateId] = useState<number | null>(null);
  // The user chose to go on without waiting for a goal detection that is still running.
  const [skippedGoalWait, setSkippedGoalWait] = useState(false);
  useEffect(() => {
    if (goalDetection.status === "PENDING") return;
    setGates((current) => (current.length > 0 ? current : gatesFromPayload(goalDetection.goals)));
  }, [goalDetection]);

  // The jersey colors are confirmed together with the corners: pre-filled from the
  // field-detection suggestion, editable by the user.
  const [colors, setColors] = useState<JerseyColors>(DEFAULT_JERSEY_COLORS);
  const hasSuggestion =
    !!fieldDetection?.suggestedColors &&
    Object.values(fieldDetection.suggestedColors).some(Boolean);
  useEffect(() => {
    setColors(colorsFromSuggestion(fieldDetection?.suggestedColors));
  }, [fieldDetection]);

  // PREPROCESSING is deliberately NOT in isBusy: if the SSE stream dies (lost network,
  // closed lid, etc.) the user can still escape via the close button. The backend keeps
  // processing in the background and the match will show up in the list with the
  // "Field selection" chip once it transitions to AWAITING_CORNERS.
  const isBusy =
    uploadPhase === "initiating" ||
    uploadPhase === "uploading" ||
    uploadPhase === "confirming";

  const onDrop = useCallback(
    (acceptedFiles: File[], fileRejections: FileRejection[]) => {
      if (isBusy) return;    
      if (acceptedFiles.length > 0) setSelectedFile(acceptedFiles[0]);
      if (fileRejections.length > 0)
        setFormError(t("upload.only-mp4"));
    },
    [isBusy, t],
  );

  const setDefault = () => {
    setSelectedFile(null);
    setFormError(null);
    setHomeTeamId("");
    setAwayTeamName("");
    setMatchDate("");
    setColors(DEFAULT_JERSEY_COLORS);
    setEditedCorners([]);
    setGates([]);
    setPlacingGateId(null);
    setSkippedGoalWait(false);
  };

  const handleUpload = () => {
    if (!selectedFile || !homeTeamId || !awayTeamName || !matchDate) return;
    onUpload({
      file: selectedFile,
      homeTeamId,
      awayTeamName,
      matchDate,
    });
  };

  // Sending needs: the four field corners, the goal detection finished (or skipped) so its result is not
  // lost silently, and every goal complete (a goal with a missing corner must be finished or deleted).
  const goalsReady = goalDetection.status !== "PENDING" || skippedGoalWait;
  const canSend =
    editedCorners.length === 4 && goalsReady && placingGateId === null && allGatesComplete(gates);

  const handleClose = () => {
    if (isBusy) return;
    setDefault();
    onClose();
  };

  const handleSendCorners = async () => {
    if (!canSend) return;
    try {
      await onConfirmCorners(editedCorners, colors, toConfirmedGates(gates));
      setDefault();
      onClose();
    } catch {
      // The hook already surfaced the error and reverted phase to 'awaiting-corners'.
      // Keep the dialog open so the user can retry.
    }
  };

  const isFormValid = selectedFile && homeTeamId && awayTeamName && matchDate;

  // ---------- Render helpers per phase ----------

  const renderForm = () => (
    <>
      <DialogContent
        dividers
        sx={{ display: "flex", flexDirection: "column", gap: 2 }}
      >
        <TextField
          select
          label={t("upload.home-team")}
          value={homeTeamId}
          onChange={(e) => setHomeTeamId(e.target.value)}
          fullWidth
        >
          {teams.map((team) => (
            <MenuItem key={team.id} value={team.id}>
              {team.name}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          label={t("upload.away-team")}
          value={awayTeamName}
          onChange={(e) => setAwayTeamName(e.target.value)}
          fullWidth
          disabled = {isBusy}
        />

        <TextField
          label={t("upload.match-date")}
          type="date"
          value={matchDate}
          onChange={(e) => setMatchDate(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
          fullWidth
          disabled={isBusy}
        />

        <UploadField
          onDrop={onDrop}
          accept={{ "video/mp4": [".mp4"] }}
          maxFiles={1}
        />

        {selectedFile && (
          <Box
            sx={{
              mt: 1,
              p: 2,
              bgcolor: "success.50",
              borderRadius: 1,
              border: "1px solid",
              borderColor: "success.200",
            }}
          >
            <Typography variant="body2" color="success.main">
              <strong>{t("upload.selected-video")}</strong> {selectedFile.name}
            </Typography>
          </Box>
        )}

        {formError && (
          <Typography color="error" variant="body2" sx={{ textAlign: "center" }}>
            {formError}
          </Typography>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={handleClose} color="inherit">{t("common.cancel")}</Button>
        <Button
          onClick={handleUpload}
          variant="contained"
          color="primary"
          disabled={!isFormValid}
        >
          {t("upload.start")}
        </Button>
      </DialogActions>
    </>
  );

  const renderUploading = () => (
    <Box sx={{ px: 3, py: 4 }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}>
        <CircularProgress size={24} />
        <Typography color="primary" fontWeight="bold">
          {uploadProgress !== null && uploadProgress < 100
            ? `${t("upload.uploading")} ${uploadProgress}%`
            : t("upload.uploading")}
        </Typography>
      </Box>
      {uploadProgress !== null && uploadProgress < 100 && (
        <LinearProgress variant="determinate" value={uploadProgress} />
      )}
    </Box>
  );

  const renderPreprocessing = () => (
    <Box sx={{ px: 3, py: 4, display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
      <CircularProgress />
      <Typography color="primary" fontWeight="bold">{t("upload.preprocessing")}</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ textAlign: "center" }}>
        {t("upload.preprocessingHint")}
      </Typography>
    </Box>
  );

  const renderAwaitingCorners = () => (
    <>
      <DialogContent dividers>
        <Typography variant="body2" sx={{ mb: 2 }}>
          {t("upload.fieldDetected.description")}
        </Typography>
        {fieldDetection && (
          <CornerPreview
            imageUrl={fieldDetection.defishedImageUrl}
            initialCorners={fieldDetection.corners}
            onChange={setEditedCorners}
            gates={gates}
            onGatesChange={setGates}
            placingGateId={placingGateId}
            onPlacingChange={setPlacingGateId}
          />
        )}
        <GateList
          gates={gates}
          onGatesChange={setGates}
          placingGateId={placingGateId}
          onPlacingChange={setPlacingGateId}
          status={goalDetection.status}
          skippedWaiting={skippedGoalWait}
          onSkipWaiting={() => setSkippedGoalWait(true)}
        />

        <JerseyColorEditor colors={colors} onChange={setColors} suggested={hasSuggestion} />
        {error && (
          <Typography color="error" variant="body2" sx={{ mt: 2, textAlign: "center" }}>
            {error}
          </Typography>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={handleClose} color="inherit">{t("common.cancel")}</Button>
        <Button
          onClick={handleSendCorners}
          variant="contained"
          color="primary"
          disabled={!canSend}
        >
          {t("upload.fieldDetected.send")}
        </Button>
      </DialogActions>
    </>
  );

  const renderError = () => (
    <Box sx={{ px: 3, py: 4, display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
      <Typography color="error" fontWeight="bold">
        {error ?? t("upload.error.generic")}
      </Typography>
      <Button onClick={handleClose} variant="outlined">{t("common.cancel")}</Button>
    </Box>
  );

  // ---------- Layout ----------

  let body: React.ReactNode;
  if (uploadPhase === "initiating" || uploadPhase === "uploading") {
    body = renderUploading();
  } else if (uploadPhase === "preprocessing" || uploadPhase === "confirming") {
    body = renderPreprocessing();
  } else if (uploadPhase === "awaiting-corners" && fieldDetection) {
    body = renderAwaitingCorners();
  } else if (uploadPhase === "error") {
    body = renderError();
  } else {
    body = renderForm();
  }

  const titleKey = uploadPhase === "awaiting-corners"
    ? t("upload.fieldDetected.title")
    : t("upload.title");

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        {titleKey}
        <IconButton onClick={handleClose} size="small" disabled={isBusy}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      {body}
    </Dialog>
  );
};
