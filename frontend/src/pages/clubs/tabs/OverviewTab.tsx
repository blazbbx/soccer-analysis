import type { ReactNode } from "react";
import { Alert, Box, Card, CardContent, Skeleton, Stack, Typography } from "@mui/material";
import BusinessIcon from "@mui/icons-material/Business";
import GroupsIcon from "@mui/icons-material/Groups";
import { useTranslation } from "react-i18next";
import { useListClubTeams } from "../../../api/generated/clubs/clubs";

const KpiCard = ({
  icon,
  label,
  value,
  loading,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  loading?: boolean;
}) => (
  <Card elevation={0} sx={{ flex: 1, border: "1px solid", borderColor: "divider", borderRadius: 3 }}>
    <CardContent>
      <Stack direction="row" spacing={2} alignItems="center">
        <Box
          sx={{
            p: 1.5,
            borderRadius: 2,
            bgcolor: "action.hover",
            color: "text.primary",
            display: "flex",
          }}
        >
          {icon}
        </Box>
        <Box>
          {loading ? (
            <Skeleton variant="text" width={72} height={36} />
          ) : (
            <Typography variant="h5" fontWeight={700} color="text.primary">
              {value}
            </Typography>
          )}
          <Typography variant="body2" color="text.secondary">
            {label}
          </Typography>
        </Box>
      </Stack>
    </CardContent>
  </Card>
);

export const OverviewTab = ({ clubId, clubCount }: { clubId: string; clubCount: number }) => {
  const { t } = useTranslation();
  const { data: teams, isLoading, isError } = useListClubTeams(clubId);

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
        <KpiCard
          icon={<BusinessIcon />}
          label={t("clubs.kpi.clubs")}
          value={String(clubCount)}
        />
        <KpiCard
          icon={<GroupsIcon />}
          label={t("clubs.kpi.teams")}
          value={isError ? "—" : String(teams?.length ?? 0)}
          loading={isLoading}
        />
      </Stack>
      {isError && <Alert severity="error">{t("clubs.teams-load-error")}</Alert>}
    </Stack>
  );
};
