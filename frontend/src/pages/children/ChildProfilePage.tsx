import { useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import {
  Avatar,
  Box,
  Button,
  Chip,
  Paper,
  Popover,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import PersonAddAltOutlinedIcon from "@mui/icons-material/PersonAddAltOutlined";
import AddIcon from "@mui/icons-material/Add";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import PeopleAltOutlinedIcon from "@mui/icons-material/PeopleAltOutlined";
import DirectionsCarOutlinedIcon from "@mui/icons-material/DirectionsCarOutlined";
import FileUploadOutlinedIcon from "@mui/icons-material/FileUploadOutlined";
import PersonOutlineIcon from "@mui/icons-material/PersonOutline";
import type { AxiosError } from "axios";
import { useTranslation } from "react-i18next";
import { createChildLogin, createParentInvite, setRideOfferRight, type ChildTeam, type CreateChildLoginRequest } from "../../api/children";
import { ParentInviteList, parentInvitesQueryKey } from "./ParentInviteList";
import { InviteCreatedDialog } from "../teams/DialogComps/InviteCreatedDialog";
import { LoadingPage } from "../../components/LoadingPage";
import { MY_CHILDREN_QUERY_KEY, useChildren } from "../../context/ChildrenContext";
import { useAuth } from "../../context/AuthContext";
import { useSnackbar } from "../../context/SnackbarContext";
import { formatPersonName, getInitials } from "../../utils/stringUtils";
import { CreateChildLoginDialog } from "./CreateChildLoginDialog";
import { MedicalSection } from "../../components/medical/MedicalSection";
import { useMedicalUploadRight } from "../../components/medical/useMedicalUploadRight";
import { eqCard, twoColumnGridSx, useEqualColumns } from "../../hooks/useEqualColumns";

const GREEN = "#10b981";
/** At most this many team chips; with more teams one slot goes to "+N more". */
const TEAM_CHIPS = 3;

// Same card as the "Saját profil" page, so the child's page reads as its sibling.
const cardSx = {
  p: 2.5,
  borderRadius: 3,
  border: "1px solid",
  borderColor: "divider",
  bgcolor: "background.paper",
  boxSizing: "border-box",
} as const;

const Card = ({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) => (
  <Paper elevation={0} {...eqCard} sx={cardSx}>
    <Typography variant="body1" fontWeight={700}>{title}</Typography>
    {subtitle && (
      <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25 }}>{subtitle}</Typography>
    )}
    <Box sx={{ mt: 2 }}>{children}</Box>
  </Paper>
);

/** "U12" from the team name, otherwise the initials of the team (or the club). */
const crestCode = (team: ChildTeam) => {
  const name = team.teamName?.trim() || team.clubName?.trim() || "";
  const age = name.match(/U\d+/i);
  if (age) return age[0].toUpperCase();
  return getInitials(name).slice(0, 2).toUpperCase() || "–";
};

const TeamChip = ({ team }: { team: ChildTeam }) => (
  <Stack direction="row" spacing={1} alignItems="center"
    sx={{ pl: 0.5, pr: 1.5, py: 0.5, borderRadius: 2, border: "1px solid", borderColor: "divider", minWidth: 0, maxWidth: "100%" }}>
    <Box sx={{
      width: 30,
      height: 30,
      borderRadius: 1.5,
      flexShrink: 0,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      fontSize: "0.7rem",
      fontWeight: 800,
      color: GREEN,
      bgcolor: `${GREEN}1a`,
    }}>
      {crestCode(team)}
    </Box>
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="body2" fontWeight={700} noWrap>{team.teamName || team.clubName}</Typography>
      {team.clubName && team.teamName && team.clubName !== team.teamName && (
        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: "block", lineHeight: 1.2 }}>
          {team.clubName}
        </Typography>
      )}
    </Box>
  </Stack>
);

