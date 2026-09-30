import { useEffect, useState } from "react";
import type { AxiosError } from "axios";
import { Alert, Container } from "@mui/material";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  useListClubs,
  useCreateClub,
  useUpdateClub,
  useDeleteClub,
  getListClubsQueryKey,
  getListClubTeamsQueryKey,
} from "../../api/generated/clubs/clubs";
import { getGetMyTeamsQueryKey } from "../../api/generated/teams/teams";
import { createClubAdminInvite, createTeamInClub } from "../../api/clubAdmins";
import {
  type ClubResponse,
  type CreateClubRequest,
  type CreateTeamRequest,
  type UpdateClubRequest,
} from "../../api/generated/model";
import { LoadingPage } from "../../components/LoadingPage";
import { useAuth } from "../../context/AuthContext";
import { useSnackbar } from "../../context/SnackbarContext";
import { ROLES } from "../../types/roles";
import { ClubPicker } from "./ClubPicker";
import { ClubWorkspace } from "./ClubWorkspace";
import { CreateClubDialog } from "./dialogs/CreateClubDialog";
import { EditClubDialog, type ClubUpdateBody } from "./dialogs/EditClubDialog";
import { CreateTeamDialog } from "../teams/DialogComps/CreateTeamDialog";
import { InviteCreatedDialog } from "../teams/DialogComps/InviteCreatedDialog";

type ClubView = ClubResponse & { clubAdmin?: boolean };

const problemDetail = (error: unknown): string | undefined => {
  const data = (error as AxiosError)?.response?.data;
  if (data && typeof data === "object" && "detail" in data) {
    const detail = (data as { detail?: unknown }).detail;
    return typeof detail === "string" && detail.trim() ? detail : undefined;
  }
  return undefined;
};

const asUpdateRequest = (data: ClubUpdateBody): UpdateClubRequest =>
  ({
    name: data.name,
    shortName: data.shortName,
    logoUrl: data.logoUrl,
  }) as UpdateClubRequest;

