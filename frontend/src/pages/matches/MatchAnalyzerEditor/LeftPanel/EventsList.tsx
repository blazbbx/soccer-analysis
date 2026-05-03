import React from "react";
import { Box, Typography, Stack, type SvgIconProps, useTheme } from "@mui/material";
import { useVideoPlayer } from "../../../../context/VideoPlayerContext";
import { formatTime } from "../../../../utils/timeFormat";

export const EventsList: React.FC = () => {
  const { setCurrentTime, labels } = useVideoPlayer();
  const theme = useTheme();

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
          Események
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
            Még nincsenek események
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
                    {label.config.event}
                  </Typography>
                </Box>
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
              </Box>
            ))
        )}
      </Stack>
    </Box>
  );
};