/** One line of the access card, in the style of the profile page's role rows: title, state, control. */
const AccessRow = ({
  icon,
  title,
  state,
  on = false,
  control,
}: {
  icon: ReactNode;
  title: string;
  state: string;
  on?: boolean;
  control?: ReactNode;
}) => (
  <Stack direction="row" alignItems="center" spacing={1.5}
    sx={{ px: 1.5, py: 0.75, minHeight: 52, boxSizing: "border-box", borderRadius: 2, border: "1px solid", borderColor: on ? `${GREEN}66` : "divider", bgcolor: on ? `${GREEN}0d` : undefined }}>
    <Box sx={{ display: "flex", color: "text.secondary" }}>{icon}</Box>
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Typography variant="body2" fontWeight={700} noWrap>{title}</Typography>
      <Typography variant="caption" noWrap sx={{ display: "block", color: on ? GREEN : "text.secondary" }}>{state}</Typography>
    </Box>
    {control}
  </Stack>
);

const smallButtonSx = { textTransform: "none", fontWeight: 700, borderRadius: 2, py: 0.25, flexShrink: 0, borderColor: "divider" } as const;

export const ChildProfilePage = () => {
  const { t } = useTranslation();
  const { profileId } = useParams<{ profileId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { showSuccess, showError } = useSnackbar();
  const { childrenList, select, isLoading } = useChildren();
  const [loginOpen, setLoginOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [inviteUrl, setInviteUrl] = useState("");
  const [inviteOpen, setInviteOpen] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [rideRightPending, setRideRightPending] = useState(false);
  const [moreTeamsAnchor, setMoreTeamsAnchor] = useState<HTMLElement | null>(null);
  const leftRef = useRef<HTMLDivElement>(null);
  const rightRef = useRef<HTMLDivElement>(null);

  const child = childrenList.find((item) => item.profileId === profileId) ?? null;
  const uploadRight = useMedicalUploadRight(child?.profileId ?? "");
  useEqualColumns(leftRef, rightRef, { enabled: Boolean(child) });

  useEffect(() => {
    if (isLoading) return;
    if (child) {
      select(child.profileId);
      return;
    }
    const next = childrenList[0];
    navigate(next ? `/children/${next.profileId}` : "/", { replace: true });
  }, [isLoading, child, childrenList, select, navigate]);

  if (isLoading || !child) {
    return <LoadingPage />;
  }

  const fullName = formatPersonName(child.firstName, child.lastName);
  const hasAccount = Boolean(child.linkedUserId);
  const teams = (child.teams ?? []).filter((team) => team.teamName?.trim() || team.clubName?.trim());
  const visibleTeams = teams.length > TEAM_CHIPS ? teams.slice(0, TEAM_CHIPS - 1) : teams;
  const hiddenTeams = teams.slice(visibleTeams.length);
  const me = formatPersonName(user?.firstName, user?.lastName);

  const handleInviteParent = async () => {
    setInviting(true);
    try {
      const response = await createParentInvite(child.profileId);
      if (!response.token) {
        showError(t("teams.invite-token-missing"));
        return;
      }
      setInviteUrl(`${window.location.origin}/registration?invitetoken=${response.token}`);
      setInviteOpen(true);
      await queryClient.invalidateQueries({ queryKey: parentInvitesQueryKey(child.profileId) });
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
      showError(detail || t("children.parent-invite-failed"));
    } finally {
      setInviting(false);
    }
  };

  const handleRideRight = async () => {
    setRideRightPending(true);
    try {
      await setRideOfferRight(child.profileId, !child.canOfferRides);
      await queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY });
      showSuccess(t("children.ride-offer-saved"));
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
      showError(detail || t("children.ride-offer-failed"));
    } finally {
      setRideRightPending(false);
    }
  };

  const handleCreate = async (form: CreateChildLoginRequest) => {
    setSubmitting(true);
    setServerError(null);
    try {
      await createChildLogin(child.profileId, form);
      await queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY });
      setLoginOpen(false);
      showSuccess(t("children.child-login-success"));
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
      setServerError(detail || t("children.child-login-failed"));
    } finally {
      setSubmitting(false);
    }
  };

  const personal = (
    <Card title={t("profile.personal.title")}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1.75} alignItems="center">
          <Avatar sx={{ width: 52, height: 52, bgcolor: "#0f766e", fontSize: "1.2rem", fontWeight: 700 }}>
            {getInitials(fullName)}
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body1" fontWeight={700} noWrap>{fullName}</Typography>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
              <Chip label={t("children.role")} size="small" sx={{ height: 20, bgcolor: `${GREEN}1a`, color: GREEN, fontWeight: 700 }} />
              <Stack direction="row" spacing={0.75} alignItems="center">
                <Box aria-hidden sx={{ width: 7, height: 7, borderRadius: "50%", bgcolor: hasAccount ? GREEN : "text.disabled" }} />
                <Typography variant="caption" color="text.secondary">
                  {hasAccount ? t("children.account.active") : t("children.account.none")}
                </Typography>
              </Stack>
            </Stack>
          </Box>
        </Stack>
        {/* The parent cannot rename the child (there is no endpoint for it), so the fields are read-only. */}
        <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" }, gap: 1.5 }}>
          <TextField size="small" label={t("profile.last-name")} value={child.lastName ?? ""} slotProps={{ input: { readOnly: true } }} />
          <TextField size="small" label={t("profile.first-name")} value={child.firstName ?? ""} slotProps={{ input: { readOnly: true } }} />
        </Box>
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="caption" color="text.secondary" fontWeight={700}>{t("children.teams-title")}</Typography>
            {teams.length > 0 && <Chip size="small" label={teams.length} sx={{ height: 18, fontSize: "0.7rem" }} />}
          </Stack>
          {teams.length === 0 ? (
            <Typography variant="body2" color="text.secondary">{t("children.no-team")}</Typography>
          ) : (
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
              {visibleTeams.map((team) => <TeamChip key={team.teamId} team={team} />)}
              {hiddenTeams.length > 0 && (
                <Button
                  size="small"
                  color="inherit"
                  onClick={(event) => setMoreTeamsAnchor(event.currentTarget)}
                  sx={{ ...smallButtonSx, py: 0.75, px: 1.5, border: "1px dashed", color: "text.secondary" }}
                >
                  {t("children.more-teams", { count: hiddenTeams.length })}
                </Button>
              )}
            </Box>
          )}
          <Popover
            open={Boolean(moreTeamsAnchor)}
            anchorEl={moreTeamsAnchor}
            onClose={() => setMoreTeamsAnchor(null)}
            anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
            slotProps={{ paper: { sx: { p: 1.5, borderRadius: 2 } } }}
          >
            <Stack spacing={1}>
              {hiddenTeams.map((team) => <TeamChip key={team.teamId} team={team} />)}
            </Stack>
          </Popover>
        </Box>
      </Stack>
    </Card>
  );

  const access = (
    <Card title={t("children.access.title")} subtitle={t("children.access.subtitle")}>
      <Stack spacing={1}>
        {/* TODO(backend): GET /api/player-profiles/{id}/parents only answers admins and club admins, so a
            parent cannot list the other parents of the child. Until it does, the row names only the
            signed-in parent, and the open invites follow it. */}
        <AccessRow
          icon={<PeopleAltOutlinedIcon sx={{ fontSize: 18 }} />}
          title={t("children.rights.parents.title")}
          state={me ? t("children.rights.parents.you", { name: me }) : t("children.rights.parents.you-only")}
          control={
            <Button size="small" variant="outlined" color="inherit" startIcon={<PersonAddAltOutlinedIcon />}
              onClick={() => void handleInviteParent()} disabled={inviting} sx={smallButtonSx}>
              {t("children.rights.parents.invite")}
            </Button>
          }
        />
        <ParentInviteList profileId={child.profileId} />
        <AccessRow
          icon={<PersonOutlineIcon sx={{ fontSize: 18 }} />}
          title={t("children.rights.account.title")}
          state={hasAccount ? t("children.account.active") : t("children.account.none")}
          on={hasAccount}
          control={!hasAccount ? (
            <Button size="small" variant="outlined" color="inherit" startIcon={<AddIcon />}
              onClick={() => setLoginOpen(true)} sx={smallButtonSx}>
              {t("children.rights.account.create")}
            </Button>
          ) : undefined}
        />
        <AccessRow
          icon={<DirectionsCarOutlinedIcon sx={{ fontSize: 18 }} />}
          title={t("children.rights.rides.title")}
          state={child.canOfferRides ? t("children.rights.rides.on") : t("children.rights.rides.off")}
          on={Boolean(child.canOfferRides)}
          control={
            <Switch
              size="small"
              color="success"
              checked={Boolean(child.canOfferRides)}
              disabled={rideRightPending}
              onChange={() => void handleRideRight()}
              slotProps={{ input: { "aria-label": t("children.rights.rides.title") } }}
            />
          }
        />
        {!child.medicalAdultManaged && <AccessRow
          icon={<FileUploadOutlinedIcon sx={{ fontSize: 18 }} />}
          title={t("medical.right.card-title")}
          state={child.canUploadMedical ? t("medical.right.on") : t("medical.right.off")}
          on={Boolean(child.canUploadMedical)}
          control={
            <Switch
              size="small"
              color="success"
              checked={Boolean(child.canUploadMedical)}
              disabled={uploadRight.busy}
              onChange={(event) => void uploadRight.setAllowed(event.target.checked)}
              slotProps={{ input: { "aria-label": t("medical.right.card-title") } }}
            />
          }
        />}
        {!hasAccount && (
          <Stack direction="row" spacing={0.75} alignItems="flex-start" sx={{ color: "warning.main", pt: 0.25 }}>
            <InfoOutlinedIcon sx={{ fontSize: 15, mt: "2px" }} />
            <Typography variant="caption" sx={{ lineHeight: 1.4 }}>{t("children.rights.no-account")}</Typography>
          </Stack>
        )}
      </Stack>
    </Card>
  );

  return (
    <Box sx={{ px: { xs: 2, md: 4 }, py: { xs: 3, md: 3 }, maxWidth: 1360, mx: "auto" }}>
      <Box sx={{ mb: 2.5 }}>
        <Typography variant="h5" fontWeight="bold">{t("children.profile-title", { name: fullName })}</Typography>
        <Typography variant="body2" color="text.secondary">{t("children.profile-subtitle")}</Typography>
      </Box>

      <Box sx={twoColumnGridSx}>
        <Stack ref={leftRef} spacing={2.5} sx={{ minWidth: 0 }}>
          {personal}
          {access}
        </Stack>
        <Stack ref={rightRef} spacing={2.5} sx={{ minWidth: 0 }}>
          {child.medicalAdultManaged ? (
            <Card title={t("medical.title")}>
              <Typography variant="body2" color="text.secondary">{t("medical.adult.parent-sentence")}</Typography>
            </Card>
          ) : (
            <MedicalSection
              profileId={child.profileId}
              viewer="manager"
              hasTeams={teams.length > 0}
              eqCard
            />
          )}
        </Stack>
      </Box>

      <InviteCreatedDialog
        open={inviteOpen}
        inviteUrl={inviteUrl}
        onClose={() => setInviteOpen(false)}
      />

      <CreateChildLoginDialog
        open={loginOpen}
        child={child}
        submitting={submitting}
        serverError={serverError}
        onClearError={() => setServerError(null)}
        onClose={() => {
          if (submitting) return;
          setLoginOpen(false);
          setServerError(null);
        }}
        onCreate={handleCreate}
      />
    </Box>
  );
};
