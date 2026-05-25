import React from "react";
import { Box, Typography, Stack, useTheme } from "@mui/material";
import { useTranslation } from "react-i18next";
import { LABEL_ITEMS } from "../../../../constants/labels";

export const HotkeysPanel: React.FC = () => {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <Box sx={{ p: 2, backgroundColor: theme.palette.background.default }}>
      <Typography sx={{ color: theme.palette.text.secondary, fontWeight: "bold", mb: 1 }}>
        {t("editor.hotkeys")}
      </Typography>

      <Stack spacing={1}>
        <Box sx={{ display: "flex", justifyContent: "space-between" }}>
          <Typography sx={{ color: theme.palette.text.secondary, fontSize: "13px" }}>
            {t("editor.start-stop")}
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
            {t("editor.space")}
          </Typography>
        </Box>
      </Stack>

      <Box sx={{ height: "1px", backgroundColor: theme.palette.divider, my: 1 }} />

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
            <Typography sx={{ color: theme.palette.text.primary, fontSize: "13px" }}>
              {t(item.event)}
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
              {item.hotkey.toUpperCase()}
            </Typography>
          </Box>
        ))}
      </Stack>
    </Box>
  );
};
