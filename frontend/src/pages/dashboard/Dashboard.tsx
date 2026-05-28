import {
  Box,
  Card,
  CardContent,
  Chip,
  Divider,
  Skeleton,
  Stack,
  Typography,
  useTheme,
} from "@mui/material";
import SportsSoccerIcon from "@mui/icons-material/SportsSoccer";
import GroupsIcon from "@mui/icons-material/Groups";
import EmojiEventsIcon from "@mui/icons-material/EmojiEvents";
import CalendarTodayIcon from "@mui/icons-material/CalendarToday";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { useGetAllMatches } from "../../api/generated/match-controller/match-controller";
import { useGetMyTeams } from "../../api/generated/teams/teams";
import { useGetAllCups } from "../../api/generated/cups/cups";
import { useAuth } from "../../context/AuthContext";
import { ROLES } from "../../types/roles";
import { type MatchResponse } from "../../api/generated/model/matchResponse";
import { type TeamResponse } from "../../api/generated/model";
import { type CupResponse } from "../../api/generated/model";
import { uploadStatus } from "../../constants/uploadStatus";
import { APP_COLORS } from "../../constants/colors";

const getMatchStatusInfo = (match: MatchResponse): { color: string; textKey: string } => {
  const overall = match.overallStatus;
  if (overall === "PREPROCESSING") return { color: "#a1a1aa", textKey: "matches.status.preprocessing" };
  if (overall === "AWAITING_CORNERS") return { color: "#f59e0b", textKey: "matches.status.awaiting-corners" };
  if (match.encodingStatus === uploadStatus.enodingFailed) return { color: "#ef4444", textKey: "matches.status.encoding-failed" };
  if (match.encodingStatus === uploadStatus.encodingPending) return { color: "#a1a1aa", textKey: "matches.status.uploading" };
  if (match.encodingStatus === uploadStatus.encodingComplete && match.mlStatus === uploadStatus.mlPending)
    return { color: "#a1a1aa", textKey: "matches.status.analyzing" };
  if (match.mlStatus === uploadStatus.mlFailed) return { color: "#ef4444", textKey: "matches.status.analysis-failed" };
  if (match.mlStatus === uploadStatus.mlComplete) return { color: "#22c55e", textKey: "matches.status.ready" };
  return { color: "#a1a1aa", textKey: "matches.status.unknown" };
};

interface StatCardProps {
  icon: React.ReactNode;
  count: number | undefined;
  label: string;
  isLoading: boolean;
}

