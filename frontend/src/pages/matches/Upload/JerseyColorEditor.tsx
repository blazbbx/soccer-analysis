import React from "react";
import { Box, Button, Stack, Typography } from "@mui/material";
import SwapVertIcon from "@mui/icons-material/SwapVert";
import { useTranslation } from "react-i18next";
import type { JerseyColors, TeamColorKey } from "../hooks/useMatchUploadFlow";

interface JerseyColorEditorProps {
  colors: JerseyColors;
  onChange: (colors: JerseyColors) => void;
  /** True when the colors came from the field-detection suggestion (shows a hint). */
  suggested: boolean;
}

interface ColorSwatchProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
}

/** The hex code and a swatch that opens the browser's color picker. */
const ColorSwatch = ({ label, value, onChange }: ColorSwatchProps) => (
  <Stack direction="row" spacing={2} alignItems="center">
    <Typography variant="caption" sx={{ fontFamily: "monospace", color: "text.secondary", minWidth: 64 }}>
      {value}
    </Typography>
    <Box
      component="label"
      aria-label={label}
      sx={{
        width: 44,
        height: 44,
        borderRadius: 1,
        border: "2px solid",
        borderColor: "divider",
        bgcolor: value,
        cursor: "pointer",
        display: "block",
        position: "relative",
        "&:hover": { borderColor: "text.secondary" },
      }}
    >
      <Box
        component="input"
        type="color"
        value={value}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
        sx={{ opacity: 0, width: 0, height: 0, position: "absolute" }}
      />
    </Box>
  </Stack>
);

/**
 * Edits the three reference colors the team classifier compares each detected player against
 * (home, away, referee). Pre-filled from the field detection suggestion; the user corrects
 * whatever the detector got wrong. Which cluster is "home" cannot be known by the detector,
 * hence the swap button. There is no goalkeeper color: the classifier is meant to treat a person
 * whose kit matches none of the three well enough as the goalkeeper (threshold still off, see
 * GOALKEEPER_MAX_DELTA_E in python/ml/object_detection/team_classifier.py).
 */
export const JerseyColorEditor = ({ colors, onChange, suggested }: JerseyColorEditorProps) => {
  const { t } = useTranslation();

  const rows: { key: TeamColorKey; label: string }[] = [
    { key: "home", label: t("upload.home-jersey") },
    { key: "away", label: t("upload.away-jersey") },
    { key: "referee", label: t("upload.referee-jersey") },
  ];

  return (
    <Stack spacing={1.5} sx={{ mt: 3 }}>
      <Typography variant="body2" fontWeight="bold">
        {t("upload.jersey-colors")}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {suggested ? t("upload.fieldDetected.colorsSuggested") : t("upload.fieldDetected.colorsManual")}
      </Typography>

      {rows.map(({ key, label }) => (
        <Stack key={key} direction="row" spacing={2} alignItems="center">
          <Typography sx={{ flex: 1 }}>{label}</Typography>
          <ColorSwatch label={label} value={colors[key]} onChange={(value) => onChange({ ...colors, [key]: value })} />
        </Stack>
      ))}

      <Box>
        <Button
          size="small"
          startIcon={<SwapVertIcon />}
          onClick={() =>
            onChange({
              ...colors,
              home: colors.away,
              away: colors.home,
            })
          }
        >
          {t("upload.fieldDetected.swapColors")}
        </Button>
      </Box>
    </Stack>
  );
};
