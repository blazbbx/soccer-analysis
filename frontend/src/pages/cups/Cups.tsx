import { useState } from "react";
import {
  Box,
  Container,
  Typography,
  CircularProgress,
  Stack,
  Chip,
  Alert,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import {
  useCreateCup,
} from "../../api/generated/cups/cups";
import { CUPS_PAGE_KEY, useListPage, usePagedCups, useRetreatEmptyPage } from "../../api/pagedLists";
import { LIST_PAGE_SIZE } from "../../api/paging";
import { useRoleTeams } from "../../api/roleTeams";
import { ListPager } from "../../components/ListPager";
import { TeamAutocomplete } from "../../components/TeamAutocomplete";
import { useView } from "../../context/ViewContext";
import { useChildScope } from "../../hooks/useChildScope";
import { ROLES } from "../../types/roles";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import { CupCard } from "./CupCard";
import { CreateCupDialog } from "./dialogs/CreateCupDialog";

export const Cups = () => {
  const { t } = useTranslation();
  const { activeView } = useView();
  const queryClient = useQueryClient();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [filterTeamId, setFilterTeamId] = useState<string | null>(null);
  const { active: parentView, selectedTeamIds, isLoading: childLoading, scopeTeams } = useChildScope();
  const { data: myTeamsData, isLoading: teamsLoading } = useRoleTeams();
  const myTeams = scopeTeams(myTeamsData?.items ?? []);
  const childTeamIds = parentView ? [...selectedTeamIds].filter((id): id is string => Boolean(id)).sort() : [];
  const soleTeamId = parentView
    ? (childTeamIds.length === 1 ? childTeamIds[0] : null)
    : (myTeams.length === 1 ? (myTeams[0]?.id ?? null) : null);
  const activeFilterId = soleTeamId ?? filterTeamId;
  const requestedTeamIds = activeFilterId ? [activeFilterId] : childTeamIds;
  const [page, setPage] = useListPage(`${activeView ?? ""}|${activeFilterId ?? ""}|${childTeamIds.join(",")}`);

  const { data: cupsData, isLoading: isLoadingCups, isError: isCupsError } = usePagedCups({
    page,
    size: LIST_PAGE_SIZE,
    teamIds: requestedTeamIds,
    enabled: !childLoading && !teamsLoading,
  });
  useRetreatEmptyPage(page, cupsData?.items.length, cupsData?.totalPages, setPage);

  const cups = cupsData?.items ?? [];

  const createCupMutation = useCreateCup();

  const handleCreate = async (name: string) => {
    await createCupMutation.mutateAsync({ data: { name } });
    queryClient.invalidateQueries({ queryKey: CUPS_PAGE_KEY });
    setIsCreateOpen(false);
  };

  const isAdmin = activeView === ROLES.ADMIN;
  const isCoachOrAdmin = activeView === ROLES.COACH || isAdmin;
  const showTeamSearch = isAdmin || Boolean(myTeamsData?.partial);

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 4 }}>
        <Box>
          <Typography
            variant="h1"
            sx={{ fontSize: "2rem", fontWeight: "bold", color: "text.primary", mb: 0.5 }}
          >
            {t("sidebar.cups")}
          </Typography>
          <Typography variant="body1" sx={{ color: "text.secondary" }}>
            {isLoadingCups || childLoading || teamsLoading ? t("common.loading") : `${cupsData?.total ?? 0} ${t("cups.cups-available")}`}
          </Typography>
        </Box>
        {isCoachOrAdmin && (
          <FilledActionButton startIcon={<AddIcon />} onClick={() => setIsCreateOpen(true)}>
            {t("cups.create-cup")}
          </FilledActionButton>
        )}
      </Stack>

      {/* Team filter chips. One team has no choice: the list is already that team's cups. */}
      {soleTeamId ? null : showTeamSearch && (myTeamsData?.total ?? 0) > 0 ? (
        <Box sx={{ mb: 3, maxWidth: 480 }}>
          <TeamAutocomplete
            teams={myTeams}
            valueId={filterTeamId}
            onChange={(teamId) => {
              setFilterTeamId(teamId);
              setPage(1);
            }}
            label={t("cups.search-team")}
            placeholder={t("cups.all-cups")}
            size="small"
          />
        </Box>
      ) : myTeams.length > 0 ? (
        <Stack direction="row" spacing={1} sx={{ mb: 3, flexWrap: "wrap", rowGap: 1 }}>
          <Chip
            label={t("cups.all-cups")}
            onClick={() => {
              setFilterTeamId(null);
              setPage(1);
            }}
            color={filterTeamId === null ? "primary" : "default"}
            variant={filterTeamId === null ? "filled" : "outlined"}
          />
          {myTeams.map((team) => (
            <Chip
              key={team.id}
              label={team.name}
              onClick={() => {
                setFilterTeamId(filterTeamId === team.id ? null : (team.id ?? null));
                setPage(1);
              }}
              color={filterTeamId === team.id ? "primary" : "default"}
              variant={filterTeamId === team.id ? "filled" : "outlined"}
            />
          ))}
        </Stack>
      ) : null}

      {/* Cup list */}
      {isLoadingCups || childLoading || teamsLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "40vh" }}>
          <CircularProgress sx={{ color: "#10b981" }} />
        </Box>
      ) : (
        <Stack spacing={4}>
          {isCupsError ? (
            <Alert severity="error">{t("cups.load-error")}</Alert>
          ) : cups.length === 0 ? (
            <Typography variant="body1" sx={{ color: "text.secondary" }}>
              {isCoachOrAdmin && !activeFilterId
                ? t("cups.no-cups-message")
                : t("cups.no-cups-visible")}
            </Typography>
          ) : (
            <>
              {cups.map((cup) => (
                <CupCard key={cup.id} cup={cup} myTeams={myTeams} />
              ))}
              <ListPager page={page} totalPages={cupsData?.totalPages ?? 0} onChange={setPage} />
            </>
          )}
        </Stack>
      )}

      <CreateCupDialog
        open={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreate={handleCreate}
      />
    </Container>
  );
};
