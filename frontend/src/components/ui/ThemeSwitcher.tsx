import { Box, IconButton, Typography } from "@mui/material";
import {
  LightMode as LightModeIcon,
  DarkMode as DarkmodeIcon,
} from "@mui/icons-material";
import { useColorMode } from "../../context/ThemeContext";
import { useTranslation } from "react-i18next";

export const ThemeSwitcher = () => {
    const {t} = useTranslation();
    const { mode, toggleColorMode } = useColorMode();
    
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1,
        p: 0.5,
        borderRadius: 2,
        bgcolor: "action.hover",
        border: 1,
        borderColor: "divider",
      }}
    >
      <Typography
        variant="caption"
        sx={{ pl: 1, color: "text.secondary", fontWeight: 500 }}
      >
        {mode === "dark" ? t("theme.lightmode") : t("theme.darkmode")}
      </Typography>
      <IconButton
        onClick={toggleColorMode}
        size="small"
        sx={{ color: "text.secondary" }}
      >
        {mode === "dark" ? (
          <LightModeIcon fontSize="small" />
        ) : (
          <DarkmodeIcon fontSize="small" />
        )}
      </IconButton>
    </Box>
  );
};
