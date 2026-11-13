import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  CircularProgress,
  Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { useTranslation } from "react-i18next";
import { useGetStandings, useGetTeams } from "../../api/generated/cups/cups";
import { useCupViewParams } from "../../api/cups";
import { CupStandingsTable } from "./CupStandingsTable";
import { CupMatchesList } from "./CupMatchesList";

interface CupDetailsProps {
  cupId: string;
  canManage: boolean;
  /** The card loads the details only when it is opened. */
  enabled?: boolean;
  /** The detail page shows the matches open, the card folds them away. */
  matchesExpanded?: boolean;
}

/** Table and match list of one cup. Used by the cup card and the cup detail page. */
export const CupDetails = ({ cupId, canManage, enabled = true, matchesExpanded = false }: CupDetailsProps) => {
  const { t } = useTranslation();
  // 3.3: every detail request carries the active view.
  const viewParams = useCupViewParams();
  const queryEnabled = enabled && cupId.length > 0;
  const { data: standingsData, isLoading, isError } = useGetStandings(cupId, viewParams, {
    query: { enabled: queryEnabled },
  });
  const { data: teamsData } = useGetTeams(cupId, viewParams, { query: { enabled: queryEnabled } });
  const standings = standingsData ?? [];
  const teams = teamsData ?? [];

  return (
    <Box>
      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 2 }}>
          <CircularProgress size={24} sx={{ color: "#10b981" }} />
        </Box>
      ) : isError ? (
        <Alert severity="error" sx={{ mb: 2 }}>{t("cups.standings-error")}</Alert>
      ) : standings.length === 0 ? (
        <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>{t("cups.no-standings")}</Typography>
      ) : (
        <CupStandingsTable rows={standings} />
      )}

      <Accordion
        disableGutters
        elevation={0}
        defaultExpanded={matchesExpanded}
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
          <CupMatchesList cupId={cupId} teams={teams} canManage={canManage} />
        </AccordionDetails>
      </Accordion>
    </Box>
  );
};
