import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import GroupsIcon from "@mui/icons-material/Groups";
import ChildCareIcon from "@mui/icons-material/ChildCare";
import ShieldIcon from "@mui/icons-material/Shield";
import ApartmentIcon from "@mui/icons-material/Apartment";
import SearchIcon from "@mui/icons-material/Search";
import AddIcon from "@mui/icons-material/Add";
import RemoveCircleOutlineIcon from "@mui/icons-material/RemoveCircleOutline";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import CheckIcon from "@mui/icons-material/Check";
import SaveIcon from "@mui/icons-material/Save";
import PersonIcon from "@mui/icons-material/Person";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { AxiosError } from "axios";
import { useTranslation } from "react-i18next";
import type { TeamResponse } from "../../../api/generated/model";
import { addCoach, addFan, addPlayer, removeCoach, removeFan, removePlayer } from "../../../api/generated/teams/teams";
import { deleteUser } from "../../../api/generated/user-controller/user-controller";
import { createParentInvite, listProfileParents, removeChildLogin, unlinkProfileParent, type ProfileParent } from "../../../api/children";
import { removeParentFromTeam, updateClubUser, type TeamCandidate } from "../../../api/teamCandidates";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { formatPersonName } from "../../../utils/stringUtils";
import { matchesQuery, monogram } from "../paging";
import { hatForRole, permissionForRole, type TeamHat, type UserPlacement } from "../clubUserModel";
import { HoldToDeleteDialog } from "./HoldToDeleteDialog";

const HATS: TeamHat[] = ["COACH", "PARENT", "PLAYER", "FAN"];

type HoldTarget =
  | { kind: "user" }
  | { kind: "account" }
  | { kind: "member"; row: UserPlacement };

const roleKey = (hat: TeamHat) => {
  switch (hat) {
    case "COACH":
      return "roles.coach";
    case "PARENT":
      return "roles.parent";
    case "PLAYER":
      return "roles.player";
    case "FAN":
      return "roles.fan";
  }
};

const sectionSx = {
  p: 2,
  borderRadius: 2,
  border: "1px solid",
  borderColor: "divider",
  bgcolor: "action.hover",
} as const;

const rowSx = {
  p: 1,
  borderRadius: 2,
  border: "1px solid",
  borderColor: "divider",
  bgcolor: "transparent",
} as const;

