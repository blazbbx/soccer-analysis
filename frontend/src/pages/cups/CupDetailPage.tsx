import { Link as RouterLink, useNavigate, useParams } from "react-router-dom";
import { Alert, Box, CircularProgress, Container, Link, Stack, Typography } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useTranslation } from "react-i18next";
import { useGetCup } from "../../api/generated/cups/cups";
import { useCupViewParams } from "../../api/cups";
import { apiErrorStatus } from "../../api/apiError";
import { useRoleTeams } from "../../api/roleTeams";
import { CupDetails } from "./CupDetails";
import { CupActions } from "./CupActions";

/** 2.4: one cup on its own page, so chips and links can point to it. */
export const CupDetailPage = () => {
  const { cupId = "" } = useParams<{ cupId: string }>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const viewParams = useCupViewParams();
  const { data: cup, isLoading, isError, error } = useGetCup(cupId, viewParams, {
    query: { enabled: cupId.length > 0, retry: false },
  });
  const { data: myTeamsData } = useRoleTeams(Boolean(cup?.canManage));

  const backLink = (
    <Link component={RouterLink} to="/cups" underline="hover" sx={{ display: "inline-flex", alignItems: "center", gap: 0.5, mb: 2 }}>
      <ArrowBackIcon fontSize="small" />
      {t("sidebar.cups")}
    </Link>
  );

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 10 }}>
        <CircularProgress sx={{ color: "#10b981" }} />
      </Box>
    );
  }

  if (isError || !cup?.id) {
    const status = apiErrorStatus(error);
    return (
      <Container maxWidth="lg" sx={{ py: 6 }}>
        {backLink}
        <Alert severity={status === 403 ? "warning" : "error"}>
          {status === 403 ? t("errors.forbidden") : t("cups.not-found")}
        </Alert>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      {backLink}
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" sx={{ mb: 3, gap: 2, flexWrap: "wrap" }}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h1" sx={{ fontSize: "2rem", fontWeight: "bold", color: "text.primary", mb: 0.5 }}>
            {cup.name}
          </Typography>
          <Typography variant="body2" sx={{ color: "text.secondary" }}>
            {`${cup.teams?.length ?? 0} ${t("cups.teams")} · ${cup.matches?.length ?? 0} ${t("cups.matches").toLowerCase()}`}
          </Typography>
        </Box>
        {cup.canManage && (
          <CupActions
            cup={{ id: cup.id, name: cup.name }}
            myTeams={myTeamsData?.items ?? []}
            withRenameAndDelete
            onDeleted={() => navigate("/cups")}
          />
        )}
      </Stack>
      <CupDetails cupId={cup.id} canManage={Boolean(cup.canManage)} matchesExpanded />
    </Container>
  );
};
