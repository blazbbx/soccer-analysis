import { Box, Typography, IconButton } from "@mui/material";
import { useTranslation } from "react-i18next";

export const LanguageSwitcher = () => {
  const { i18n } = useTranslation();

  const toggleLanguage = () => {
    
    const nextLang = i18n.language === 'hu' ? 'en' : 'hu';
    i18n.changeLanguage(nextLang);
  };

  return (
    <Box
      sx={{
        px: 2,
        py: 1,
        display: "flex",
        justifyContent: "flex-start",
      }}
    >
      <Box
        onClick={toggleLanguage}
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 1,
          p: 0.5,
          borderRadius: 2,
          bgcolor: "action.hover",
          border: 1,
          borderColor: "divider",
          cursor: "pointer", 
          transition: "background-color 0.2s",
          "&:hover": {
            bgcolor: "action.selected",
          }
        }}
      >
        <Typography
          variant="caption"
          sx={{ pl: 1, color: "text.secondary", fontWeight: 500 }}
        >
          {i18n.language === 'hu' ? 'Magyar' : 'English'}
        </Typography>
        <IconButton
          size="small"
          disableRipple 
          sx={{ color: "text.secondary", fontSize: "1.2rem" }}
        >
          {i18n.language === 'hu' ? '🇭🇺' : '🇬🇧'}
        </IconButton>
      </Box>
    </Box>
  );
};