export const Clubs = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { showError } = useSnackbar();
  const queryClient = useQueryClient();
  const [openClubId, setOpenClubId] = useState<string | null>(null);
  const [pickerQuery, setPickerQuery] = useState("");
  const [usersQuery, setUsersQuery] = useState("");
  const [usersPage, setUsersPage] = useState(1);
  const [teamsQuery, setTeamsQuery] = useState("");
  const [teamsPage, setTeamsPage] = useState(1);
  const [adminsQuery, setAdminsQuery] = useState("");
  const [adminsPage, setAdminsPage] = useState(1);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [createTeamClubId, setCreateTeamClubId] = useState<string | null>(null);
  const [createTeamError, setCreateTeamError] = useState<string | null>(null);
  const [inviteUrl, setInviteUrl] = useState("");

  const { data: clubsData, isLoading, isError } = useListClubs();
  const clubs = (clubsData as ClubView[]) ?? [];

  const createClubMutation = useCreateClub();
  const updateClubMutation = useUpdateClub();
  const deleteClubMutation = useDeleteClub();

  const isPlatformAdmin = user?.role === ROLES.ADMIN;
  const canManageClub = (club: ClubView) =>
    isPlatformAdmin || (user?.role === ROLES.CLUB_ADMIN && Boolean(club.clubAdmin));

  const autoOpen = user?.role === ROLES.CLUB_ADMIN && clubs.length === 1;
  const openClub =
    clubs.find((club) => club.id === openClubId) ?? (autoOpen ? clubs[0] : undefined);
  const showBack = openClub != null && !autoOpen;
  const workspaceClubId = openClub?.id ?? null;

  useEffect(() => {
    setUsersQuery("");
    setUsersPage(1);
    setTeamsQuery("");
    setTeamsPage(1);
    setAdminsQuery("");
    setAdminsPage(1);
  }, [workspaceClubId]);

  const invalidateClubs = (clubId?: string) => {
    queryClient.invalidateQueries({ queryKey: getListClubsQueryKey() });
    queryClient.invalidateQueries({ queryKey: getGetMyTeamsQueryKey() });
    if (clubId) {
      queryClient.invalidateQueries({ queryKey: getListClubTeamsQueryKey(clubId) });
    }
  };

  const handleCreateClub = async (data: CreateClubRequest) => {
    try {
      await createClubMutation.mutateAsync({ data });
      invalidateClubs();
      setIsCreateOpen(false);
      setCreateError(null);
    } catch {
      setCreateError(t("clubs.error.generic"));
    }
  };

  const handleUpdateClub = async (id: string, data: ClubUpdateBody) => {
    try {
      await updateClubMutation.mutateAsync({ id, data: asUpdateRequest(data) });
      invalidateClubs(id);
    } catch {
      showError(t("clubs.error.generic"));
    }
  };

  const handleDeleteClub = async (id: string) => {
    try {
      await deleteClubMutation.mutateAsync({ id });
      setOpenClubId(null);
      setIsEditOpen(false);
      invalidateClubs();
    } catch (error) {
      const status = (error as AxiosError)?.response?.status;
      showError(
        status === 409
          ? problemDetail(error) ?? t("clubs.error.has-teams")
          : t("clubs.error.generic"),
      );
    }
  };

  const handleCreateTeam = async (data: CreateTeamRequest) => {
    if (!createTeamClubId) return;
    try {
      await createTeamInClub(createTeamClubId, {
        name: data.name,
        shortName: data.shortName,
        logoUrl: data.logoUrl,
      });
      invalidateClubs(createTeamClubId);
      setCreateTeamClubId(null);
      setCreateTeamError(null);
    } catch {
      setCreateTeamError(t("teams.error.generic"));
    }
  };

  const handleInviteAdmin = async (clubId: string) => {
    try {
      const response = await createClubAdminInvite(clubId);
      if (!response.token) {
        showError(t("teams.invite-token-missing"));
        return;
      }
      setInviteUrl(`${window.location.origin}/registration?invitetoken=${response.token}`);
      await queryClient.invalidateQueries({ queryKey: ["clubs", clubId, "admins"] });
    } catch {
      showError(t("clubs.error.generic"));
    }
  };

  const handleOpenClub = (clubId: string) => {
    setOpenClubId(clubId);
  };

  return (
    <>
      {isLoading ? (
        <Container maxWidth="lg" sx={{ py: 6 }}>
          <LoadingPage />
        </Container>
      ) : isError ? (
        <Container maxWidth="lg" sx={{ py: 6 }}>
          <Alert severity="error">{t("clubs.load-error")}</Alert>
        </Container>
      ) : openClub ? (
        <ClubWorkspace
          key={openClub.id}
          club={openClub}
          clubCount={clubs.length}
          isPlatformAdmin={isPlatformAdmin}
          showBack={showBack}
          canManage={canManageClub(openClub)}
          usersQuery={usersQuery}
          usersPage={usersPage}
          teamsQuery={teamsQuery}
          teamsPage={teamsPage}
          adminsQuery={adminsQuery}
          adminsPage={adminsPage}
          onUsersQueryChange={(value) => {
            setUsersQuery(value);
            setUsersPage(1);
          }}
          onUsersPageChange={setUsersPage}
          onTeamsQueryChange={(value) => {
            setTeamsQuery(value);
            setTeamsPage(1);
          }}
          onTeamsPageChange={setTeamsPage}
          onAdminsQueryChange={(value) => {
            setAdminsQuery(value);
            setAdminsPage(1);
          }}
          onAdminsPageChange={setAdminsPage}
          onBack={() => setOpenClubId(null)}
          onAddTeam={() => openClub.id && setCreateTeamClubId(openClub.id)}
          onInvite={() => openClub.id && handleInviteAdmin(openClub.id)}
          onLeftClub={() => {
            setOpenClubId(null);
            invalidateClubs();
          }}
          onEdit={() => setIsEditOpen(true)}
        />
      ) : (
        <ClubPicker
          clubs={clubs}
          isPlatformAdmin={isPlatformAdmin}
          query={pickerQuery}
          onQueryChange={setPickerQuery}
          onOpen={handleOpenClub}
          onCreate={() => setIsCreateOpen(true)}
        />
      )}

      <CreateClubDialog
        open={isCreateOpen}
        onClose={() => {
          setIsCreateOpen(false);
          setCreateError(null);
        }}
        onCreate={handleCreateClub}
        serverError={createError}
        onClearError={() => setCreateError(null)}
      />

      {openClub && (
        <EditClubDialog
          open={isEditOpen}
          onClose={() => setIsEditOpen(false)}
          onEdit={handleUpdateClub}
          club={openClub}
          onDelete={handleDeleteClub}
        />
      )}

      <CreateTeamDialog
        open={Boolean(createTeamClubId)}
        onClose={() => {
          setCreateTeamClubId(null);
          setCreateTeamError(null);
        }}
        onCreate={handleCreateTeam}
        serverError={createTeamError}
        onClearError={() => setCreateTeamError(null)}
        initialClubId={createTeamClubId ?? undefined}
        lockClub
      />

      <InviteCreatedDialog
        open={Boolean(inviteUrl)}
        inviteUrl={inviteUrl}
        onClose={() => setInviteUrl("")}
      />
    </>
  );
};
