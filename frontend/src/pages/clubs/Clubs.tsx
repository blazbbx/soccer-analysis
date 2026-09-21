import { useState } from "react";
import type { AxiosError } from "axios";
import { Box, Container, Typography, Stack, Alert } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
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
import { getGetMyTeamsQueryKey, useCreateTeam } from "../../api/generated/teams/teams";
import {
  type ClubResponse,
  type CreateClubRequest,
  type CreateTeamRequest,
  type UpdateClubRequest,
} from "../../api/generated/model";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import { LoadingPage } from "../../components/LoadingPage";
import { useAuth } from "../../context/AuthContext";
import { useSnackbar } from "../../context/SnackbarContext";
import { ROLES } from "../../types/roles";
import { ClubCard } from "./ClubCard";
import { CreateClubDialog } from "./dialogs/CreateClubDialog";
import { CreateTeamDialog } from "../teams/DialogComps/CreateTeamDialog";

const problemDetail = (error: unknown): string | undefined => {
  const data = (error as AxiosError)?.response?.data;
  if (data && typeof data === "object" && "detail" in data) {
    const detail = (data as { detail?: unknown }).detail;
    return typeof detail === "string" && detail.trim() ? detail : undefined;
  }
  return undefined;
};

export const Clubs = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { showError } = useSnackbar();
  const queryClient = useQueryClient();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [createTeamClubId, setCreateTeamClubId] = useState<string | null>(null);
  const [createTeamError, setCreateTeamError] = useState<string | null>(null);

  const { data: clubsData, isLoading, isError } = useListClubs();
  const clubs = (clubsData as ClubResponse[]) ?? [];

  const createClubMutation = useCreateClub();
  const updateClubMutation = useUpdateClub();
  const deleteClubMutation = useDeleteClub();
  const createTeamMutation = useCreateTeam();

  const canManage = user?.role === ROLES.COACH || user?.role === ROLES.ADMIN;

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

  const handleUpdateClub = async (id: string, data: UpdateClubRequest) => {
    try {
      await updateClubMutation.mutateAsync({ id, data });
      invalidateClubs(id);
    } catch {
      showError(t("clubs.error.generic"));
    }
  };

  const handleDeleteClub = async (id: string) => {
    try {
      await deleteClubMutation.mutateAsync({ id });
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
    try {
      await createTeamMutation.mutateAsync({
        data: { ...data, clubId: createTeamClubId ?? data.clubId },
      });
      invalidateClubs(createTeamClubId ?? undefined);
      setCreateTeamClubId(null);
      setCreateTeamError(null);
    } catch {
      setCreateTeamError(t("teams.error.generic"));
    }
  };

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 4 }}
      >
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
            {t("sidebar.clubs")}
          </Typography>
          <Typography variant="body1" sx={{ color: "text.secondary" }}>
            {isLoading
              ? t("common.loading")
              : `${clubs.length} ${t("clubs.clubs-available")}`}
          </Typography>
        </Box>
        {canManage && (
          <FilledActionButton startIcon={<AddIcon />} onClick={() => setIsCreateOpen(true)}>
            {t("clubs.create-club")}
          </FilledActionButton>
        )}
      </Stack>

      {isLoading ? (
        <LoadingPage />
      ) : isError ? (
        <Alert severity="error">{t("clubs.load-error")}</Alert>
      ) : (
        <Stack spacing={4}>
          {clubs.length === 0 ? (
            <Typography variant="body1" sx={{ color: "text.secondary" }}>
              {canManage ? t("clubs.no-clubs-message") : t("clubs.no-clubs-visible")}
            </Typography>
          ) : (
            clubs.map((club) => (
              <ClubCard
                key={club.id}
                club={club}
                canManage={canManage}
                onUpdateClub={handleUpdateClub}
                onDeleteClub={handleDeleteClub}
                onAddTeam={(clubId) => setCreateTeamClubId(clubId)}
              />
            ))
          )}
        </Stack>
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
    </Container>
  );
};
