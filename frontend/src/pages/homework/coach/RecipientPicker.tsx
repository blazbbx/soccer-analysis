import { useMemo, useState } from "react";
import { Checkbox, FormControlLabel, List, ListItem, Stack, Switch, TextField, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { listRecipients } from "../../../api/homework";
import { formatPersonName } from "../../../utils/stringUtils";

export const RecipientPicker = ({
  teamId,
  entireRoster,
  selectedIds,
  excludedIds,
  allowEntire = true,
  onChange,
}: {
  teamId: string;
  entireRoster: boolean;
  selectedIds: string[];
  /** Who already has a row, when adding to a published homework. */
  excludedIds?: string[];
  allowEntire?: boolean;
  onChange: (entireRoster: boolean, selectedIds: string[]) => void;
}) => {
  const { t } = useTranslation();
  const [search, setSearch] = useState("");
  const roster = useQuery({
    queryKey: ["homework", "recipients", teamId],
    queryFn: ({ signal }) => listRecipients(teamId, signal),
    enabled: Boolean(teamId),
  });

  const excluded = useMemo(() => new Set(excludedIds ?? []), [excludedIds]);
  const players = (roster.data ?? []).filter((player) => !excluded.has(player.playerProfileId));
  const visible = players.filter((player) =>
    formatPersonName(player.firstName, player.lastName).toLowerCase().includes(search.trim().toLowerCase()));
  const selected = new Set(selectedIds);
  const count = entireRoster ? players.length : selectedIds.length;

  const toggle = (id: string) =>
    onChange(false, selected.has(id) ? selectedIds.filter((other) => other !== id) : [...selectedIds, id]);

  return (
    <Stack spacing={1}>
      {allowEntire && (
        <FormControlLabel
          control={<Switch checked={entireRoster} onChange={(event) => onChange(event.target.checked, event.target.checked ? [] : selectedIds)} />}
          label={t("homework.recipients.entire")}
        />
      )}
      <Typography variant="body2" sx={{ fontWeight: 600 }}>{t("homework.recipients.count", { count })}</Typography>
      {!entireRoster && (
        <>
          <TextField size="small" fullWidth label={t("homework.recipients.search")} value={search} onChange={(event) => setSearch(event.target.value)} />
          {roster.data && players.length === 0 && (
            <Typography variant="body2" sx={{ color: "text.secondary" }}>{t("homework.recipients.no-roster")}</Typography>
          )}
          <List dense disablePadding sx={{ maxHeight: 320, overflowY: "auto" }}>
            {visible.map((player) => (
              <ListItem key={player.playerProfileId} disablePadding>
                <FormControlLabel
                  sx={{ ml: 0 }}
                  control={<Checkbox size="small" checked={selected.has(player.playerProfileId)} onChange={() => toggle(player.playerProfileId)} />}
                  label={formatPersonName(player.firstName, player.lastName)}
                />
              </ListItem>
            ))}
          </List>
        </>
      )}
    </Stack>
  );
};
