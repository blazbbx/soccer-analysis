import React, { useState, useCallback } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Stack,
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
import { type TeamResponse } from "../../../api/generated/model/teamResponse";
import type {
  Corner,
  FieldDetectionPayload,
  UploadPhase,
} from "../hooks/useMatchUploadFlow";

export interface MatchUploadData {
  file: File;
  homeTeamId: string;
  awayTeamName: string;
  matchDate: string;
  homeTeamColor: string;
  awayTeamColor: string;
  refereeColor: string;
}

interface UploadDialogProps {
  open: boolean;
  onClose: () => void;
  onUpload: (data: MatchUploadData) => void;
  uploadPhase: UploadPhase;
  uploadProgress: number | null;
  fieldDetection: FieldDetectionPayload | null;
  onConfirmCorners: (corners: Corner[]) => Promise<void>;
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

  const [homeTeamColor, setHomeTeamColor] = useState<string>("#ffffff");
  const [awayTeamColor, setAwayTeamColor] = useState<string>("#ffffff");
  const [refereeColor, setRefereeColor] = useState<string>("#ffffff");

  const [editedCorners, setEditedCorners] = useState<Corner[]>([]);

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
      setFormError(null);
      if (acceptedFiles.length > 0) setSelectedFile(acceptedFiles[0]);
      if (fileRejections.length > 0)
        setFormError("Csak MP4 formátumú videót tölthetsz fel!");
    },
    [isBusy],
  );

  const setDefault = () => {
    setSelectedFile(null);
    setFormError(null);
    setHomeTeamId("");
    setAwayTeamName("");
    setMatchDate("");
    setHomeTeamColor("#ffffff");
    setAwayTeamColor("#ffffff");
    setRefereeColor("#ffffff");
    setEditedCorners([]);
  };

  const handleUpload = () => {
    if (!selectedFile || !homeTeamId || !awayTeamName || !matchDate) return;
    onUpload({
      file: selectedFile,
      homeTeamId,
      awayTeamName,
      matchDate,
      homeTeamColor,
      awayTeamColor,
      refereeColor,
    });
  };

  const handleClose = () => {
    if (isBusy) return;
    setDefault();
    onClose();
  };

  const handleSendCorners = async () => {
    if (editedCorners.length !== 4) return;
    try {
      await onConfirmCorners(editedCorners);
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
          label="Hazai csapat"
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
          label="Vendég csapat (Név)"
          value={awayTeamName}
          onChange={(e) => setAwayTeamName(e.target.value)}
          fullWidth
        />

        <TextField
          label="Mérkőzés dátuma"
          type="date"
          value={matchDate}
          onChange={(e) => setMatchDate(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
          fullWidth
        />

        <Typography variant="body2" fontWeight="bold" sx={{ mt: 1, mb: -1 }}>
          Mezszínek
        </Typography>

        {(
          [
            { label: "Hazai csapat mez", color: homeTeamColor, setColor: setHomeTeamColor },
            { label: "Vendég csapat mez", color: awayTeamColor, setColor: setAwayTeamColor },
            { label: "Bíró mez", color: refereeColor, setColor: setRefereeColor },
          ] as const
        ).map(({ label, color, setColor }) => (
          <Stack key={label} direction="row" spacing={2} alignItems="center">
            <Typography sx={{ flex: 1 }}>{label}</Typography>
            <Box
              component="label"
              sx={{
                width: 44,
                height: 44,
                borderRadius: 1,
                border: "2px solid",
                borderColor: "divider",
                bgcolor: color,
                cursor: "pointer",
                display: "block",
                position: "relative",
                "&:hover": { borderColor: "text.secondary" },
              }}
            >
              <Box
                component="input"
                type="color"
                value={color}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setColor(e.target.value)}
                sx={{ opacity: 0, width: 0, height: 0, position: "absolute" }}
              />
            </Box>
          </Stack>
        ))}

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
              <strong>Kiválasztott videó:</strong> {selectedFile.name}
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
        <Button onClick={handleClose} color="inherit">Mégse</Button>
        <Button
          onClick={handleUpload}
          variant="contained"
          color="primary"
          disabled={!isFormValid}
        >
          Feltöltés indítása
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
          />
        )}
        {error && (
          <Typography color="error" variant="body2" sx={{ mt: 2, textAlign: "center" }}>
            {error}
          </Typography>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={handleClose} color="inherit">Mégse</Button>
        <Button
          onClick={handleSendCorners}
          variant="contained"
          color="primary"
          disabled={editedCorners.length !== 4}
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
      <Button onClick={handleClose} variant="outlined">Mégse</Button>
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
    : "Mérkőzés feltöltése";

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
