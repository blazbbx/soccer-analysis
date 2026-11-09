import { useEffect, useMemo, useState } from "react";
import {
  Button, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItem, ListItemButton,
  ListItemIcon, ListItemText, Stack, TextField, Typography,
} from "@mui/material";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { addAssignees, homeworkErrorMessage, listRecipients, removeAssignee, type AssignmentRow } from "../../../api/homework";
import { useSnackbar } from "../../../context/SnackbarContext";
import { formatPersonName } from "../../../utils/stringUtils";

/**
 * The whole roster with a tick for each player who has the homework. Ticking adds, unticking takes away; work already
 * handed in stays and cannot be unticked. Nothing changes until Save, and the line above it says what will.
 */
export const ManageRecipientsDialog = ({
  open,
  teamId,
  homeworkId,
  rows,
  onClose,
  onSaved,
}: {
  open: boolean;
  teamId: string;
  homeworkId: string;
  rows: AssignmentRow[];
  onClose: () => void;
  onSaved: () => void;
}) => {
  const { t } = useTranslation();
  const { showError } = useSnackbar();
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const roster = useQuery({
    queryKey: ["homework", "recipients", teamId],
    queryFn: ({ signal }) => listRecipients(teamId, signal),
    enabled: open && Boolean(teamId),
  });

  const rowByProfile = useMemo(() => new Map(rows.map((row) => [row.playerProfileId, row])), [rows]);
  // Start from what is stored each time it opens, so a closed dialog leaves nothing half-chosen behind. Only on opening:
  // a refetch while it is open must not undo the ticks.
  useEffect(() => {
    if (open) {
      setSelected(new Set(rows.map((row) => row.playerProfileId)));
      setSearch("");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const players = roster.data ?? [];
  const handedIn = (profileId: string) => Boolean(rowByProfile.get(profileId)?.submittedAt);
  const toAdd = players.filter((player) => selected.has(player.playerProfileId) && !rowByProfile.has(player.playerProfileId))
    .map((player) => player.playerProfileId);
  const toRemove = players.filter((player) => !selected.has(player.playerProfileId) && rowByProfile.has(player.playerProfileId))
    .map((player) => player.playerProfileId);
  const visible = players.filter((player) =>
    formatPersonName(player.firstName, player.lastName).toLowerCase().includes(search.trim().toLowerCase()));

  const toggle = (profileId: string) => {
    if (handedIn(profileId)) return;
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(profileId)) next.delete(profileId);
      else next.add(profileId);
      return next;
    });
  };

  // The additions in one call, then each removal: a failed removal leaves the others done, and the list reloads to show it.
  const save = useMutation({
    mutationFn: async () => {
      if (toAdd.length > 0) await addAssignees(teamId, homeworkId, toAdd);
      for (const profileId of toRemove) await removeAssignee(teamId, homeworkId, profileId);
    },
    onSuccess: () => {
      onSaved();
      onClose();
    },
    onError: (error) => {
      showError(homeworkErrorMessage(error, t("homework.save-failed")));
      onSaved();
    },
  });

  const changes = [
    toAdd.length > 0 ? t("homework.recipients.will-add", { count: toAdd.length }) : null,
    toRemove.length > 0 ? t("homework.recipients.will-remove", { count: toRemove.length }) : null,
  ].filter(Boolean).join(", ");

  return (
    <Dialog open={open} onClose={save.isPending ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t("homework.recipients.manage")}</DialogTitle>
      <DialogContent>
        <Stack spacing={1.5} sx={{ pt: 1 }}>
          <TextField size="small" fullWidth label={t("homework.recipients.search")} value={search} onChange={(event) => setSearch(event.target.value)} />
          {roster.isLoading && <CircularProgress size={24} />}
          {roster.data && players.length === 0 && (
            <Typography variant="body2" sx={{ color: "text.secondary" }}>{t("homework.recipients.no-roster")}</Typography>
          )}
          <List dense disablePadding sx={{ maxHeight: 400, overflowY: "auto" }}>
            {visible.map((player) => {
              const locked = handedIn(player.playerProfileId);
              return (
                <ListItem key={player.playerProfileId} disablePadding>
                  <ListItemButton dense disabled={locked || save.isPending} onClick={() => toggle(player.playerProfileId)}>
                    <ListItemIcon sx={{ minWidth: 36 }}>
                      <Checkbox edge="start" size="small" checked={selected.has(player.playerProfileId)} tabIndex={-1} disableRipple />
                    </ListItemIcon>
                    <ListItemText primary={formatPersonName(player.firstName, player.lastName)} />
                    {locked && <Chip size="small" variant="outlined" label={t("homework.recipients.handed-in")} />}
                  </ListItemButton>
                </ListItem>
              );
            })}
          </List>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2, justifyContent: "space-between" }}>
        <Typography variant="body2" sx={{ color: "text.secondary" }}>{changes || t("homework.recipients.no-change")}</Typography>
        <Stack direction="row" spacing={1}>
          <Button onClick={onClose} disabled={save.isPending}>{t("homework.editor.cancel")}</Button>
          <Button variant="contained" disabled={save.isPending || !changes} onClick={() => save.mutate()}>{t("homework.recipients.save")}</Button>
        </Stack>
      </DialogActions>
    </Dialog>
  );
};
