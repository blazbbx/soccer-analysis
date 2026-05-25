import { useState } from "react";
import {
  Box,
  Container,
  Typography,
  CircularProgress,
  Stack,
  Chip,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import {
  useGetAllCups,
  useCreateCup,
  getGetAllCupsQueryKey,
} from "../../api/generated/cups/cups";
import { useGetMyTeams } from "../../api/generated/teams/teams";
import { type CupResponse, type TeamResponse } from "../../api/generated/model";
import { useAuth } from "../../context/AuthContext";
import { ROLES } from "../../types/roles";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import { CupCard } from "./CupCard";
import { CreateCupDialog } from "./dialogs/CreateCupDialog";

export const Cups = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [filterTeamId, setFilterTeamId] = useState<string | null>(null);

  const { data: cupsData, isLoading: isLoadingCups } = useGetAllCups();
  const { data: myTeamsData } = useGetMyTeams();

  const cups = (cupsData as unknown as CupResponse[]) ?? [];
  const myTeams = (myTeamsData as unknown as TeamResponse[]) ?? [];

  const createCupMutation = useCreateCup();

  const handleCreate = async (name: string) => {
    await createCupMutation.mutateAsync({ data: { name } });
    queryClient.invalidateQueries({ queryKey: getGetAllCupsQueryKey() });
    setIsCreateOpen(false);
  };

  const filteredCups = filterTeamId
    ? cups.filter((cup) => cup.teams?.some((team) => team.realTeam?.id === filterTeamId))
    : cups;

  const isCoachOrAdmin = user?.role === ROLES.COACH || user?.role === ROLES.ADMIN;

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
            {isLoadingCups ? t("common.loading") : `${cups.length} ${t("cups.cups-available")}`}
          </Typography>
        </Box>
        {isCoachOrAdmin && (
          <FilledActionButton startIcon={<AddIcon />} onClick={() => setIsCreateOpen(true)}>
            {t("cups.create-cup")}
          </FilledActionButton>
        )}
      </Stack>

      {/* Team filter chips */}
      {myTeams.length > 0 && (
        <Stack direction="row" spacing={1} sx={{ mb: 3, flexWrap: "wrap", rowGap: 1 }}>
          <Chip
            label={t("cups.all-cups")}
            onClick={() => setFilterTeamId(null)}
            color={filterTeamId === null ? "primary" : "default"}
            variant={filterTeamId === null ? "filled" : "outlined"}
          />
          {myTeams.map((team) => (
            <Chip
              key={team.id}
              label={team.name}
              onClick={() =>
                setFilterTeamId(filterTeamId === team.id ? null : (team.id ?? null))
              }
              color={filterTeamId === team.id ? "primary" : "default"}
              variant={filterTeamId === team.id ? "filled" : "outlined"}
            />
          ))}
        </Stack>
      )}

      {/* Cup list */}
      {isLoadingCups ? (
        <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "40vh" }}>
          <CircularProgress sx={{ color: "#10b981" }} />
        </Box>
      ) : (
        <Stack spacing={4}>
          {filteredCups.length === 0 ? (
            <Typography variant="body1" sx={{ color: "text.secondary" }}>
              {t("cups.no-cups-message")}
            </Typography>
          ) : (
            filteredCups.map((cup) => (
              <CupCard key={cup.id} cup={cup} myTeams={myTeams} />
            ))
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
