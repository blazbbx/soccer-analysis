import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { Box, Typography, Stack, IconButton, type SvgIconProps, useTheme } from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import { useVideoPlayer, type PlacedLabel } from "../../../../context/VideoPlayerContext";
import { formatTime } from "../../../../utils/timeFormat";
import { EditLabelDialog } from "./EditLabelDialog";

export const EventsList: React.FC = () => {
  const { setCurrentTime, labels, deleteLabel, updateLabel } = useVideoPlayer();
  const theme = useTheme();
  const { t } = useTranslation();
  const [editingLabel, setEditingLabel] = useState<PlacedLabel | null>(null);

  return (
    <Box
      sx={{
        p: 2,
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        borderBottom: `1px solid ${theme.palette.divider}`,
      }}
    >
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 2 }}
      >
        <Typography
          sx={{
            color: theme.palette.text.secondary,
            fontWeight: "bold",
            fontSize: "14px",
            textTransform: "uppercase",
            letterSpacing: "1px",
          }}
        >
          {t("editor.events")}
        </Typography>
        <Typography
          sx={{
            color: theme.palette.text.primary,
            fontSize: "12px",
            bgcolor: theme.palette.action.selected,
            px: 1,
            borderRadius: "4px",
          }}
        >
          {labels.length}
        </Typography>
      </Stack>

      <Stack spacing={1}>
        {labels.length === 0 ? (
          <Typography
            sx={{
              color: theme.palette.text.secondary,
              fontSize: "13px",
              fontStyle: "italic",
              textAlign: "center",
              mt: 2,
            }}
          >
            {t("editor.no-events")}
          </Typography>
        ) : (
          labels
            .sort((a, b) => a.time - b.time)
            .map((label) => (
              <Box
                key={label.id}
                onClick={() => {                  
                  setCurrentTime(label.time);
                }}
                sx={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  p: 1,
                  borderRadius: "4px",
                  cursor: "pointer",
                  transition: "background 0.2s",
                  "&:hover": {
                    backgroundColor: theme.palette.action.hover,
                  },
                }}
              >
                <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
                  {React.cloneElement(
                    label.config.icon as React.ReactElement<SvgIconProps>,
                    { sx: { color: label.config.color, fontSize: "18px" } },
                  )}
                  <Typography
                    sx={{
                      color: theme.palette.text.primary,
                      fontSize: "13px",
                      fontWeight: 500,
                    }}
                  >
                    {t(label.config.event)}
                  </Typography>
                </Box>
                <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <Typography
                    sx={{
                      color: label.config.color,
                      fontSize: "12px",
                      fontFamily: "monospace",
                      opacity: 0.8,
                    }}
                  >
                    {formatTime(label.time)}
                  </Typography>
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation();
                      setEditingLabel(label);
                    }}
                    sx={{
                      color: theme.palette.text.secondary,
                      opacity: 0.5,
                      "&:hover": { opacity: 1, color: theme.palette.primary.main },
                    }}
                  >
                    <EditIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation();
                      deleteLabel(label.id);
                    }}
                    sx={{
                      color: theme.palette.text.secondary,
                      opacity: 0.5,
                      "&:hover": { opacity: 1, color: theme.palette.error.main },
                    }}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Box>
              </Box>
            ))
        )}
      </Stack>

      <EditLabelDialog
        open={editingLabel !== null}
        label={editingLabel}
        onSave={(config) => {
          updateLabel(editingLabel!.id, config);
          setEditingLabel(null);
        }}
        onClose={() => setEditingLabel(null)}
      />
    </Box>
  );
};
