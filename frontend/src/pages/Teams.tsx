import { useState } from "react";
import {
  Box,
  Container,
  Typography,
  CircularProgress,
  Stack,
} from "@mui/material";

import {
  useGetAllTeams,
  useCreateTeam,
  useUpdateTeam,
  useDeleteTeam,
  getGetAllTeamsQueryKey,
} from "../api/generated/teams/teams";
import {
  type CreateInviteParams,
  type CreateTeamRequest,
  type TeamResponse,
  type UpdateTeamRequest,
} from "../api/generated/model";
import { useQueryClient } from "@tanstack/react-query";

import { TeamCard } from "../components/common/TeamPageComps/TeamCard";
import { FilledActionButton } from "../components/common/ui/FilledActionButton";
import AddIcon from "@mui/icons-material/Add";
import { useAuth } from "../context/AuthContext";
import { ROLES } from "../types/roles";
import { useTranslation } from "react-i18next";
import { CreateTeamDialog } from "../components/common/TeamPageComps/CreateTeamDialog";
import { useCreateInvite } from "../api/generated/team-invitations/team-invitations";
import { InviteCreatedDialog } from "../components/common/TeamPageComps/InviteCreatedDialog";

export const Teams = () => {
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isInviteDialogOpen, setIsInviteDialogOpen] = useState(false);

  const [inviteUrl, setInviteUrl] = useState<string>("");

  const { user } = useAuth();
  const { data: teamsData, isLoading: isLoadingTeams } = useGetAllTeams();
  const { t } = useTranslation();

  const queryClient = useQueryClient();

  const teams = (teamsData as unknown as TeamResponse[]) || [];

  const createTeamMutation = useCreateTeam();
  const updateTeamMutation = useUpdateTeam();
  const deleteTeamMutation = useDeleteTeam();
  const inviteMutation = useCreateInvite();

  const handleCreateSubmit = async (data: CreateTeamRequest) => {
    try {
      await createTeamMutation.mutateAsync({ data });
      queryClient.invalidateQueries({ queryKey: getGetAllTeamsQueryKey() });

      setIsCreateDialogOpen(false);
    } catch (error) {
      console.error("Hiba történt a csapat létrehozásakor:", error);
    }
  };

  const editTeam = async (id: string, data: UpdateTeamRequest) => {
    try {
      await updateTeamMutation.mutateAsync({ id, data });
      queryClient.invalidateQueries({ queryKey: getGetAllTeamsQueryKey() });
    } catch (error) {
      console.error("Hiba történt a csapat frissítésekor:", error);
    }
  };

  const removeTeam = async (id: string) => {
    try {
      await deleteTeamMutation.mutateAsync({ id });
      queryClient.invalidateQueries({ queryKey: getGetAllTeamsQueryKey() });
    } catch (error) {
      console.error("Hiba történt a csapat törlésekor:", error);
    }
  };

  const handleCreateInvite = async (teamId: string,params: CreateInviteParams) => {
    try {
      const response = await inviteMutation.mutateAsync({
        teamId,
        params,
      });
      console.log(response)

      
      const backendUrl = response.inviteLink || "";
      const inviteToken = backendUrl.split('/').pop(); 
      const frontendRegistrationUrl = `${window.location.origin}/registration?invitetoken=${inviteToken}`;

      setInviteUrl(frontendRegistrationUrl);
      setIsInviteDialogOpen(true);
    } catch (error) {
      console.error("Hiba a meghívó létrehozásakor", error);
    }
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
              ? "Loading..."
              : `${teams.length} ${t("teams.teams-available")}`}
          </Typography>
        </Box>

        {user?.role === ROLES.COACH && (
         
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
        {user?.role === ROLES.PLAYER && (
          <FilledActionButton startIcon={<AddIcon />}>
            {t("teams.join-team")}
          </FilledActionButton>
        )}
      </Stack>

      {/* Feltételes renderelés: Ha töltünk, Spinner, ha nem, kártyák */}
      {isLoadingTeams ? (
        <Box
          sx={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            minHeight: "40vh",
          }}
        >
          <CircularProgress sx={{ color: "#10b981" }} /> {/* Zöld pörgő */}
        </Box>
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
                  user?.role === ROLES.COACH || user?.role === ROLES.ADMIN
                }
                onUpdateTeam={editTeam}
                onDeleteTeam={removeTeam}
                onCreateInvite={handleCreateInvite}
              />
            ))
          )}
        </Stack>
      )}

      <CreateTeamDialog
        open={isCreateDialogOpen}
        onClose={() => setIsCreateDialogOpen(false)}
        onCreate={handleCreateSubmit}
      />

      <InviteCreatedDialog
        open={isInviteDialogOpen}
        onClose={() => setIsInviteDialogOpen(false)}
        inviteUrl={inviteUrl}
      />
    </Container>
  );
};
