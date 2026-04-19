import React from "react";
import {
  Box,
  Typography,
  Stack,
  IconButton,
  Button,
  TextField,
  useTheme,
} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import ContentCutIcon from "@mui/icons-material/ContentCut";
import { useVideoPlayer } from "../../../../../context/VideoPlayerContext";
import { formatTime } from "../../../../../utils/timeFormat";

export const ClipsSidebar: React.FC = () => {
  const {
    clips,
    updateClipName,
    toggleClipEditMode,
    deleteClip,
    setCurrentTime,
  } = useVideoPlayer();

  const theme = useTheme();

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        flex: 1,
        minHeight: 0,
        bgcolor: theme.palette.background.paper,
        borderLeft: `1px solid ${theme.palette.divider}`,
      }}
    >
      {/* Fejléc */}
      <Box
        sx={{
          p: 2,
          borderBottom: `1px solid ${theme.palette.divider}`,
          display: "flex",
          alignItems: "center",
          gap: 1,
        }}
      >
        <ContentCutIcon
          sx={{
            color: "#8b5cf6",
            fontSize: "18px",
            transform: "rotate(270deg)",
          }}
        />
        <Typography
          sx={{
            color: theme.palette.text.secondary,
            fontWeight: "bold",
            fontSize: "14px",
            textTransform: "uppercase",
          }}
        >
          Klippek ({clips.length})
        </Typography>
      </Box>

      {/* Klipek listája */}
      <Box sx={{ flex: 1, overflowY: "auto", p: 2 }}>
        <Stack spacing={2}>
          {clips.length === 0 ? (
            <Typography
              sx={{
                color: theme.palette.text.secondary,
                fontSize: "13px",
                fontStyle: "italic",
                textAlign: "center",
              }}
            >
              Még nincsenek klipek.
            </Typography>
          ) : (
            clips.map((clip) => (
              <Box
                key={clip.id}
                sx={{
                  backgroundColor: theme.palette.background.default,
                  borderRadius: 1,
                  p: 2,
                  border: clip.isEditing
                    ? `1px solid ${clip.color}`
                    : `1px solid ${theme.palette.divider}`,
                }}
              >
                {clip.isEditing ? (
                 
                  <Stack spacing={1.5}>
                    <TextField
                      size="small"
                      value={clip.name}
                      onChange={(e) => updateClipName(clip.id, e.target.value)}
                      placeholder="Klip neve..."
                      autoFocus
                      sx={{
                        input: { color: theme.palette.text.primary, fontSize: "14px" },
                        "& .MuiOutlinedInput-root": {
                          "& fieldset": { color: theme.palette.divider },
                          "&:hover fieldset": { borderColor: clip.color },
                          "&.Mui-focused fieldset": { borderColor: clip.color },
                        },
                      }}
                    />
                    <Stack
                      direction="row"
                      spacing={1}
                      justifyContent="flex-end"
                    >
                      <Button
                        size="small"
                        color="error"
                        onClick={() => deleteClip(clip.id)}
                      >
                        Törlés
                      </Button>
                      <Button
                        size="small"
                        variant="contained"
                        sx={{
                          bgcolor: clip.color,
                          color: "#fff",
                          "&:hover": { bgcolor: clip.color },
                        }}
                        onClick={() => toggleClipEditMode(clip.id, false)}
                      >
                        Mentés
                      </Button>
                    </Stack>
                  </Stack>
                ) : (
                 
                  <Stack spacing={1}>
                    <Typography
                      sx={{ color: theme.palette.text.primary, fontSize: "14px", fontWeight: 500 }}
                    >
                      {clip.name}
                    </Typography>
                    <Box
                      sx={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                      }}
                    >
                      <Typography
                        onClick={() => setCurrentTime(clip.startTime)}
                        sx={{
                          color: theme.palette.text.secondary,
                          fontSize: "13px",
                          fontFamily: "monospace",
                          cursor: "color 0.2s",
                          "&:hover": { color: clip.color },
                        }}
                      >
                        {formatTime(clip.startTime)} →{" "}
                        {formatTime(clip.endTime)}
                      </Typography>
                      <Box>
                        <IconButton
                          size="small"
                          onClick={() => toggleClipEditMode(clip.id, true)}
                          sx={{ color: "#00e676" }}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          onClick={() => deleteClip(clip.id)}
                          sx={{ color: "#f44336" }}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Box>
                    </Box>
                  </Stack>
                )}
              </Box>
            ))
          )}
        </Stack>
      </Box>
    </Box>
  );
};
