import { useEffect, useState } from "react";
import { Alert, Box, Button, Card, CardActionArea, Chip, CircularProgress, LinearProgress, Stack, Tab, Tabs, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router-dom";
import type { TeamResponse } from "../../../api/generated/model";
import { formatHomeworkDate, homeworkListKey, listHomeworks, type CoachTab, type HomeworkSummary } from "../../../api/homework";
import { useRoleTeams, type TeamCatalogItem } from "../../../api/roleTeams";
import { ListPager } from "../../../components/ListPager";
import type { TeamPick } from "../../../components/TeamAutocomplete";
import { useAuth } from "../../../context/AuthContext";
import { useView } from "../../../context/ViewContext";
import { ROLES } from "../../../types/roles";
import { TravelTeamBar } from "../../travel/TravelTeamBar";

const TABS: CoachTab[] = ["active", "draft", "archived"];

const storageKey = (userId?: string) => `homework.team:${userId ?? ""}`;

/** The chosen team survives leaving the page. Only a convenience: without storage the first team is chosen. */
const readStoredTeam = (userId?: string): TeamCatalogItem | null => {
  try {
    const raw = sessionStorage.getItem(storageKey(userId));
    return raw ? (JSON.parse(raw) as TeamCatalogItem) : null;
  } catch {
    return null;
  }
};

const storeTeam = (userId: string | undefined, team: TeamCatalogItem) => {
  try {
    sessionStorage.setItem(storageKey(userId), JSON.stringify(team));
  } catch {
    // Private window or blocked storage: the page works without it.
  }
};

const HomeworkCard = ({ homework }: { homework: HomeworkSummary }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const done = homework.assignedCount > 0 ? (homework.submittedCount / homework.assignedCount) * 100 : 0;
  return (
    <Card variant="outlined">
      <CardActionArea
        onClick={() => navigate(homework.status === "DRAFT" ? `/homework/${homework.id}/edit` : `/homework/${homework.id}`)}
        sx={{ p: 2 }}
      >
        <Stack spacing={0.75}>
          <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
            <Typography sx={{ fontWeight: 700, flexGrow: 1, overflowWrap: "anywhere" }}>{homework.title}</Typography>
            {homework.toReviewCount > 0 && <Chip size="small" color="warning" label={t("homework.to-review", { count: homework.toReviewCount })} />}
          </Stack>
          <Typography variant="body2" sx={{ color: "text.secondary" }}>
            {homework.dueAt ? t("homework.due", { date: formatHomeworkDate(homework.dueAt) }) : t("homework.no-due")}
          </Typography>
          {homework.status !== "DRAFT" && (
            <>
              <LinearProgress variant="determinate" value={done} sx={{ borderRadius: 1, height: 6 }} />
              <Typography variant="caption" sx={{ color: "text.secondary" }}>
                {t("homework.progress", {
                  submitted: homework.submittedCount,
                  assigned: homework.assignedCount,
                  completed: homework.completedCount,
                  overdue: homework.overdueCount,
                })}
              </Typography>
            </>
          )}
        </Stack>
      </CardActionArea>
    </Card>
  );
};

export const HomeworkList = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { activeView } = useView();
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const { data: catalog, isLoading: loadingTeams } = useRoleTeams();
  const teams = catalog?.items ?? [];
  const needsSearch = activeView === ROLES.ADMIN || Boolean(catalog?.partial);
  const [stored] = useState(() => readStoredTeam(user?.id));
  const [teamId, setTeamId] = useState(searchParams.get("teamId") ?? stored?.id ?? "");
  const [picked, setPicked] = useState<TeamCatalogItem | null>(stored);
  const [tab, setTab] = useState<CoachTab>(() => {
    const requested = searchParams.get("tab") as CoachTab | null;
    return requested && TABS.includes(requested) ? requested : "active";
  });
  const [page, setPage] = useState(1);

  useEffect(() => {
    // While the list is still loading it is empty, which must not count as "the chosen team is gone".
    if (!catalog || (needsSearch && teams.length !== 1)) return;
    if (!teams.some((team) => team.id === teamId)) setTeamId(teams[0]?.id ?? "");
  }, [catalog, teams, teamId, needsSearch]);

  useEffect(() => {
    setPage(1);
  }, [teamId, tab]);

  const selectedTeam = teams.find((team) => team.id === teamId) ?? (picked?.id === teamId ? picked : null);
  useEffect(() => {
    if (selectedTeam?.id) storeTeam(user?.id, { id: selectedTeam.id, name: selectedTeam.name, clubName: selectedTeam.clubName });
  }, [user?.id, selectedTeam?.id, selectedTeam?.name, selectedTeam?.clubName]);

  const list = useQuery({
    queryKey: homeworkListKey(teamId, tab, page),
    queryFn: ({ signal }) => listHomeworks(teamId, tab, page, signal),
    enabled: Boolean(teamId),
  });

  const menuTeams: TeamResponse[] = teams.map((team) => ({
    id: team.id, name: team.name, shortName: team.shortName, logoUrl: team.logoUrl, clubId: team.clubId, clubName: team.clubName,
  }));

  if (loadingTeams) {
    return <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}><CircularProgress /></Box>;
  }

  return (
    <Box sx={{ py: { xs: 2, md: 2.5 }, px: { xs: 2, md: 3 } }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: "center", flexWrap: "wrap", mb: 1.5 }}>
        <Typography sx={{ fontSize: { xs: 24, md: 28 }, fontWeight: 800, flexGrow: 1 }}>{t("sidebar.homework")}</Typography>
        {teamId && (
          <Button variant="contained" onClick={() => navigate(`/homework/new?teamId=${teamId}`)}>{t("homework.new")}</Button>
        )}
      </Stack>
      {(teams.length > 1 || needsSearch) && (
        <Box sx={{ pt: 1.25, mb: 1, maxWidth: 360 }}>
          <TravelTeamBar
            teams={menuTeams}
            selectedTeamId={teamId}
            onSelect={(id: string, team?: TeamPick | null) => {
              setTeamId(id);
              setPicked(team?.id ? { id: team.id, name: team.name ?? undefined, clubName: team.clubName ?? undefined } : null);
            }}
            searchTeams={needsSearch && teams.length !== 1}
            selectedTeam={selectedTeam}
          />
        </Box>
      )}
      {!teamId && (
        <Typography sx={{ color: "text.secondary" }}>{t(teams.length === 0 && !needsSearch ? "homework.empty-teams" : "homework.pick-team")}</Typography>
      )}
      {teamId && (
        <>
          <Tabs value={tab} onChange={(_event, next: CoachTab) => setTab(next)} variant="scrollable" sx={{ mb: 2 }}>
            {TABS.map((value) => <Tab key={value} value={value} label={t(`homework.tabs.${value}`)} />)}
          </Tabs>
          {list.isError && <Alert severity="error">{t("homework.load-failed")}</Alert>}
          {list.isLoading && <CircularProgress size={28} />}
          <Stack spacing={1.25}>
            {(list.data?.items ?? []).map((homework) => <HomeworkCard key={homework.id} homework={homework} />)}
          </Stack>
          {list.data && list.data.items.length === 0 && <Typography sx={{ color: "text.secondary" }}>{t("homework.empty")}</Typography>}
          <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}>
            <ListPager page={page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />
          </Box>
        </>
      )}
    </Box>
  );
};
