import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Avatar,
  Box,
  Chip,
  Container,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import type { AxiosError } from "axios";
import { useTranslation } from "react-i18next";
import { createChildLogin, createParentInvite, formatChildTeam, type CreateChildLoginRequest } from "../../api/children";
import { ParentInviteList, parentInvitesQueryKey } from "./ParentInviteList";
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { InviteCreatedDialog } from "../teams/DialogComps/InviteCreatedDialog";
import { LoadingPage } from "../../components/LoadingPage";
import { MY_CHILDREN_QUERY_KEY, useChildren } from "../../context/ChildrenContext";
import { useSnackbar } from "../../context/SnackbarContext";
import { formatPersonName, getInitials } from "../../utils/stringUtils";
import { CreateChildLoginDialog } from "./CreateChildLoginDialog";

export const ChildProfilePage = () => {
  const { t } = useTranslation();
  const { profileId } = useParams<{ profileId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showSuccess, showError } = useSnackbar();
  const { childrenList, select, isLoading } = useChildren();
  const [loginOpen, setLoginOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [inviteUrl, setInviteUrl] = useState("");
  const [inviteOpen, setInviteOpen] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const child = childrenList.find((item) => item.profileId === profileId) ?? null;

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
  const teams = (child.teams ?? []).flatMap((team) => {
    const label = formatChildTeam(team);
    return label ? [{ id: team.teamId, label }] : [];
  });

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

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Stack spacing={4}>
        <Typography variant="h4" fontWeight="bold">
          {t("children.profile-title", { name: fullName })}
        </Typography>

        <Paper sx={{ p: 4, borderRadius: 3 }}>
          <Stack spacing={3}>
            <Box sx={{ display: "flex", justifyContent: "center" }}>
              <Avatar
                sx={{
                  width: 80,
                  height: 80,
                  bgcolor: "#0f766e",
                  fontSize: "2rem",
                }}
              >
                {getInitials(fullName)}
              </Avatar>
            </Box>

            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <Typography variant="body2" color="text.secondary">
                {t("profile.role")}:
              </Typography>
              <Chip
                label={t("children.role")}
                size="small"
                sx={{ bgcolor: "rgba(16,185,129,0.1)", color: "#10b981" }}
              />
            </Box>

            <TextField
              label={t("profile.first-name")}
              value={child.firstName}
              fullWidth
              slotProps={{ input: { readOnly: true } }}
            />
            <TextField
              label={t("profile.last-name")}
              value={child.lastName}
              fullWidth
              slotProps={{ input: { readOnly: true } }}
            />
            <Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {t("children.team")}
              </Typography>
              {teams.length === 0 ? (
                <Typography variant="body1">{t("children.no-team")}</Typography>
              ) : (
                <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
                  {teams.map((team) => (
                    <Chip key={team.id} label={team.label} />
                  ))}
                </Box>
              )}
            </Box>

            {child.linkedUserId && (
              <Alert severity="success">{t("children.child-login-ready")}</Alert>
            )}
            <ParentInviteList profileId={child.profileId} />
            <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1, flexWrap: "wrap" }}>
                <SecondaryButton onClick={handleInviteParent} disabled={inviting}>
                  {t("children.invite-parent")}
                </SecondaryButton>
                {!child.linkedUserId && (
                  <PrimaryButton onClick={() => setLoginOpen(true)}>
                    {t("children.create-profile")}
                  </PrimaryButton>
                )}
            </Box>
          </Stack>
        </Paper>
      </Stack>

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
    </Container>
  );
};
