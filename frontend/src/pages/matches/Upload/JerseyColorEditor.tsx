import React from "react";
import { Box, Button, Checkbox, FormControlLabel, Stack, Typography } from "@mui/material";
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
 * Edits the reference colors the team classifier compares each detected player against: the
 * three the field detection suggests (home, away, referee) and the two optional goalkeeper
 * colors. Pre-filled from the suggestion; the user corrects whatever the detector got wrong.
 * Which cluster is "home" cannot be known by the detector, hence the swap button.
 *
 * A goalkeeper color is only stored if the box is ticked: the default is "same as the team",
 * which is what the classifier does when no goalkeeper color is given.
 */
export const JerseyColorEditor = ({ colors, onChange, suggested }: JerseyColorEditorProps) => {
  const { t } = useTranslation();

  const rows: { key: TeamColorKey; label: string }[] = [
    { key: "home", label: t("upload.home-jersey") },
    { key: "away", label: t("upload.away-jersey") },
    { key: "referee", label: t("upload.referee-jersey") },
  ];

  const goalkeeperRows: {
    key: "homeGoalkeeper" | "awayGoalkeeper";
    team: "home" | "away";
    label: string;
    checkboxLabel: string;
  }[] = [
    {
      key: "homeGoalkeeper",
      team: "home",
      label: t("upload.home-goalkeeper-jersey"),
      checkboxLabel: t("upload.fieldDetected.homeGoalkeeperDifferent"),
    },
    {
      key: "awayGoalkeeper",
      team: "away",
      label: t("upload.away-goalkeeper-jersey"),
      checkboxLabel: t("upload.fieldDetected.awayGoalkeeperDifferent"),
    },
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
              // a goalkeeper belongs to the team, so the goalkeeper colors swap with it
              homeGoalkeeper: colors.awayGoalkeeper,
              awayGoalkeeper: colors.homeGoalkeeper,
            })
          }
        >
          {t("upload.fieldDetected.swapColors")}
        </Button>
      </Box>

      <Typography variant="body2" fontWeight="bold" sx={{ pt: 1 }}>
        {t("upload.fieldDetected.goalkeepersTitle")}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {t("upload.fieldDetected.goalkeepersHint")}
      </Typography>

      {goalkeeperRows.map(({ key, team, label, checkboxLabel }) => {
        const value = colors[key];
        return (
          <Stack key={key} direction="row" spacing={2} alignItems="center" sx={{ minHeight: 44 }}>
            <FormControlLabel
              sx={{ flex: 1, mr: 0 }}
              label={checkboxLabel}
              control={
                <Checkbox
                  checked={value !== null}
                  // ticking starts from the team color, so the picker opens close to the right shade
                  onChange={(e) => onChange({ ...colors, [key]: e.target.checked ? colors[team] : null })}
                />
              }
            />
            {value !== null && (
              <ColorSwatch label={label} value={value} onChange={(next) => onChange({ ...colors, [key]: next })} />
            )}
          </Stack>
        );
      })}
    </Stack>
  );
};