const StatCard = ({ icon, count, label, isLoading }: StatCardProps) => {
  const theme = useTheme();
  return (
    <Card
      elevation={0}
      sx={{
        flex: 1,
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: theme.shape.borderRadius,
      }}
    >
      <CardContent>
        <Stack direction="row" spacing={2} alignItems="center">
          <Box
            sx={{
              p: 1.5,
              borderRadius: 2,
              bgcolor: APP_COLORS.sideBarButton.activeBackGround,
              color: APP_COLORS.sideBarButton.active,
              display: "flex",
            }}
          >
            {icon}
          </Box>
          <Box>
            {isLoading ? (
              <Skeleton variant="text" width={40} height={36} />
            ) : (
              <Typography variant="h5" fontWeight={700} color="text.primary">
                {count ?? 0}
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
};

export function DashBoard() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const theme = useTheme();

  const isFan = user?.role === ROLES.FAN;

  const { data: matchesData, isLoading: isLoadingMatches } = useGetAllMatches();
  const { data: teamsData, isLoading: isLoadingTeams } = useGetMyTeams();
  const { data: cupsData, isLoading: isLoadingCups } = useGetAllCups();

  const matches = (matchesData as unknown as MatchResponse[]) ?? [];
  const teams = (teamsData as unknown as TeamResponse[]) ?? [];
  const cups = (cupsData as unknown as CupResponse[]) ?? [];

  const recentMatches = [...matches]
    .sort((a, b) => {
      const dateA = a.matchDate ? new Date(a.matchDate).getTime() : 0;
      const dateB = b.matchDate ? new Date(b.matchDate).getTime() : 0;
      return dateB - dateA;
    })
    .slice(0, 3);

  return (
    <Box sx={{ p: 3, maxWidth: 900 }}>
      {/* Welcome */}
      <Stack direction="row" spacing={2} alignItems="center" mb={4}>
        <Typography variant="h4" fontWeight={700} color="text.primary">
          {t("dashboard.welcome", { name: user?.name ?? "" })}
        </Typography>
        {user?.role && (
          <Chip
            label={t(`roles.${user.role}`)}
            size="small"
            sx={{
              bgcolor: APP_COLORS.sideBarButton.activeBackGround,
              color: APP_COLORS.sideBarButton.active,
              fontWeight: 600,
            }}
          />
        )}
      </Stack>

      {/* Stats */}
      <Typography variant="overline" color="text.secondary" fontWeight={600} letterSpacing={1.2}>
        {t("dashboard.overview")}
      </Typography>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} mt={1} mb={4}>
        <StatCard
          icon={<SportsSoccerIcon />}
          count={matches.length}
          label={t("dashboard.matches")}
          isLoading={isLoadingMatches}
        />
        {!isFan && (
          <StatCard
            icon={<GroupsIcon />}
            count={teams.length}
            label={t("dashboard.teams")}
            isLoading={isLoadingTeams}
          />
        )}
        <StatCard
          icon={<EmojiEventsIcon />}
          count={cups.length}
          label={t("dashboard.cups")}
          isLoading={isLoadingCups}
        />
      </Stack>

      {/* Recent Matches */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" mb={1}>
        <Typography variant="overline" color="text.secondary" fontWeight={600} letterSpacing={1.2}>
          {t("dashboard.recent-matches")}
        </Typography>
        <Chip
          label={t("dashboard.view-all")}
          size="small"
          icon={<ArrowForwardIcon sx={{ fontSize: "0.9rem !important", color: "inherit !important" }} />}
          onClick={() => navigate("/matches")}
          sx={{
            bgcolor: APP_COLORS.sideBarButton.activeBackGround,
            color: APP_COLORS.sideBarButton.active,
            fontWeight: 500,
            cursor: "pointer",
            "& .MuiChip-icon": { color: APP_COLORS.sideBarButton.active },
            "&:hover": { bgcolor: APP_COLORS.sideBarButton.activeHoverBackGround },
          }}
        />
      </Stack>

      <Card
        elevation={0}
        sx={{ border: `1px solid ${theme.palette.divider}`, borderRadius: theme.shape.borderRadius }}
      >
        {isLoadingMatches ? (
          [0, 1, 2].map((i) => (
            <Box key={i}>
              <Box sx={{ px: 2, py: 1.5 }}>
                <Skeleton variant="text" width="60%" height={28} />
              </Box>
              {i < 2 && <Divider />}
            </Box>
          ))
        ) : recentMatches.length === 0 ? (
          <Box sx={{ px: 2, py: 3, textAlign: "center" }}>
            <Typography variant="body2" color="text.secondary">
              {t("dashboard.no-recent-matches")}
            </Typography>
          </Box>
        ) : (
          recentMatches.map((match, i) => {
            const statusInfo = getMatchStatusInfo(match);
            return (
              <Box key={match.id}>
                <Stack
                  direction="row"
                  alignItems="center"
                  justifyContent="space-between"
                  sx={{ px: 2, py: 1.5 }}
                >
                  <Typography variant="body1" fontWeight={500} color="text.primary">
                    {match.homeTeamName}{" "}
                    <Typography component="span" variant="body2" color="text.secondary">
                      {t("common.vs")}
                    </Typography>{" "}
                    {match.awayTeamName}
                  </Typography>
                  <Stack direction="row" spacing={2} alignItems="center">
                    <Stack direction="row" spacing={0.5} alignItems="center">
                      <CalendarTodayIcon sx={{ fontSize: "0.85rem", color: "text.secondary" }} />
                      <Typography variant="body2" color="text.secondary">
                        {match.matchDate?.split("T")[0]}
                      </Typography>
                    </Stack>
                    <Chip
                      label={t(statusInfo.textKey)}
                      size="small"
                      sx={{
                        bgcolor: `${statusInfo.color}18`,
                        color: statusInfo.color,
                        fontWeight: 600,
                        fontSize: "0.7rem",
                        height: 22,
                      }}
                    />
                  </Stack>
                </Stack>
                {i < recentMatches.length - 1 && <Divider />}
              </Box>
            );
          })
        )}
      </Card>
    </Box>
  );
}
