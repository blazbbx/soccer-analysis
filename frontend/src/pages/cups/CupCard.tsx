import { useState } from "react";
import {
  Box,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Typography,
  Chip,
  Stack,
  CircularProgress,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import AddIcon from "@mui/icons-material/Add";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  useGetStandings,
  useGetTeams,
  useAddTeamToCup,
  getGetTeamsQueryKey,
  getGetStandingsQueryKey,
} from "../../api/generated/cups/cups";
import { type CupResponse, type TeamResponse } from "../../api/generated/model";
import { CupStandingsTable } from "./CupStandingsTable";
import { CupMatchesList } from "./CupMatchesList";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { AddTeamDialog } from "./dialogs/AddTeamDialog";

interface CupCardProps {
  cup: CupResponse;
  myTeams: TeamResponse[];
}

export const CupCard = ({ cup, myTeams }: CupCardProps) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [isAddTeamOpen, setIsAddTeamOpen] = useState(false);

  const cupId = cup.id ?? "";
  const { data: standingsData, isLoading: isLoadingStandings } = useGetStandings(cupId);
  const { data: teamsData } = useGetTeams(cupId);
  const teams = teamsData ?? [];
  const standings = standingsData ?? [];

  const addTeamMutation = useAddTeamToCup();

  const handleAddTeam = async (name: string, realTeamId?: string) => {
    await addTeamMutation.mutateAsync({ cupId, data: { name, realTeamId } });
    queryClient.invalidateQueries({ queryKey: getGetTeamsQueryKey(cupId) });
    queryClient.invalidateQueries({ queryKey: getGetStandingsQueryKey(cupId) });
    setIsAddTeamOpen(false);
  };

  return (
    <Box
      sx={{
        backgroundColor: "background.paper",
        borderRadius: 3,
        p: 3,
        border: "1px solid",
        borderColor: "divider",
      }}
    >
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" sx={{ mb: 2 }}>
        <Box>
          <Typography variant="h6" fontWeight="bold">
            {cup.name}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
            <Chip
              label={`${teams.length} ${t("cups.teams")}`}
              size="small"
              variant="outlined"
            />
            {cup.createdAt && (
              <Chip
                label={new Date(cup.createdAt).toLocaleDateString()}
                size="small"
                variant="outlined"
                sx={{ color: "text.secondary" }}
              />
            )}
          </Stack>
        </Box>
        <SecondaryButton startIcon={<AddIcon />} size="small" onClick={() => setIsAddTeamOpen(true)}>
          {t("cups.add-team")}
        </SecondaryButton>
      </Stack>

      {/* Standings */}
      {isLoadingStandings ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 2 }}>
          <CircularProgress size={24} sx={{ color: "#10b981" }} />
        </Box>
      ) : standings.length === 0 ? (
        <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
          {t("cups.no-standings")}
        </Typography>
      ) : (
        <CupStandingsTable rows={standings} />
      )}

      {/* Matches accordion */}
      <Accordion
        disableGutters
        elevation={0}
        sx={{
          mt: 2,
          border: "1px solid",
          borderColor: "divider",
          borderRadius: "8px !important",
          "&:before": { display: "none" },
        }}
      >
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">{t("cups.matches")}</Typography>
        </AccordionSummary>
        <AccordionDetails sx={{ pt: 0 }}>
          <CupMatchesList cupId={cupId} teams={teams} />
        </AccordionDetails>
      </Accordion>

      <AddTeamDialog
        open={isAddTeamOpen}
        onClose={() => setIsAddTeamOpen(false)}
        onAdd={handleAddTeam}
        myTeams={myTeams}
      />
    </Box>
  );
};
