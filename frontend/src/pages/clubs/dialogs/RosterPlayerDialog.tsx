import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import RemoveCircleOutlineIcon from "@mui/icons-material/RemoveCircleOutline";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import CheckIcon from "@mui/icons-material/Check";
import { AxiosError } from "axios";
import { useTranslation } from "react-i18next";
import {
  createChildLogin,
  createParentInvite,
  listProfileParents,
  unlinkProfileParent,
  type ChildResponse,
  type CreateChildLoginRequest,
  type ProfileParent,
} from "../../../api/children";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { formatPersonName } from "../../../utils/stringUtils";
import { CreateChildLoginDialog } from "../../children/CreateChildLoginDialog";
import { ParentInviteList, parentInvitesQueryKey } from "../../children/ParentInviteList";

const rowSx = {
  p: 1,
  borderRadius: 2,
  border: "1px solid",
  borderColor: "divider",
  bgcolor: "transparent",
} as const;

export const RosterPlayerDialog = ({
  open,
  profileId,
  firstName,
  lastName,
  hasLogin,
  onClose,
  onChanged,
}: {
  open: boolean;
  profileId: string | null;
  firstName?: string;
  lastName?: string;
  hasLogin: boolean;
  onClose: () => void;
  onChanged: () => Promise<void>;
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [parents, setParents] = useState<ProfileParent[]>([]);
  const [parentLink, setParentLink] = useState("");
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setParentLink("");
    setCopied(false);
    setError(null);
    setLoginOpen(false);
    setLoginError(null);
  }, [open, profileId, hasLogin]);

  useEffect(() => {
    if (!open || !profileId) {
      setParents([]);
      return;
    }
    let cancelled = false;
    listProfileParents(profileId)
      .then((rows) => {
        if (!cancelled) setParents(rows);
      })
      .catch(() => {
        if (!cancelled) setParents([]);
      });
    return () => {
      cancelled = true;
    };
  }, [open, profileId]);

  if (!profileId) return null;

  const child: ChildResponse = {
    profileId,
    firstName: firstName ?? "",
    lastName: lastName ?? "",
  };
  const lastParentLocked = !hasLogin && parents.length <= 1;

  const reloadParents = async () => {
    setParents(await listProfileParents(profileId));
  };

  const handleUnlink = async (parentId: string) => {
    if (lastParentLocked) {
      setError(t("clubs.editor.last-parent"));
      return;
    }
    setPending(true);
    try {
      await unlinkProfileParent(profileId, parentId);
      setError(null);
      await reloadParents();
      await onChanged();
    } catch (err) {
      const detail = (err as AxiosError<{ detail?: string }>).response?.data?.detail;
      setError(detail || t("clubs.error.generic"));
    } finally {
      setPending(false);
    }
  };

  const handleInvite = async () => {
    setPending(true);
    try {
      const response = await createParentInvite(profileId);
      if (!response.token) {
        setError(t("teams.invite-token-missing"));
        return;
      }
      setParentLink(`${window.location.origin}/registration?invitetoken=${response.token}`);
      setError(null);
      await queryClient.invalidateQueries({ queryKey: parentInvitesQueryKey(profileId) });
    } catch {
      setError(t("clubs.error.generic"));
    } finally {
      setPending(false);
    }
  };

  const handleCreateLogin = async (form: CreateChildLoginRequest) => {
    setPending(true);
    setLoginError(null);
    try {
      await createChildLogin(profileId, form);
      setLoginOpen(false);
      setError(null);
      await onChanged();
      onClose();
    } catch (err) {
      const detail = (err as AxiosError<{ detail?: string }>).response?.data?.detail;
      setLoginError(detail || t("children.child-login-failed"));
    } finally {
      setPending(false);
    }
  };

  return (
    <>
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" disableEnforceFocus={loginOpen}>
        <DialogTitle sx={{ pr: 6 }}>
          {formatPersonName(firstName, lastName)}
          <IconButton aria-label={t("clubs.editor.close")} onClick={onClose} sx={{ position: "absolute", right: 8, top: 8 }}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Stack spacing={1.5}>
            {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
            {!hasLogin && (
              <Typography variant="body2" color="text.secondary">{t("clubs.editor.child-login-hint")}</Typography>
            )}
            {parents.length === 0 ? (
              <Typography variant="body2" color="text.secondary">{t("clubs.userEditor.parent-empty")}</Typography>
            ) : parents.map((parent) => (
              <Stack key={parent.id} direction="row" spacing={1} alignItems="center" sx={rowSx}>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="body2" fontWeight={700} noWrap>{formatPersonName(parent.firstName, parent.lastName)}</Typography>
                  <Typography variant="caption" color="text.secondary" noWrap>{parent.email || t("clubs.users.no-email")}</Typography>
                </Box>
                <IconButton
                  aria-label={t("clubs.userEditor.parent-remove")}
                  disabled={pending || (lastParentLocked && parents.length <= 1)}
                  onClick={() => void handleUnlink(parent.id)}
                >
                  <RemoveCircleOutlineIcon fontSize="small" />
                </IconButton>
              </Stack>
            ))}
            {lastParentLocked && parents.length === 1 && (
              <Typography variant="caption" color="text.secondary">{t("clubs.editor.last-parent")}</Typography>
            )}
            <ParentInviteList profileId={profileId} enabled={open} />
            {parentLink && (
              <Stack direction="row" spacing={1} alignItems="center">
                <TextField value={parentLink} size="small" fullWidth slotProps={{ input: { readOnly: true } }} />
                <SecondaryButton
                  onClick={() => { void navigator.clipboard.writeText(parentLink); setCopied(true); }}
                  startIcon={copied ? <CheckIcon /> : <ContentCopyIcon />}
                  sx={{ flexShrink: 0 }}
                >
                  {copied ? t("clubs.editor.copied") : t("clubs.editor.copy")}
                </SecondaryButton>
              </Stack>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2, justifyContent: "space-between" }}>
          <SecondaryButton disabled={pending} onClick={() => void handleInvite()}>
            {t("clubs.userEditor.parent-invite")}
          </SecondaryButton>
          <Stack direction="row" spacing={1}>
            {!hasLogin && (
              <PrimaryButton disabled={pending} onClick={() => setLoginOpen(true)}>
                {t("clubs.editor.child-login")}
              </PrimaryButton>
            )}
            <SecondaryButton onClick={onClose}>{t("clubs.editor.close")}</SecondaryButton>
          </Stack>
        </DialogActions>
      </Dialog>
      <CreateChildLoginDialog
        open={loginOpen}
        child={child}
        onClose={() => setLoginOpen(false)}
        onCreate={(form) => void handleCreateLogin(form)}
        submitting={pending}
        serverError={loginError}
        onClearError={() => setLoginError(null)}
      />
    </>
  );
};