export const ClubUserDialog = ({
  open,
  user,
  clubName,
  clubShortName,
  teams,
  placements,
  isPlatformAdmin,
  actorId,
  onClose,
  onChanged,
}: {
  open: boolean;
  user: TeamCandidate | null;
  clubName?: string;
  clubShortName?: string;
  teams: TeamResponse[];
  placements: UserPlacement[];
  isPlatformAdmin: boolean;
  actorId?: string;
  onClose: () => void;
  onChanged: () => Promise<void>;
}) => {
  const { t } = useTranslation();
  const [lastName, setLastName] = useState("");
  const [firstName, setFirstName] = useState("");
  const [birth, setBirth] = useState("");
  const [teamQuery, setTeamQuery] = useState("");
  const [parents, setParents] = useState<ProfileParent[]>([]);
  const [parentLink, setParentLink] = useState("");
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [pending, setPending] = useState(false);
  const [hold, setHold] = useState<HoldTarget | null>(null);

  useEffect(() => {
    if (!open || !user) return;
    setLastName(user.lastName ?? "");
    setFirstName(user.firstName ?? "");
    setBirth(placements.find((row) => row.dateOfBirth)?.dateOfBirth ?? "");
    setTeamQuery("");
    setParentLink("");
    setCopied(false);
    setError(null);
    setSaved(false);
    setHold(null);
  }, [open, user?.id]);

  const profileId = placements.find((row) => row.profileId)?.profileId;

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

  if (!user?.id) return null;
  const userId = user.id;
  const hat = hatForRole(user.role);
  const permission = permissionForRole(user.role);
  const joined = new Set(placements.map((row) => row.teamId));
  const joinable = teams.filter((team) => team.id && !joined.has(team.id));
  const visibleTeams = teamQuery.trim()
    ? joinable.filter((team) => matchesQuery(teamQuery, [team.name, team.shortName, clubName]))
    : [];

  const run = async (action: () => Promise<unknown>) => {
    setPending(true);
    try {
      await action();
      setError(null);
      await onChanged();
    } catch (err) {
      const detail = (err as AxiosError<{ detail?: string }>).response?.data?.detail;
      setError(detail || t("clubs.error.generic"));
    } finally {
      setPending(false);
    }
  };

  const addToTeam = (teamId: string) => {
    if (!hat) return;
    setTeamQuery("");
    if (hat === "PLAYER") void run(() => addPlayer(teamId, userId));
    else if (hat === "COACH") void run(() => addCoach(teamId, userId));
    else if (hat === "FAN") void run(() => addFan(teamId, userId));
  };

  const removeFromTeam = (row: UserPlacement) => {
    if (row.hat === "PLAYER" && row.profileId) void run(() => removePlayer(row.teamId, row.profileId as string));
    else if (row.hat === "COACH") void run(() => removeCoach(row.teamId, userId));
    else if (row.hat === "FAN") void run(() => removeFan(row.teamId, userId));
    else if (row.hat === "PARENT") void run(() => removeParentFromTeam(row.teamId, userId));
  };

  const handleSave = () => {
    if (!lastName.trim()) {
      setError(t("clubs.userEditor.last-name-required"));
      return;
    }
    const body: { firstName: string; lastName: string; dateOfBirth?: string } = {
      firstName: firstName.trim(),
      lastName: lastName.trim(),
    };
    if (hat === "PLAYER" && birth) body.dateOfBirth = birth;
    void run(async () => {
      await updateClubUser(userId, body);
      setSaved(true);
    });
  };

  const reloadParents = async () => {
    if (!profileId) return;
    setParents(await listProfileParents(profileId));
  };

  const handleParentInvite = async () => {
    if (!profileId) return;
    setPending(true);
    try {
      const response = await createParentInvite(profileId);
      if (!response.token) {
        setError(t("teams.invite-token-missing"));
        return;
      }
      setParentLink(`${window.location.origin}/registration?invitetoken=${response.token}`);
      setError(null);
    } catch {
      setError(t("clubs.error.generic"));
    } finally {
      setPending(false);
    }
  };

  const handleUnlinkParent = (parentId: string) => {
    if (!profileId) return;
    void run(async () => {
      await unlinkProfileParent(profileId, parentId);
      await reloadParents();
    });
  };

  const handleRemoveAccount = async () => {
    if (actorId === userId || !profileId || parents.length === 0) return;
    setPending(true);
    try {
      if (profileId) await removeChildLogin(profileId);
      else await deleteUser(userId);
      await onChanged();
      onClose();
    } catch (err) {
      const detail = (err as AxiosError<{ detail?: string }>).response?.data?.detail;
      setError(detail || t("clubs.error.generic"));
    } finally {
      setPending(false);
    }
  };

  const handleDelete = async () => {
    setPending(true);
    try {
      await deleteUser(userId);
      await onChanged();
      onClose();
    } catch (err) {
      const detail = (err as AxiosError<{ detail?: string }>).response?.data?.detail;
      setError(detail || t("clubs.error.generic"));
    } finally {
      setPending(false);
    }
  };

  const person = formatPersonName(firstName, lastName) || formatPersonName(user.firstName, user.lastName);
  const canDelete = isPlatformAdmin && actorId !== userId;

  return (
    <>
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" disableEnforceFocus={hold != null}>
      <DialogTitle sx={{ pr: 6 }}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Box sx={{ width: 40, height: 40, borderRadius: "50%", bgcolor: "success.main", color: "success.contrastText", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <PersonIcon fontSize="small" />
          </Box>
          <Box>
            <Typography variant="h6" fontWeight={700}>{t("clubs.userEditor.title")}</Typography>
            <Typography variant="caption" color="text.secondary">{person}</Typography>
          </Box>
        </Stack>
        <IconButton aria-label={t("clubs.editor.close")} onClick={onClose} sx={{ position: "absolute", right: 12, top: 12 }}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          {error && <Alert severity="warning">{error}</Alert>}

          <Box sx={sectionSx}>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
              <TextField required fullWidth size="small" label={t("clubs.userEditor.last-name")} value={lastName} onChange={(event) => setLastName(event.target.value)} />
              <TextField fullWidth size="small" label={t("clubs.userEditor.first-name")} value={firstName} onChange={(event) => setFirstName(event.target.value)} />
            </Stack>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} sx={{ mt: 1.5 }}>
              <TextField fullWidth size="small" label={t("clubs.userEditor.email")} value={user.email ?? ""} helperText={t("clubs.userEditor.email-locked")} disabled />
              <TextField
                fullWidth
                size="small"
                type="date"
                label={t("clubs.userEditor.birth")}
                value={birth}
                onChange={(event) => setBirth(event.target.value)}
                disabled={hat !== "PLAYER"}
                helperText={hat === "PLAYER" ? t("clubs.userEditor.birth-player") : t("clubs.userEditor.birth-other")}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Stack>
          </Box>

          <Box sx={sectionSx}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <Typography variant="caption" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                <GroupsIcon sx={{ fontSize: 16 }} />
                {t("clubs.userEditor.teams-title", { count: placements.length })}
              </Typography>
              <Typography variant="caption" color="text.secondary">{t("clubs.userEditor.teams-hint")}</Typography>
            </Stack>
            {placements.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{t("clubs.userEditor.no-teams")}</Typography>
            ) : (
              <Stack spacing={1} sx={{ mb: 1.5 }}>
                {placements.map((row) => (
                  <Stack key={`${row.hat}-${row.teamId}`} direction="row" spacing={1} alignItems="center" sx={rowSx}>
                    <Typography variant="body2" fontWeight={700} sx={{ flex: 1 }} noWrap>{row.teamName}</Typography>
                    <TextField select size="small" value={row.hat} onChange={() => setError(t("clubs.userEditor.role-fixed"))} sx={{ minWidth: 130 }}>
                      {HATS.map((option) => (
                        <MenuItem key={option} value={option}>{t(roleKey(option))}</MenuItem>
                      ))}
                    </TextField>
                    <IconButton
                      aria-label={t("clubs.userEditor.remove")}
                      disabled={pending || (row.hat === "PLAYER" && !row.profileId)}
                      onClick={() => setHold({ kind: "member", row })}
                    >
                      <RemoveCircleOutlineIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                ))}
              </Stack>
            )}
            {hat === "PLAYER" || hat === "COACH" || hat === "FAN" ? (
              joinable.length > 0 && (
                <Stack spacing={1}>
                  <TextField
                    size="small"
                    fullWidth
                    value={teamQuery}
                    onChange={(event) => setTeamQuery(event.target.value)}
                    placeholder={t("clubs.userEditor.team-search")}
                    slotProps={{ input: { startAdornment: <SearchIcon sx={{ mr: 1, color: "text.secondary" }} /> } }}
                  />
                  {teamQuery.trim() && (
                    visibleTeams.length === 0 ? (
                      <Typography variant="body2" color="text.secondary" textAlign="center">{t("clubs.userEditor.no-team-hit")}</Typography>
                    ) : visibleTeams.map((team) => (
                      <Stack key={team.id} direction="row" spacing={1} alignItems="center" sx={rowSx}>
                        <Box sx={{ width: 28, height: 28, borderRadius: 1, bgcolor: "action.selected", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 10, fontWeight: 700 }}>
                          {monogram(team.name, team.shortName)}
                        </Box>
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          <Typography variant="body2" fontWeight={700} noWrap>{team.name}</Typography>
                          <Typography variant="caption" color="text.secondary" noWrap>{clubName}</Typography>
                        </Box>
                        <PrimaryButton startIcon={<AddIcon />} disabled={pending || !team.id} onClick={() => team.id && addToTeam(team.id)} sx={{ py: 0.5, flexShrink: 0 }}>
                          {t("clubs.userEditor.add")}
                        </PrimaryButton>
                      </Stack>
                    ))
                  )}
                </Stack>
              )
            ) : (
              <Typography variant="caption" color="text.secondary">
                {hat === "PARENT" ? t("clubs.userEditor.parent-joins-by-invite") : t("clubs.userEditor.no-team-hat")}
              </Typography>
            )}
          </Box>

          {hat === "PLAYER" && (
            <Box sx={sectionSx}>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.5 }}>
                <Typography variant="caption" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <ChildCareIcon sx={{ fontSize: 16 }} />
                  {t("clubs.userEditor.parent-title")}
                </Typography>
                <Chip size="small" label={t("roles.player")} />
              </Stack>
              <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 1 }}>
                {profileId ? t("clubs.userEditor.parent-hint") : t("clubs.userEditor.parent-no-profile")}
              </Typography>
              {profileId && parents.length === 0 && (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{t("clubs.userEditor.parent-empty")}</Typography>
              )}
              {profileId && parents.length > 0 && (
                <Stack spacing={1} sx={{ mb: 1 }}>
                  {parents.map((parent) => (
                    <Stack key={parent.id} direction="row" spacing={1} alignItems="center" sx={rowSx}>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography variant="body2" fontWeight={700} noWrap>{formatPersonName(parent.firstName, parent.lastName)}</Typography>
                        <Typography variant="caption" color="text.secondary" noWrap>{parent.email || t("clubs.users.no-email")}</Typography>
                      </Box>
                      <IconButton aria-label={t("clubs.userEditor.parent-remove")} disabled={pending} onClick={() => handleUnlinkParent(parent.id)}>
                        <RemoveCircleOutlineIcon fontSize="small" />
                      </IconButton>
                    </Stack>
                  ))}
                </Stack>
              )}
              {profileId && (
                <SecondaryButton disabled={pending} onClick={handleParentInvite}>
                  {t("clubs.userEditor.parent-invite")}
                </SecondaryButton>
              )}
              {actorId !== userId && (
                <Box sx={{ mt: 1.5 }}>
                  <SecondaryButton
                    disabled={pending || !profileId || parents.length === 0}
                    onClick={() => setHold({ kind: "account" })}
                  >
                    {t("clubs.userEditor.remove-account")}
                  </SecondaryButton>
                  {parents.length === 0 && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.5 }}>
                      {t("clubs.userEditor.remove-account-needs-parent")}
                    </Typography>
                  )}
                </Box>
              )}
              {parentLink && (
                <Box sx={{ mt: 1.5, p: 1.25, borderRadius: 2, border: "1px solid", borderColor: "success.light", bgcolor: "rgba(16, 185, 129, 0.08)" }}>
                  <Typography variant="caption" fontWeight={700} color="success.main">
                    {t("clubs.userEditor.parent-link")}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 0.5 }}>
                    <TextField value={parentLink} size="small" fullWidth slotProps={{ input: { readOnly: true } }} />
                    <SecondaryButton
                      onClick={() => { void navigator.clipboard.writeText(parentLink); setCopied(true); }}
                      startIcon={copied ? <CheckIcon /> : <ContentCopyIcon />}
                      sx={{ flexShrink: 0, px: 1.5, py: 1, border: "1px solid", borderColor: "success.main" }}
                    >
                      {copied ? t("clubs.editor.copied") : t("clubs.editor.copy")}
                    </SecondaryButton>
                  </Stack>
                </Box>
              )}
            </Box>
          )}

          <Box sx={sectionSx}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.5 }}>
              <Typography variant="caption" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                <ShieldIcon sx={{ fontSize: 16 }} />
                {t("clubs.userEditor.permission-title")}
              </Typography>
              <Typography variant="caption" color="text.secondary">{t("clubs.userEditor.permission-scope")}</Typography>
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 1 }}>
              {t("clubs.userEditor.permission-hint")}
            </Typography>
            <TextField
              select
              fullWidth
              size="small"
              label={t("clubs.userEditor.permission-title")}
              value={permission}
              onChange={() => setError(t("clubs.userEditor.permission-fixed"))}
            >
              <MenuItem value="NORMAL">{t("clubs.userEditor.permission-normal")}</MenuItem>
              {isPlatformAdmin && <MenuItem value="ADMIN">{t("clubs.userEditor.permission-admin")}</MenuItem>}
              <MenuItem value="CLUB_ADMIN">{t("clubs.userEditor.permission-club")}</MenuItem>
            </TextField>
            {permission === "ADMIN" && (
              <Alert severity="success" sx={{ mt: 1 }}>{t("clubs.userEditor.admin-note")}</Alert>
            )}
            {permission === "CLUB_ADMIN" && (
              <Box sx={{ mt: 1.5, p: 1.5, borderRadius: 2, border: "1px solid", borderColor: "warning.main" }}>
                <Typography variant="body2" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.5, mb: 1 }}>
                  <ApartmentIcon fontSize="small" />
                  {t("clubs.userEditor.assigned-club")}
                </Typography>
                <Chip color="warning" label={clubShortName || clubName || t("clubs.userEditor.this-club")} />
              </Box>
            )}
          </Box>

          {canDelete && (
            <Box sx={{ ...sectionSx, borderColor: "error.light" }}>
              <Typography variant="caption" fontWeight={700} color="error" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                <DeleteOutlineIcon sx={{ fontSize: 16 }} />
                {t("clubs.editor.danger")}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 1 }}>
                {t("clubs.userEditor.delete-hint")}
              </Typography>
              <SecondaryButton disabled={pending} onClick={() => setHold({ kind: "user" })} sx={{ color: "error.main" }}>
                {t("clubs.userEditor.delete")}
              </SecondaryButton>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2, justifyContent: "space-between" }}>
        <Typography variant="body2" color="success.main" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
          {saved && (<><CheckIcon fontSize="small" /> {t("clubs.userEditor.saved")}</>)}
        </Typography>
        <Stack direction="row" spacing={1}>
          <SecondaryButton onClick={onClose}>{t("clubs.editor.cancel")}</SecondaryButton>
          <PrimaryButton startIcon={<SaveIcon />} disabled={pending} onClick={handleSave}>
            {t("clubs.editor.save")}
          </PrimaryButton>
        </Stack>
      </DialogActions>
    </Dialog>
    <HoldToDeleteDialog
      open={hold?.kind === "user"}
      title={t("clubs.userEditor.delete")}
      itemName={person}
      description={t("clubs.userEditor.delete-confirm")}
      pending={pending}
      showWarning={false}
      onClose={() => setHold(null)}
      onConfirm={() => { setHold(null); void handleDelete(); }}
    />
    <HoldToDeleteDialog
      open={hold?.kind === "account"}
      title={t("clubs.userEditor.remove-account")}
      itemName={person}
      description={t("clubs.userEditor.remove-account-confirm")}
      pending={pending}
      onClose={() => setHold(null)}
      onConfirm={() => { setHold(null); void handleRemoveAccount(); }}
    />
    <HoldToDeleteDialog
      open={hold?.kind === "member"}
      title={t("clubs.editor.remove-member-title")}
      itemName={hold?.kind === "member" ? hold.row.teamName : ""}
      description={t("clubs.editor.remove-member-body")}
      pending={pending}
      onClose={() => setHold(null)}
      onConfirm={() => {
        const row = hold?.kind === "member" ? hold.row : null;
        setHold(null);
        if (row) removeFromTeam(row);
      }}
    />
    </>
  );
};
