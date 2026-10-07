import { useEffect, useState } from "react";
import type { AxiosError } from "axios";
import {
  Box,
  Container,
  TextField,
  Typography,
  Stack,
} from "@mui/material";

import {
  useCreateTeam,
  useUpdateTeam,
  useDeleteTeam,
  useRemovePlayer,
} from "../../api/generated/teams/teams";
import { TEAM_CATALOG_KEY, useTeamCatalog } from "../../api/roleTeams";
import { useListPage, useRetreatEmptyPage } from "../../api/pagedLists";
import { LIST_PAGE_SIZE } from "../../api/paging";
import { getListClubsQueryKey } from "../../api/generated/clubs/clubs";
import {
  type CreateTeamRequest,
  type UpdateTeamRequest,
  type CreateInviteRole,
} from "../../api/generated/model";
import { useQueryClient } from "@tanstack/react-query";

import { TeamCard } from "./TeamCard";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import AddIcon from "@mui/icons-material/Add";
import { useView } from "../../context/ViewContext";
import { useChildScope } from "../../hooks/useChildScope";
import { ROLES } from "../../types/roles";
import { useTranslation } from "react-i18next";
import { useSnackbar } from "../../context/SnackbarContext";
import { LoadingPage } from "../../components/LoadingPage";
import { ListPager } from "../../components/ListPager";
import { CreateTeamDialog } from "./DialogComps/CreateTeamDialog";
import { useCreateInvite } from "../../api/generated/team-invitations/team-invitations";
import { InviteCreatedDialog } from "./DialogComps/InviteCreatedDialog";
import { InviteRoleSelectDialog } from "./DialogComps/InviteRoleSelectDialog";
import { createOpenParentInvite } from "../../api/children";

