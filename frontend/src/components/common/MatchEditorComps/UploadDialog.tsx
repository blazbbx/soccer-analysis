import React, { useState, useCallback } from "react";
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
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { FileRejection } from "react-dropzone";
import { UploadField } from "./UploadField";

export const UploadDialog = ({
  open,
  onClose,
  onUpload,
  isUploading
}: {
  open: boolean;
  onClose: () => void;
  onUpload: (file: File) => void;
  isUploading: boolean;
}) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);  

  
  const onDrop = useCallback(
    (acceptedFiles: File[], fileRejections: FileRejection[]) => {
      if(isUploading) return; 

      setError(null);

      if (acceptedFiles.length > 0) {
        setSelectedFile(acceptedFiles[0]);
      }

      if (fileRejections.length > 0) {
        setError("Csak MP4 formátumú videót tölthetsz fel!");
      }
    },
    [],
  );

  const handleUpload = () => {
    if (!selectedFile) return;
    console.log("Feltöltésre váró fájl:", selectedFile);    

    onUpload(selectedFile);
  };

  const handleClose = () => {
    if (isUploading) return;
    setSelectedFile(null);
    setError(null);
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        Mérkőzés videó feltöltése
        <IconButton onClick={handleClose} size="small" disabled={isUploading}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers>
        {/* Dropzone Terület */}
        <UploadField
          onDrop={onDrop}
          accept={{ "video/mp4": [".mp4"] }}
          maxFiles={1}
          disabled={isUploading}
        />

        {/* Kiválasztott fájl kijelzése */}
        {selectedFile && !isUploading && (
          <Box
            sx={{
              mt: 3,
              p: 2,
              bgcolor: "success.50",
              borderRadius: 1,
              border: "1px solid",
              borderColor: "success.200",
            }}
          >
            <Typography variant="body2" color="success.main">
              <strong>Kiválasztott fájl:</strong> {selectedFile.name} (
              {(selectedFile.size / (1024 * 1024)).toFixed(2)} MB)
            </Typography>
          </Box>
        )}

        {/* Hibaüzenet kijelzése */}
        {error && (
          <Typography
            color="error"
            variant="body2"
            sx={{ mt: 2, textAlign: "center" }}
          >
            {error}
          </Typography>
        )}
      </DialogContent>

      {isUploading ? (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 2, px: 3, py: 3 }}>
          <CircularProgress size={24} />
          <Typography color="primary" fontWeight="bold">
            Feltöltés és feldolgozás folyamatban...
          </Typography>
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
            disabled={!selectedFile}
          >
            Feltöltés indítása
          </Button>
        </DialogActions>
      )}
    </Dialog>
  );
};
