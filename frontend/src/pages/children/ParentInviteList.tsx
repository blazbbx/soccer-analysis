import { useState } from "react";
import { Box, IconButton, Stack, Typography } from "@mui/material";
import RemoveCircleOutlineIcon from "@mui/icons-material/RemoveCircleOutline";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { AxiosError } from "axios";
import { useTranslation } from "react-i18next";
import { listParentInvites, revokeParentInvite, type ParentInviteSummary } from "../../api/children";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { useSnackbar } from "../../context/SnackbarContext";
import { HoldToDeleteDialog } from "../clubs/dialogs/HoldToDeleteDialog";

export const parentInvitesQueryKey = (profileId: string) => ["parent-invites", profileId] as const;

const inviteUrl = (invite: ParentInviteSummary) =>
  `${window.location.origin}/registration?invitetoken=${invite.token}`;

export const ParentInviteList = ({
  profileId,
  enabled = true,
}: {
  profileId: string;
  enabled?: boolean;
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { showError } = useSnackbar();
  const { data } = useQuery({
    queryKey: parentInvitesQueryKey(profileId),
    queryFn: () => listParentInvites(profileId),
    enabled: enabled && Boolean(profileId),
  });
  const [revokeId, setRevokeId] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const invites = data ?? [];

  const revoke = async () => {
    if (!revokeId) return;
    setPending(true);
    try {
      await revokeParentInvite(profileId, revokeId);
      setRevokeId(null);
      await queryClient.invalidateQueries({ queryKey: parentInvitesQueryKey(profileId) });
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
      showError(detail || t("children.parent-invite-revoke-failed"));
    } finally {
      setPending(false);
    }
  };

  if (invites.length === 0) return null;

  return (
    <>
      <Stack spacing={1}>
        <Typography variant="caption" color="text.secondary">
          {t("children.parent-invites")}
        </Typography>
        {invites.map((invite) => {
          const url = inviteUrl(invite);
          const when = invite.expiresAt ? new Date(invite.expiresAt).toLocaleString() : "";
          return (
            <Stack key={invite.id} direction="row" spacing={1} alignItems="center">
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography variant="body2" noWrap>{url}</Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                  {t("children.parent-invite-meta", { used: invite.usedCount, max: invite.maxUses })}
                  {when ? ` · ${t("children.parent-invite-until", { when })}` : ""}
                  {invite.expired ? ` · ${t("children.parent-invite-expired")}` : ""}
                </Typography>
              </Box>
              <SecondaryButton
                onClick={() => {
                  void navigator.clipboard.writeText(url);
                  setCopiedId(invite.id);
                }}
                sx={{ flexShrink: 0 }}
              >
                {copiedId === invite.id ? t("invite.copied") : t("children.parent-invite-copy")}
              </SecondaryButton>
              <IconButton
                aria-label={t("children.parent-invite-revoke")}
                disabled={pending}
                onClick={() => setRevokeId(invite.id)}
              >
                <RemoveCircleOutlineIcon fontSize="small" />
              </IconButton>
            </Stack>
          );
        })}
      </Stack>
      <HoldToDeleteDialog
        open={revokeId != null}
        title={t("children.parent-invite-revoke")}
        itemName={t("children.parent-invite-link")}
        description={t("children.parent-invite-revoke-body")}
        pending={pending}
        onClose={() => {
          if (!pending) setRevokeId(null);
        }}
        onConfirm={() => { void revoke(); }}
      />
    </>
  );
};
