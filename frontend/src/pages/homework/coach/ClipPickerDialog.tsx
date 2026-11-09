import { useState } from "react";
import {
  Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItemButton, ListItemText, MenuItem, Stack, TextField, Typography,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import type { ClipResponse } from "../../../api/generated/model";
import { usePagedClips, usePagedMatches, useListPage } from "../../../api/pagedLists";
import { PICKER_PAGE_SIZE } from "../../../api/paging";
import { ListPager } from "../../../components/ListPager";
import { useView } from "../../../context/ViewContext";

const UNUSABLE = new Set(["PENDING_UPLOAD", "FAILED"]);

/**
 * Match, then clip. Only the matches of the homework's team: a homework holds no other clip. A clip that failed or
 * was never uploaded cannot go out, so it is shown but not offered.
 */
export const ClipPickerDialog = ({
  open,
  teamId,
  onClose,
  onPick,
}: {
  open: boolean;
  teamId: string;
  onClose: () => void;
  onPick: (clip: ClipResponse) => void;
}) => {
  const { t } = useTranslation();
  const { activeView } = useView();
  const [matchId, setMatchId] = useState("");
  const [page, setPage] = useListPage(matchId);
  const [matchPage, setMatchPage] = useListPage(teamId);
  const matches = usePagedMatches({
    page: matchPage,
    size: PICKER_PAGE_SIZE,
    view: activeView?.toUpperCase() ?? "",
    teamIds: [teamId],
    enabled: open && !!teamId,
  });
  const clips = usePagedClips(open ? matchId : "", page);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t("homework.clip-picker.title")}</DialogTitle>
      <DialogContent>
        <Stack spacing={1.5} sx={{ pt: 1 }}>
          <TextField select size="small" label={t("homework.clip-picker.match")} value={matchId} onChange={(event) => setMatchId(event.target.value)}>
            {(matches.data?.items ?? []).map((match) => (
              <MenuItem key={match.id} value={match.id ?? ""}>
                {match.originalFileName ?? `${match.homeTeamName ?? ""} – ${match.awayTeamName ?? ""}`}
              </MenuItem>
            ))}
          </TextField>
          <ListPager page={matchPage} totalPages={matches.data?.totalPages ?? 0} onChange={setMatchPage} />
          {!matchId && <Typography variant="body2" sx={{ color: "text.secondary" }}>{t("homework.clip-picker.select-match")}</Typography>}
          {matchId && clips.data?.items.length === 0 && (
            <Typography variant="body2" sx={{ color: "text.secondary" }}>{t("homework.clip-picker.no-clips")}</Typography>
          )}
          <List disablePadding>
            {(clips.data?.items ?? []).map((clip) => (
              <ListItemButton key={clip.id} disabled={UNUSABLE.has(clip.renderStatus ?? "")} onClick={() => onPick(clip)}>
                <ListItemText primary={clip.name} />
                <Chip size="small" variant="outlined" label={clip.renderStatus ?? "—"} />
              </ListItemButton>
            ))}
          </List>
          <ListPager page={page} totalPages={clips.data?.totalPages ?? 0} onChange={setPage} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("homework.editor.cancel")}</Button>
      </DialogActions>
    </Dialog>
  );
};
