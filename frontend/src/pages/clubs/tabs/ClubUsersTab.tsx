import { useState } from "react";
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Pagination,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useListClubTeams, getListClubTeamsQueryKey } from "../../../api/generated/clubs/clubs";
import { listClubUsers } from "../../../api/teamCandidates";
import { useAuth } from "../../../context/AuthContext";
import { formatPersonName } from "../../../utils/stringUtils";
import { birthOf, permissionForRole, placementsForUser } from "../clubUserModel";
import { matchesQuery, monogram, pageOf } from "../paging";
import { ClubUserDialog } from "../dialogs/ClubUserDialog";

const usersKey = (clubId: string) => ["clubs", clubId, "users"] as const;

export const ClubUsersTab = ({
  clubId,
  clubName,
  clubShortName,
  isPlatformAdmin,
  query,
  page,
  onQueryChange,
  onPageChange,
}: {
  clubId: string;
  clubName?: string;
  clubShortName?: string;
  isPlatformAdmin: boolean;
  query: string;
  page: number;
  onQueryChange: (value: string) => void;
  onPageChange: (page: number) => void;
}) => {
  const { t } = useTranslation();
  const { user: actor } = useAuth();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { data: users = [], isLoading, isError } = useQuery({
    queryKey: usersKey(clubId),
    queryFn: () => listClubUsers(clubId),
    enabled: Boolean(clubId),
  });
  const { data: teams = [] } = useListClubTeams(clubId);

  const filtered = users.filter((person) => {
    const placements = person.id ? placementsForUser(person.id, teams) : [];
    return matchesQuery(query, [
      formatPersonName(person.firstName, person.lastName),
      person.email,
      person.role,
      ...placements.map((row) => row.teamName),
    ]);
  });
  const paging = pageOf(filtered, page);
  const selected = users.find((person) => person.id === selectedId) ?? null;

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: usersKey(clubId) });
    await queryClient.invalidateQueries({ queryKey: getListClubTeamsQueryKey(clubId) });
  };

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1} justifyContent="space-between" alignItems={{ sm: "center" }}>
        <Typography variant="caption" fontWeight={700} color="text.secondary">
          {t("clubs.users.title", { count: filtered.length })}
          {paging.totalPages > 1 ? ` — ${paging.safePage} / ${paging.totalPages}` : ""}
        </Typography>
        <Typography variant="caption" color="text.secondary">{t("clubs.users.hint")}</Typography>
      </Stack>
      <TextField
        fullWidth
        size="small"
        label={t("clubs.search")}
        value={query}
        onChange={(event) => onQueryChange(event.target.value)}
      />
      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
          <CircularProgress size={28} />
        </Box>
      ) : isError ? (
        <Alert severity="error">{t("clubs.error.generic")}</Alert>
      ) : users.length === 0 ? (
        <Typography variant="body2" color="text.secondary">{t("clubs.users.empty")}</Typography>
      ) : filtered.length === 0 ? (
        <Typography variant="body2" color="text.secondary">{t("clubs.users.search-empty")}</Typography>
      ) : (
        <>
          <Box sx={{ display: { xs: "none", md: "grid" }, gridTemplateColumns: "1.8fr 1.6fr 1.1fr 2fr", gap: 1.5, px: 1.5 }}>
            <Typography variant="caption" fontWeight={700} color="text.secondary">{t("clubs.users.col.name")}</Typography>
            <Typography variant="caption" fontWeight={700} color="text.secondary">{t("clubs.users.col.email")}</Typography>
            <Typography variant="caption" fontWeight={700} color="text.secondary">{t("clubs.users.col.birth")}</Typography>
            <Typography variant="caption" fontWeight={700} color="text.secondary">{t("clubs.users.col.teams")}</Typography>
          </Box>
          <Stack spacing={0.5}>
            {paging.visible.map((person) => {
              const name = formatPersonName(person.firstName, person.lastName);
              const placements = person.id ? placementsForUser(person.id, teams) : [];
              const permission = permissionForRole(person.role);
              const birth = birthOf(placements);
              return (
                <Box
                  key={person.id}
                  role="button"
                  tabIndex={0}
                  onClick={() => person.id && setSelectedId(person.id)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      if (person.id) setSelectedId(person.id);
                    }
                  }}
                  sx={{
                    display: "grid",
                    gridTemplateColumns: { xs: "1fr", md: "1.8fr 1.6fr 1.1fr 2fr" },
                    gap: 0.5,
                    alignItems: "center",
                    px: 1.5,
                    py: 1.25,
                    borderRadius: 2,
                    cursor: "pointer",
                    "&:hover": { bgcolor: "action.hover" },
                  }}
                >
                  <Stack direction="row" spacing={1.5} alignItems="center" sx={{ minWidth: 0 }}>
                    <Box sx={{ width: 36, height: 36, borderRadius: 1.5, bgcolor: "action.selected", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 12, flexShrink: 0 }}>
                      {monogram(name, "")}
                    </Box>
                    <Stack direction="row" spacing={0.5} alignItems="center" sx={{ minWidth: 0 }}>
                      <Typography variant="body2" fontWeight={700} noWrap>{name}</Typography>
                      {permission === "ADMIN" && <Chip size="small" color="error" label={t("roles.admin")} />}
                      {permission === "CLUB_ADMIN" && (
                        <Chip size="small" color="warning" label={`${t("roles.club_admin")}${clubShortName ? ` (${clubShortName})` : ""}`} />
                      )}
                    </Stack>
                  </Stack>
                  <Typography variant="caption" color="text.secondary" noWrap>{person.email || t("clubs.users.no-email")}</Typography>
                  <Typography variant="caption" color="text.secondary" noWrap>{birth || t("clubs.users.no-birth")}</Typography>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                    {placements.length === 0 ? (
                      <Typography variant="caption" color="text.secondary" fontStyle="italic">{t("clubs.users.no-team")}</Typography>
                    ) : placements.map((row) => (
                      <Chip
                        key={`${row.hat}-${row.teamId}`}
                        size="small"
                        variant="outlined"
                        label={clubShortName ? `${row.teamName} (${clubShortName})` : row.teamName}
                      />
                    ))}
                  </Stack>
                </Box>
              );
            })}
          </Stack>
          {paging.totalPages > 1 && (
            <Pagination
              count={paging.totalPages}
              page={paging.safePage}
              onChange={(_event, next) => onPageChange(next)}
              siblingCount={2}
              boundaryCount={0}
              sx={{ alignSelf: "center" }}
            />
          )}
        </>
      )}
      <ClubUserDialog
        open={selected != null}
        user={selected}
        clubName={clubName}
        clubShortName={clubShortName}
        teams={teams}
        placements={selected?.id ? placementsForUser(selected.id, teams) : []}
        isPlatformAdmin={isPlatformAdmin}
        actorId={actor?.id}
        onClose={() => setSelectedId(null)}
        onChanged={refresh}
      />
    </Stack>
  );
};
