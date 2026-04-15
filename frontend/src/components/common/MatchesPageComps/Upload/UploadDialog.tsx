import { useState, useCallback } from "react";
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
import { type FileRejection } from "react-dropzone";
import { UploadField } from "./UploadField";
import { type TeamResponse } from "../../../../api/generated/model/teamResponse"; 

export interface MatchUploadData {
  file: File;
  homeTeamId: string;
  awayTeamName: string;
  matchDate: string;
}

interface UploadDialogProps {
  open: boolean;
  onClose: () => void;
  onUpload: (data: MatchUploadData) => void;
  isUploading: boolean;
  uploadProgress: number | null;
  teams: TeamResponse[];
}

export const UploadDialog = ({
  open,
  onClose,
  onUpload,
  isUploading,
  uploadProgress,
  teams,
}: UploadDialogProps) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);

  
  const [homeTeamId, setHomeTeamId] = useState("");
  const [awayTeamName, setAwayTeamName] = useState("");
  const [matchDate, setMatchDate] = useState("");

  const onDrop = useCallback(
    (acceptedFiles: File[], fileRejections: FileRejection[]) => {
      if (isUploading) return;
      setError(null);
      if (acceptedFiles.length > 0) setSelectedFile(acceptedFiles[0]);
      if (fileRejections.length > 0)
        setError("Csak MP4 formátumú videót tölthetsz fel!");
    },
    [isUploading],
  );

  const handleUpload = () => {
    if (!selectedFile || !homeTeamId || !awayTeamName || !matchDate) return;
    onUpload({
      file: selectedFile,
      homeTeamId,
      awayTeamName,
      matchDate,
    });
    setSelectedFile(null);
    setError(null);
    setHomeTeamId("");
    setAwayTeamName("");
    setMatchDate("");
    onClose();
  };

  const handleClose = () => {
    if (isUploading) return;
    setSelectedFile(null);
    setError(null);
    setHomeTeamId("");
    setAwayTeamName("");
    setMatchDate("");
    onClose();
  };

  const isFormValid = selectedFile && homeTeamId && awayTeamName && matchDate;

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        Mérkőzés feltöltése
        <IconButton onClick={handleClose} size="small" disabled={isUploading}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>

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
          disabled={isUploading}
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
          disabled={isUploading}
        />

        <TextField
          label="Mérkőzés dátuma"
          type="date"
          value={matchDate}
          onChange={(e) => setMatchDate(e.target.value)}
          slotProps={{
            inputLabel: { shrink: true },
          }}
          fullWidth
          disabled={isUploading}
        />

        <UploadField
          onDrop={onDrop}
          accept={{ "video/mp4": [".mp4"] }}
          maxFiles={1}
          disabled={isUploading}
        />

        {selectedFile && !isUploading && (
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

        {error && (
          <Typography
            color="error"
            variant="body2"
            sx={{ textAlign: "center" }}
          >
            {error}
          </Typography>
        )}
      </DialogContent>

      {isUploading ? (
        <Box sx={{ px: 3, py: 3 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}>
            <CircularProgress size={24} />
            <Typography color="primary" fontWeight="bold">
              {uploadProgress !== null && uploadProgress < 100
                ? `Feltöltés folyamatban... ${uploadProgress}%`
                : "Feldolgozás indítása..."}
            </Typography>
          </Box>
          {uploadProgress !== null && uploadProgress < 100 && (
            <LinearProgress variant="determinate" value={uploadProgress} />
          )}
        </Box>
      ) : (
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={handleClose} color="inherit">
            Mégse
          </Button>
          <Button
            onClick={handleUpload}
            variant="contained"
            color="primary"
            disabled={!isFormValid}
          >
            Feltöltés indítása
          </Button>
        </DialogActions>
      )}
    </Dialog>
  );
};
