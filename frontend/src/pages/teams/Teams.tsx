import { useState } from "react";
import type { AxiosError } from "axios";
import {
  Box,
  Container,
  Typography,
  Stack,
} from "@mui/material";

import {
  useGetMyTeams,
  useCreateTeam,
  useUpdateTeam,
  useDeleteTeam,
  useRemovePlayer,
  getGetMyTeamsQueryKey,
} from "../../api/generated/teams/teams";
import { getListClubsQueryKey } from "../../api/generated/clubs/clubs";
import {
  type CreateTeamRequest,
  type TeamResponse,
  type UpdateTeamRequest,
  type CreateInviteRole,
} from "../../api/generated/model";
import { useQueryClient } from "@tanstack/react-query";

import { TeamCard } from "./TeamCard";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import AddIcon from "@mui/icons-material/Add";
import { useAuth } from "../../context/AuthContext";
import { useChildScope } from "../../hooks/useChildScope";
import { ROLES } from "../../types/roles";
import { useTranslation } from "react-i18next";
import { useSnackbar } from "../../context/SnackbarContext";
import { LoadingPage } from "../../components/LoadingPage";
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

  const { user } = useAuth();
  const { data: teamsData, isLoading: isLoadingTeams } = useGetMyTeams();
  const { scopeTeams } = useChildScope();
  const { t } = useTranslation();
  const { showError } = useSnackbar();

  const queryClient = useQueryClient();

  const teams = scopeTeams((teamsData as unknown as TeamResponse[]) || []);

  const createTeamMutation = useCreateTeam();
  const updateTeamMutation = useUpdateTeam();
  const deleteTeamMutation = useDeleteTeam();
  const removePlayerMutation = useRemovePlayer();
  const inviteMutation = useCreateInvite();

  const handleCreateSubmit = async (data: CreateTeamRequest) => {
    try {
      await createTeamMutation.mutateAsync({ data });
      queryClient.invalidateQueries({ queryKey: getGetMyTeamsQueryKey() });
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
      queryClient.invalidateQueries({ queryKey: getGetMyTeamsQueryKey() });
    } catch {
      showError(t('teams.error.generic'));
    }
  };

  const removeTeam = async (id: string) => {
    try {
      await deleteTeamMutation.mutateAsync({ id });
      queryClient.invalidateQueries({ queryKey: getGetMyTeamsQueryKey() });
    } catch {
      showError(t('teams.error.generic'));
    }
  };

  const handleRemovePlayer = async (teamId: string, playerId: string) => {
    try {
      await removePlayerMutation.mutateAsync({ teamId, playerId });
      queryClient.invalidateQueries({ queryKey: getGetMyTeamsQueryKey() });
    } catch {
      showError(t('teams.error.generic'));
    }
  };

  const handleCreateInvite = async (teamId: string, role: CreateInviteRole) => {
    try {
      const response = await inviteMutation.mutateAsync({
        teamId,
        params: { role },
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
    if (user?.role === ROLES.PLAYER || user?.role === ROLES.PARENT) {
      handleCreateInvite(teamId, "FAN");
    } else if (user?.role === ROLES.COACH || user?.role === ROLES.ADMIN) {
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

  const handleRoleSelected = async (role: "PLAYER" | "FAN" | "PARENT") => {
    setIsRoleSelectDialogOpen(false);
    if (role === "PARENT") {
      const teamId = pendingInviteTeamId;
      setPendingInviteTeamId(null);
      if (!teamId) return;
      try {
        const response = await createOpenParentInvite(teamId);
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
              : `${teams.length} ${t("teams.teams-available")}`}
          </Typography>
        </Box>

        {(user?.role === ROLES.COACH || user?.role === ROLES.ADMIN) && (
         
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

      {/* Feltételes renderelés: Ha töltünk, Spinner, ha nem, kártyák */}
      {isLoadingTeams ? (
        <LoadingPage />
      ) : (
        
        <Stack spacing={4}>
          {teams.length === 0 ? (
            <Typography variant="body1" sx={{ color: "text.secondary" }}>
              {t("teams.noteamsmessage")}
            </Typography>
          ) : (
            
            teams.map((team) => (
              <TeamCard
                key={team.id}
                team={team}
                showInviteAction={
                  user?.role === ROLES.COACH || user?.role === ROLES.ADMIN || user?.role === ROLES.PLAYER || user?.role === ROLES.PARENT
                }
                onUpdateTeam={editTeam}
                onDeleteTeam={removeTeam}
                onCreateInvite={handleInviteClick}
                onRemovePlayer={
                  user?.role === ROLES.COACH || user?.role === ROLES.ADMIN
                    ? (playerId) => handleRemovePlayer(team.id ?? "", playerId)
                    : undefined
                }
              />
            ))
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
        onClose={() => { setIsRoleSelectDialogOpen(false); setPendingInviteTeamId(null); }}
        onSelectRole={handleRoleSelected}
      />
    </Container>
  );
};
