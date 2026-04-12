import React from "react";
import { Box, Typography, Stack, type SvgIconProps, useTheme } from "@mui/material";
import { LABEL_ITEMS } from "../../../../../constants/labels";
import { useVideoPlayer } from "../../../../../context/VideoPlayerContext";
import { formatTime } from "../../../../../utils/timeFormat";

export const EventsSideBar: React.FC = () => {
  const { labels, setCurrentTime } = useVideoPlayer();
  const theme = useTheme();

  return (
    <Box
      sx={{
        width: "260px",
        backgroundColor: theme.palette.background.paper,
        borderRight: `1px solid ${theme.palette.divider}`,
        display: "flex",
        flexDirection: "column",
        height: "100%",
      }}
    >
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
                  onClick={() => setCurrentTime(label.time)}
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
                      {
                        sx: { color: label.config.color, fontSize: "18px" },
                      },
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
      <Box sx={{ p: 2, backgroundColor: theme.palette.background.default }}>
        <Typography sx={{ color: theme.palette.text.secondary, fontWeight: "bold", mb: 1 }}>
          Gyorsgombok
        </Typography>

        <Stack spacing={1}>
          <Box sx={{ display: "flex", justifyContent: "space-between" }}>
            <Typography sx={{ color: theme.palette.text.secondary, fontSize: "13px" }}>
              Start/Stop
            </Typography>
            <Typography
              sx={{
                color: "#00e676",
                fontSize: "13px",
                border: "1px solid #00e676",
                borderRadius: "4px",
                px: 1,
              }}
            >
              Szóköz
            </Typography>
          </Box>
        </Stack>

        <Box sx={{ height: "1px", backgroundColor: theme.palette.divider, my: 1 }} />

        {/* Dinamikus labelek a konstansból */}
        <Stack spacing={1.5}>
          {LABEL_ITEMS.map((item, index) => (
            <Box
              key={index}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
              }}
            >
              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <Typography sx={{ color: theme.palette.text.primary, fontSize: "13px" }}>
                  {item.event}
                </Typography>
              </Box>
              <Typography
                sx={{
                  color: "#00e676",
                  fontSize: "13px",
                  border: "1px solid #00e676",
                  borderRadius: "4px",
                  px: 1,
                }}
              >
                {item.hotkey.toUpperCase()}
              </Typography>
            </Box>
          ))}
        </Stack>
      </Box>
    </Box>
  );
};
