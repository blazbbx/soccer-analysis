import { useState, useEffect } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stack,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormHelperText,
} from "@mui/material";
import { DateTimePicker } from "@mui/x-date-pickers/DateTimePicker";
import dayjs, { type Dayjs } from "dayjs";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { type CupMatchResponse, type CupTeamResponse } from "../../../api/generated/model";

interface EditCupMatchDialogProps {
  open: boolean;
  onClose: () => void;
  onSave: (homeTeamId: string, awayTeamId: string, scheduledAt: string) => void;
  match: CupMatchResponse | null;
  teams: CupTeamResponse[];
}

interface MatchErrors {
  home: boolean;
  away: boolean;
  date: boolean;
  same: boolean;
}

export const EditCupMatchDialog = ({ open, onClose, onSave, match, teams }: EditCupMatchDialogProps) => {
  const { t } = useTranslation();
  const [homeTeamId, setHomeTeamId] = useState("");
  const [awayTeamId, setAwayTeamId] = useState("");
  const [scheduledAt, setScheduledAt] = useState<Dayjs | null>(null);
  const [errors, setErrors] = useState<MatchErrors>({ home: false, away: false, date: false, same: false });

  useEffect(() => {
    if (match) {
      setHomeTeamId(match.homeTeam?.id ?? "");
      setAwayTeamId(match.awayTeam?.id ?? "");
      setScheduledAt(match.scheduledAt ? dayjs(match.scheduledAt) : null);
    }
  }, [match]);

  const handleSubmit = () => {
    const e: MatchErrors = {
      home: homeTeamId === "",
      away: awayTeamId === "",
      date: scheduledAt === null,
      same: homeTeamId !== "" && homeTeamId === awayTeamId,
    };
    setErrors(e);
    if (e.home || e.away || e.date || e.same) return;
    onSave(homeTeamId, awayTeamId, scheduledAt!.format("YYYY-MM-DDTHH:mm:ss"));
  };

  const handleClose = () => {
    setErrors({ home: false, away: false, date: false, same: false });
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("admin.edit-cup-match")}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <FormControl fullWidth error={errors.home}>
            <InputLabel>{t("cups.home-team")}</InputLabel>
            <Select
              value={homeTeamId}
              onChange={(e) => {
                setHomeTeamId(e.target.value);
                setErrors((prev) => ({ ...prev, home: false, same: false }));
              }}
              label={t("cups.home-team")}
            >
              {teams.map((team) => (
                <MenuItem key={team.id} value={team.id ?? ""}>
                  {team.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl fullWidth error={errors.away || errors.same}>
            <InputLabel>{t("cups.away-team")}</InputLabel>
            <Select
              value={awayTeamId}
              onChange={(e) => {
                setAwayTeamId(e.target.value);
                setErrors((prev) => ({ ...prev, away: false, same: false }));
              }}
              label={t("cups.away-team")}
            >
              {teams.map((team) => (
                <MenuItem key={team.id} value={team.id ?? ""}>
                  {team.name}
                </MenuItem>
              ))}
            </Select>
            {errors.same && (
              <FormHelperText>{t("cups.same-team-error")}</FormHelperText>
            )}
          </FormControl>
          <DateTimePicker
            label={t("cups.match-date")}
            value={scheduledAt}
            onChange={(val) => {
              setScheduledAt(val);
              setErrors((prev) => ({ ...prev, date: false }));
            }}
            slotProps={{
              textField: {
                fullWidth: true,
                error: errors.date,
                helperText: errors.date ? t("cups.name-error") : "",
              },
            }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={handleClose}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit} variant="contained">
          {t("common.save")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
