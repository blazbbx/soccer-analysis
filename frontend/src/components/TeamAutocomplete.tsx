import { Autocomplete, TextField } from "@mui/material";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useTeamCatalog } from "../api/roleTeams";
import { useChildScope } from "../hooks/useChildScope";

export type TeamPick = {
  id?: string | null;
  name?: string | null;
  shortName?: string | null;
  clubName?: string | null;
};

export const TeamAutocomplete = ({
  teams,
  valueId,
  onChange,
  label,
  placeholder,
  size = "medium",
  selectedLabel,
}: {
  teams: TeamPick[];
  valueId: string | null;
  onChange: (teamId: string | null, team: TeamPick | null) => void;
  label: string;
  placeholder?: string;
  size?: "small" | "medium";
  selectedLabel?: string;
}) => {
  const { t } = useTranslation();
  const [inputValue, setInputValue] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [remembered, setRemembered] = useState<TeamPick | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(inputValue.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [inputValue]);

  const { data } = useTeamCatalog(debouncedQuery, true, true);
  const { active: parentView, selectedTeamIds } = useChildScope();
  const loaded = data?.items ?? [];
  const items = parentView
    ? loaded.filter((team) => team.id != null && selectedTeamIds.has(team.id))
    : loaded;
  const named = teams.find((team) => team.id === valueId);
  const fallbackName = named?.name || selectedLabel || "";
  const selected: TeamPick | null = valueId
    ? items.find((team) => team.id === valueId)
      ?? (remembered?.id === valueId ? remembered : null)
      ?? (fallbackName ? { id: valueId, name: fallbackName, shortName: named?.shortName, clubName: named?.clubName } : null)
    : null;
  const options = selected && !items.some((team) => team.id === selected.id) ? [selected, ...items] : items;
  const query = inputValue.trim();

  const selectedName = selected?.name ?? "";
  const selectedId = selected?.id ?? "";
  useEffect(() => {
    setInputValue(selectedName);
  }, [selectedId, selectedName]);

  return (
    <Autocomplete
      options={options}
      value={selected}
      inputValue={inputValue}
      onInputChange={(_, next) => setInputValue(next)}
      onChange={(_, team) => {
        setRemembered(team);
        onChange(team?.id ?? null, team);
      }}
      getOptionLabel={(team) => team.name ?? ""}
      isOptionEqualToValue={(left, right) => left.id === right.id}
      filterOptions={(next) => next}
      noOptionsText={query === "" ? t("teams.search-hint") : t("teams.search-empty")}
      renderOption={(props, team) => {
        const { key, ...rest } = props;
        return (
          <li key={key} {...rest}>
            {team.name}
            {team.clubName ? ` · ${team.clubName}` : ""}
          </li>
        );
      }}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={placeholder}
          size={size}
          helperText={query && data?.partial ? t("teams.search-more") : undefined}
        />
      )}
      fullWidth
      clearOnEscape
    />
  );
};
