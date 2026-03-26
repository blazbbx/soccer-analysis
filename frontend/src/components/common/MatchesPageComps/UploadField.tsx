import { Box, Typography } from "@mui/material";
import CloudUploadIcon from "@mui/icons-material/CloudUpload";
import { useDropzone, type DropzoneOptions } from "react-dropzone";



export const UploadField = (props: DropzoneOptions) => { 
  const { getRootProps, getInputProps, isDragActive } = useDropzone({    ...props,
  });

  return (
    <Box
      {...getRootProps()}
      sx={{
        border: "2px dashed",   
        borderColor: isDragActive ? "text.primary" : "divider",       
        backgroundColor: isDragActive ? "background.paper" : "secondary.main",
        borderRadius: 2, 
        padding: 4,
        textAlign: "center",
        cursor: "pointer",
        transition: "all 0.2s ease-in-out",
        "&:hover": {
          borderColor: "text.secondary",
          backgroundColor: "background.paper",
        },
      }}
    >
      <input {...getInputProps()} />
      <CloudUploadIcon
        sx={{
          fontSize: 48,
          color: isDragActive ? "primary.main" : "grey.500",
          mb: 2,
        }}
      />

      {isDragActive ? (
        <Typography variant="body1" color="primary">
          Húzd ide a videót...
        </Typography>
      ) : (
        <>
          <Typography variant="body1" gutterBottom>
            <strong>Húzd ide</strong> az MP4 fájlt, vagy{" "}
            <strong>kattints</strong> a tallózáshoz
          </Typography>
          <Typography variant="body2" color="textSecondary">
            Csak .mp4 kiterjesztésű fájlok támogatottak
          </Typography>
        </>
      )}
    </Box>
  );
};