export const Teams = () => {
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isInviteDialogOpen, setIsInviteDialogOpen] = useState(false);
  const [isRoleSelectDialogOpen, setIsRoleSelectDialogOpen] = useState(false);
  const [pendingInviteTeamId, setPendingInviteTeamId] = useState<string | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);

  const [inviteUrl, setInviteUrl] = useState<string>("");

  const { activeView } = useView();
  const [teamQuery, setTeamQuery] = useState("");
  const [debouncedTeamQuery, setDebouncedTeamQuery] = useState("");
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedTeamQuery(teamQuery), 300);
    return () => window.clearTimeout(timer);
  }, [teamQuery]);
  const { scopeTeams, active: parentView, selectedTeamIds } = useChildScope();
  const browse = !parentView;
  const [page, setPage] = useListPage(browse ? debouncedTeamQuery.trim() : "summary");
  const { data: catalog, isLoading: isLoadingTeams } = useTeamCatalog(
    debouncedTeamQuery,
    true,
    true,
    browse ? page : undefined,
    browse ? LIST_PAGE_SIZE : undefined,
  );
  useRetreatEmptyPage(
    page,
    browse ? catalog?.items.length : undefined,
    browse ? catalog?.totalPages : undefined,
    setPage,
  );
  const { t } = useTranslation();
  const { showError } = useSnackbar();

  const queryClient = useQueryClient();

  const teams = scopeTeams(catalog?.items ?? []);
  const teamTotal = parentView ? selectedTeamIds.size : (catalog?.total ?? 0);
  const queryActive = debouncedTeamQuery.trim().length > 0;
  const soleTeamVisible = teamTotal === 1 && teams.length === 1 && !queryActive;
  const invalidateTeams = () => {
    queryClient.invalidateQueries({ queryKey: TEAM_CATALOG_KEY });
  };

  const createTeamMutation = useCreateTeam();
  const updateTeamMutation = useUpdateTeam();
  const deleteTeamMutation = useDeleteTeam();
  const removePlayerMutation = useRemovePlayer();
  const inviteMutation = useCreateInvite();

  const handleCreateSubmit = async (data: CreateTeamRequest) => {
    try {
      await createTeamMutation.mutateAsync({ data });
      invalidateTeams();
      queryClient.invalidateQueries({ queryKey: getListClubsQueryKey() });
      setIsCreateDialogOpen(false);
      setCreateError(null);
    } catch (error) {
      const status = (error as AxiosError)?.response?.status;
      setCreateError(
        status === 409 ? t('teams.error.name-taken') : t('teams.error.generic')
      );
    }
  };

  const editTeam = async (id: string, data: UpdateTeamRequest) => {
    try {
      await updateTeamMutation.mutateAsync({ id, data });
      invalidateTeams();
    } catch {
      showError(t('teams.error.generic'));
    }
  };

  const removeTeam = async (id: string) => {
    try {
      await deleteTeamMutation.mutateAsync({ id });
      invalidateTeams();
    } catch {
      showError(t('teams.error.generic'));
    }
  };

  const handleRemovePlayer = async (teamId: string, playerId: string) => {
    try {
      await removePlayerMutation.mutateAsync({ teamId, playerId });
      invalidateTeams();
      queryClient.invalidateQueries({ queryKey: [`/api/teams/${teamId}`] });
    } catch {
      showError(t('teams.error.generic'));
    }
  };

  const handleCreateInvite = async (teamId: string, role: CreateInviteRole) => {
    if (!activeView) return;
    try {
      const response = await inviteMutation.mutateAsync({
        teamId,
        params: { role, view: activeView.toUpperCase() },
      });

      const inviteToken = response.token;
      if (!inviteToken) {
        showError(t("teams.invite-token-missing"));
        return;
      }
      const frontendRegistrationUrl = `${window.location.origin}/registration?invitetoken=${inviteToken}`;

      setInviteUrl(frontendRegistrationUrl);
      setIsInviteDialogOpen(true);
    } catch {
      showError(t('teams.error.generic'));
    }
  };

  const handleInviteClick = (teamId: string) => {
    if (activeView === ROLES.PLAYER || activeView === ROLES.PARENT) {
      handleCreateInvite(teamId, "FAN");
    } else if (activeView === ROLES.COACH || activeView === ROLES.ADMIN) {
      setPendingInviteTeamId(teamId);
      setIsRoleSelectDialogOpen(true);
    } else {
      handleCreateInvite(teamId, "PLAYER");
    }
  };

  const showInviteLink = (token?: string) => {
    if (!token) {
      showError(t("teams.invite-token-missing"));
      return;
    }
    setInviteUrl(`${window.location.origin}/registration?invitetoken=${token}`);
    setIsInviteDialogOpen(true);
  };

  const handleRoleSelected = async (role: "PLAYER" | "FAN" | "PARENT" | "COACH") => {
    setIsRoleSelectDialogOpen(false);
    if (role === "PARENT") {
      const teamId = pendingInviteTeamId;
      setPendingInviteTeamId(null);
      if (!teamId || !activeView) return;
      try {
        const response = await createOpenParentInvite(teamId, activeView);
        showInviteLink(response.token);
      } catch {
        showError(t("teams.error.generic"));
      }
      return;
    }
    if (pendingInviteTeamId) {
      handleCreateInvite(pendingInviteTeamId, role);
    }
    setPendingInviteTeamId(null);
  };

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      {/* Oldal címe */}
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 4 }}
      >
        {/* Bal oldal: Cím és Számláló */}
        <Box>
          <Typography
            variant="h1"
            sx={{
              fontSize: "2rem",
              fontWeight: "bold",
              color: "text.primary",
              mb: 0.5,
            }}
          >
            {t("sidebar.teams")}
          </Typography>
          <Typography variant="body1" sx={{ color: "text.secondary" }}>
            {/* Dinamikusan kiírjuk a tömb hosszát */}
            {isLoadingTeams
              ? t("common.loading")
              : `${teamTotal} ${t("teams.teams-available")}`}
          </Typography>
        </Box>

        {activeView === ROLES.ADMIN && (
         
          <Stack direction="row" spacing={2}>
            {/* Create Team gomb (Contained, élénk zöld) */}
            <FilledActionButton
              startIcon={<AddIcon />}
              onClick={() => setIsCreateDialogOpen(true)}
            >
              {t("teams.create-team")}
            </FilledActionButton>
          </Stack>
        )}
      </Stack>

      {!isLoadingTeams && !soleTeamVisible && (
        <TextField
          value={teamQuery}
          onChange={(event) => setTeamQuery(event.target.value)}
          label={t("teams.search")}
          placeholder={t("teams.catalog-search")}
          size="small"
          fullWidth
          sx={{ mb: 3, maxWidth: 480 }}
        />
      )}

      {/* Feltételes renderelés: Ha töltünk, Spinner, ha nem, kártyák */}
      {isLoadingTeams ? (
        <LoadingPage />
      ) : (
        
        <Stack spacing={4}>
          {!browse && !queryActive && catalog?.partial ? (
            <Typography variant="body1" sx={{ color: "text.secondary" }}>
              {t("teams.search-hint")}
            </Typography>
          ) : teams.length === 0 ? (
            <Typography variant="body1" sx={{ color: "text.secondary" }}>
              {queryActive ? t("teams.search-empty") : t("teams.noteamsmessage")}
            </Typography>
          ) : (
            <>
              {teams.map((team) => (
                <TeamCard
                  key={team.id}
                  team={team}
                  startExpanded={teams.length === 1}
                  showInviteAction={
                    activeView === ROLES.COACH || activeView === ROLES.ADMIN || activeView === ROLES.PLAYER || activeView === ROLES.PARENT
                  }
                  onUpdateTeam={editTeam}
                  onDeleteTeam={removeTeam}
                  onCreateInvite={handleInviteClick}
                  onRemovePlayer={
                    activeView === ROLES.COACH || activeView === ROLES.ADMIN
                      ? (playerId) => handleRemovePlayer(team.id ?? "", playerId)
                      : undefined
                  }
                />
              ))}
              {browse && (
                <ListPager page={page} totalPages={catalog?.totalPages ?? 0} onChange={setPage} />
              )}
            </>
          )}
          {!browse && queryActive && catalog?.partial && (
            <Typography variant="body2" sx={{ color: "text.secondary" }}>
              {t("teams.search-more")}
            </Typography>
          )}
        </Stack>
      )}

      <CreateTeamDialog
        open={isCreateDialogOpen}
        onClose={() => { setIsCreateDialogOpen(false); setCreateError(null); }}
        onCreate={handleCreateSubmit}
        serverError={createError}
        onClearError={() => setCreateError(null)}
      />

      <InviteCreatedDialog
        open={isInviteDialogOpen}
        onClose={() => setIsInviteDialogOpen(false)}
        inviteUrl={inviteUrl}
      />

      <InviteRoleSelectDialog
        open={isRoleSelectDialogOpen}
        allowCoach={activeView === ROLES.ADMIN}
        onClose={() => { setIsRoleSelectDialogOpen(false); setPendingInviteTeamId(null); }}
        onSelectRole={handleRoleSelected}
      />
    </Container>
  );
};
