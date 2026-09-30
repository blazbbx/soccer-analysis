import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Dialog,
  DialogActions,
  Button,
  DialogContent,
  IconButton,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import ApartmentIcon from "@mui/icons-material/Apartment";
import GroupsIcon from "@mui/icons-material/Groups";
import MailOutlineIcon from "@mui/icons-material/MailOutline";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import CheckIcon from "@mui/icons-material/Check";
import SearchIcon from "@mui/icons-material/Search";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import SaveIcon from "@mui/icons-material/Save";
import type { AxiosError } from "axios";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { getListClubTeamsQueryKey } from "../../../api/generated/clubs/clubs";
import { type MemberInfo, type TeamResponse, type UpdateTeamRequest } from "../../../api/generated/model";
import { createInvite } from "../../../api/generated/team-invitations/team-invitations";
import { useDeleteTeam, useRemoveCoach, useRemovePlayer, useUpdateTeam } from "../../../api/generated/teams/teams";
import { createOpenParentInvite } from "../../../api/children";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { formatPersonName } from "../../../utils/stringUtils";
import { useAuth } from "../../../context/AuthContext";
import { ROLES } from "../../../types/roles";
import type { TeamCandidate } from "../../../api/teamCandidates";
import { matchesQuery, monogram } from "../paging";
import { placementsForUser } from "../clubUserModel";
import { HoldToDeleteDialog } from "./HoldToDeleteDialog";
import { RosterPlayerDialog } from "./RosterPlayerDialog";
import { ClubUserDialog } from "./ClubUserDialog";

type Hat = "COACH" | "PARENT" | "PLAYER" | "FAN";

const HATS: Hat[] = ["COACH", "PARENT", "PLAYER", "FAN"];

const roleLabelKey = (hat: Hat) => {
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

type MemberRow = {
  id: string;
  userId?: string;
  hat: Hat;
  name: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  dateOfBirth?: string;
};

type DeleteTarget =
  | { type: "team"; id: string; name: string }
  | { type: "member"; id: string; name: string; hat: Hat };

const toRow = (member: MemberInfo, hat: Hat, userIsId: boolean): MemberRow | null => {
  if (!member.id) return null;
  return {
    id: member.id,
    userId: userIsId ? member.id : member.linkedUserId,
    hat,
    name: formatPersonName(member.firstName, member.lastName),
    firstName: member.firstName,
    lastName: member.lastName,
    email: member.email,
    dateOfBirth: member.dateOfBirth,
  };
};

export const ClubTeamDialog = ({
  open,
  clubId,
  clubName,
  clubShortName,
  team,
  clubTeams,
  onClose,
}: {
  open: boolean;
  clubId: string;
  clubName?: string;
  clubShortName?: string;
  team: TeamResponse | null;
  clubTeams: TeamResponse[];
  onClose: () => void;
}) => {
  const { t } = useTranslation();
  const { user: actor } = useAuth();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState(0);
  const [name, setName] = useState("");
  const [shortName, setShortName] = useState("");
  const [logoUrl, setLogoUrl] = useState("");
  const [nameError, setNameError] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [inviteRole, setInviteRole] = useState<Hat>("PLAYER");
  const [inviteLink, setInviteLink] = useState("");
  const [copied, setCopied] = useState(false);
  const [memberQuery, setMemberQuery] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [rosterProfileId, setRosterProfileId] = useState<string | null>(null);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);

  const updateTeam = useUpdateTeam();
  const deleteTeam = useDeleteTeam();
  const removePlayer = useRemovePlayer();
  const removeCoach = useRemoveCoach();

  useEffect(() => {
    if (!open || !team) return;
    setTab(0);
    setName(team.name ?? "");
    setShortName((team.shortName ?? "").slice(0, 3).toUpperCase());
    setLogoUrl(team.logoUrl ?? "");
    setNameError(false);
    setError(null);
    setSaved(false);
    setInviteRole("PLAYER");
    setInviteLink("");
    setCopied(false);
    setMemberQuery("");
    setDeleteTarget(null);
    setRosterProfileId(null);
    setSelectedUserId(null);
  }, [open, team?.id]);

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: getListClubTeamsQueryKey(clubId) });
    await queryClient.invalidateQueries({ queryKey: ["clubs", clubId, "users"] });
  };

  const openMember = (member: MemberRow) => {
    if (member.userId) setSelectedUserId(member.userId);
    else setRosterProfileId(member.id);
  };

  const members = useMemo(() => {
    const players = (team?.players ?? []).map((member) => toRow(member, "PLAYER", false));
    const coaches = (team?.coaches ?? []).map((member) => toRow(member, "COACH", true));
    return [...players, ...coaches].filter((row): row is MemberRow => row != null);
  }, [team?.players, team?.coaches]);

  const visibleMembers = members.filter((member) =>
    matchesQuery(memberQuery, [member.name, member.email, t(roleLabelKey(member.hat))]),
  );
  const accountMember = members.find((member) => member.userId === selectedUserId);
  const accountUser: TeamCandidate | null = accountMember?.userId
    ? {
        id: accountMember.userId,
        email: accountMember.email,
        firstName: accountMember.firstName,
        lastName: accountMember.lastName,
        role: accountMember.hat,
      }
    : null;

  const handleSave = async () => {
    if (!team?.id) return;
    if (name.trim() === "") {
      setNameError(true);
      setTab(0);
      return;
    }
    const logo = logoUrl.trim();
    const monogramText = shortName.trim().slice(0, 3).toUpperCase();
    try {
      await updateTeam.mutateAsync({
        id: team.id,
        data: {
          name: name.trim(),
          shortName: monogramText || undefined,
          logoUrl: logo.length === 0 ? null : logo,
        } as UpdateTeamRequest,
      });
      setError(null);
      setSaved(true);
      await refresh();
      window.setTimeout(() => {
        setSaved(false);
        onClose();
      }, 400);
    } catch (err) {
      const status = (err as AxiosError)?.response?.status;
      setError(status === 409 ? t("teams.error.name-taken") : t("teams.error.generic"));
    }
  };

  const handleGenerate = async () => {
    if (!team?.id) return;
    try {
      const response = inviteRole === "PARENT"
        ? await createOpenParentInvite(team.id)
        : await createInvite(team.id, { role: inviteRole });
      if (!response.token) {
        setError(t("teams.invite-token-missing"));
        return;
      }
      setInviteLink(`${window.location.origin}/registration?invitetoken=${response.token}`);
      setCopied(false);
      setError(null);
    } catch {
      setError(t("teams.error.generic"));
    }
  };

  const handleCopy = async () => {
    if (!inviteLink) return;
    try {
      await navigator.clipboard.writeText(inviteLink);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!team?.id || !deleteTarget) return;
    try {
      if (deleteTarget.type === "team") {
        await deleteTeam.mutateAsync({ id: team.id });
        setDeleteTarget(null);
        await refresh();
        onClose();
        return;
      }
      if (deleteTarget.hat === "COACH") {
        await removeCoach.mutateAsync({ teamId: team.id, coachId: deleteTarget.id });
      } else {
        await removePlayer.mutateAsync({ teamId: team.id, playerId: deleteTarget.id });
      }
      setDeleteTarget(null);
      setError(null);
      await refresh();
    } catch {
      setDeleteTarget(null);
      setError(t("teams.error.generic"));
    }
  };

  const crest = (shortName || monogram(name, clubShortName) || "CS").slice(0, 3).toUpperCase();
  const clubMark = (clubShortName || monogram(clubName, "") || "KL").slice(0, 3).toUpperCase();
  const deleting = deleteTeam.isPending || removePlayer.isPending || removeCoach.isPending;

  return (
    <>
      <Dialog
        open={open && Boolean(team)}
        onClose={onClose}
        fullWidth
        maxWidth="md"
        disableEnforceFocus={selectedUserId != null || rosterProfileId != null || deleteTarget != null}
      >
        <Box sx={{ px: 3, pt: 2.5, pb: 2, display: "flex", alignItems: "center", gap: 2, borderBottom: 1, borderColor: "divider" }}>
          <Box
            sx={{
              width: 48,
              height: 48,
              borderRadius: 2,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontWeight: 800,
              color: "#04170e",
              background: "linear-gradient(135deg, #22c55e, #15803d)",
              flexShrink: 0,
            }}
          >
            {crest || "CS"}
          </Box>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography variant="h6" fontWeight={700} noWrap>
              {name || t("teams.edit-team")}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap>
              {t("clubs.editor.subtitle")}
            </Typography>
          </Box>
          <IconButton aria-label={t("clubs.editor.close")} onClick={onClose}>
            <CloseIcon />
          </IconButton>
        </Box>

        <Tabs value={tab} onChange={(_event, next: number) => setTab(next)} variant="scrollable" sx={{ px: 2, borderBottom: 1, borderColor: "divider" }}>
          <Tab icon={<ApartmentIcon />} iconPosition="start" label={t("clubs.editor.tab-basic")} />
          <Tab icon={<GroupsIcon />} iconPosition="start" label={t("clubs.editor.tab-members", { count: members.length })} />
        </Tabs>

        <DialogContent sx={{ height: { xs: "auto", sm: 470 }, maxHeight: { xs: "62vh", sm: 470 } }}>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          {tab === 0 && (
            <Stack spacing={2}>
              <TextField
                label={t("clubs.editor.name")}
                placeholder={t("clubs.editor.name-placeholder")}
                value={name}
                onChange={(event) => {
                  setName(event.target.value);
                  setNameError(false);
                }}
                error={nameError}
                helperText={nameError ? t("teams.name-error") : ""}
                fullWidth
              />
              <TextField
                label={t("clubs.editor.monogram")}
                value={shortName}
                onChange={(event) => setShortName(event.target.value.toUpperCase().slice(0, 3))}
                placeholder="EWF"
                slotProps={{ htmlInput: { maxLength: 3, style: { textAlign: "center", fontWeight: 700, letterSpacing: "0.2em" } } }}
                sx={{ maxWidth: { sm: 180 } }}
              />
              <TextField
                label={t("clubs.logo-url")}
                value={logoUrl}
                onChange={(event) => setLogoUrl(event.target.value)}
                slotProps={{ htmlInput: { maxLength: 500 } }}
                fullWidth
              />
              <Box sx={{ p: 1.5, borderRadius: 2, border: "1px solid", borderColor: "rgba(245, 158, 11, 0.45)", bgcolor: "rgba(245, 158, 11, 0.08)" }}>
                <Typography variant="caption" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.5, mb: 1, letterSpacing: "0.04em" }}>
                  <ApartmentIcon sx={{ fontSize: 16, color: "warning.main" }} />
                  {t("clubs.editor.club")}
                </Typography>
                <Stack direction="row" spacing={1.5} alignItems="center">
                  <Box sx={{ width: 28, height: 28, borderRadius: 1, bgcolor: "rgba(245, 158, 11, 0.2)", color: "warning.dark", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 12 }}>
                    {clubMark}
                  </Box>
                  <Typography fontWeight={700} noWrap sx={{ flex: 1 }}>
                    {clubName || t("clubs.editor.own-club")}
                  </Typography>
                  <CheckIcon sx={{ color: "success.main" }} />
                </Stack>
              </Box>
              <Box sx={{ p: 1.5, borderRadius: 2, border: "1px solid", borderColor: "divider" }}>
                <Typography variant="caption" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.5, mb: 1.5 }}>
                  <MailOutlineIcon sx={{ fontSize: 16, color: "success.main" }} />
                  {t("clubs.editor.invite-title")}
                </Typography>
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ sm: "flex-end" }}>
                  <TextField
                    select
                    fullWidth
                    label={t("clubs.editor.invite-role")}
                    value={inviteRole}
                    onChange={(event) => setInviteRole(event.target.value as Hat)}
                  >
                    {HATS.map((hat) => (
                      <MenuItem key={hat} value={hat}>{t(roleLabelKey(hat))}</MenuItem>
                    ))}
                  </TextField>
                  <PrimaryButton onClick={handleGenerate} startIcon={<MailOutlineIcon />} sx={{ flexShrink: 0 }}>
                    {t("clubs.editor.generate")}
                  </PrimaryButton>
                </Stack>
                {inviteLink && (
                  <Box sx={{ mt: 1.5, p: 1.25, borderRadius: 2, border: "1px solid", borderColor: "success.light", bgcolor: "rgba(16, 185, 129, 0.08)" }}>
                    <Typography variant="caption" fontWeight={700} color="success.main">
                      {t("clubs.editor.generated", { role: t(roleLabelKey(inviteRole)) })}
                    </Typography>
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 0.5 }}>
                      <TextField value={inviteLink} size="small" fullWidth slotProps={{ input: { readOnly: true } }} />
                      <SecondaryButton onClick={handleCopy} startIcon={copied ? <CheckIcon /> : <ContentCopyIcon />}>
                        {copied ? t("clubs.editor.copied") : t("clubs.editor.copy")}
                      </SecondaryButton>
                    </Stack>
                  </Box>
                )}
              </Box>
              <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ pt: 1, borderTop: "1px solid", borderColor: "rgba(239, 68, 68, 0.35)" }}>
                <Box>
                  <Typography variant="body2" fontWeight={700} color="error">{t("clubs.editor.danger")}</Typography>
                  <Typography variant="caption" color="text.secondary">{t("clubs.editor.danger-hint")}</Typography>
                </Box>
                <Button
                  color="error"
                  variant="outlined"
                  startIcon={<DeleteOutlineIcon />}
                  onClick={() => team?.id && setDeleteTarget({ type: "team", id: team.id, name: name || team.name || "" })}
                  sx={{ textTransform: "none", fontWeight: 700 }}
                >
                  {t("clubs.editor.delete-team")}
                </Button>
              </Stack>
            </Stack>
          )}

          {tab === 1 && (
            <Stack spacing={2}>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="caption" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <GroupsIcon sx={{ fontSize: 16 }} />
                  {t("clubs.editor.members-title", { count: members.length })}
                </Typography>
                <Typography variant="caption" color="text.secondary">{t("clubs.editor.member-hint")}</Typography>
              </Stack>
              <TextField
                size="small"
                fullWidth
                value={memberQuery}
                onChange={(event) => setMemberQuery(event.target.value)}
                placeholder={t("clubs.editor.member-search")}
                slotProps={{ input: { startAdornment: <SearchIcon sx={{ mr: 1, color: "text.secondary" }} /> } }}
              />
              {members.length === 0 ? (
                <Typography variant="body2" color="text.secondary" textAlign="center">{t("clubs.editor.no-members")}</Typography>
              ) : visibleMembers.length === 0 ? (
                <Typography variant="body2" color="text.secondary" textAlign="center">{t("clubs.editor.no-member-hit")}</Typography>
              ) : (
                <Stack spacing={1}>
                  {visibleMembers.map((member) => (
                    <Stack
                      key={`${member.hat}-${member.id}`}
                      role="button"
                      tabIndex={0}
                      onClick={() => openMember(member)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          openMember(member);
                        }
                      }}
                      direction={{ xs: "column", sm: "row" }}
                      spacing={1}
                      alignItems={{ sm: "center" }}
                      sx={{ p: 1.25, borderRadius: 2, border: "1px solid", borderColor: "divider", cursor: "pointer", "&:hover": { bgcolor: "action.hover" } }}
                    >
                      <Box sx={{ width: 32, height: 32, borderRadius: 1.5, bgcolor: "action.hover", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 12, flexShrink: 0 }}>
                        {monogram(member.name, "")}
                      </Box>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography variant="body2" fontWeight={700} noWrap>{member.name}</Typography>
                        <Typography variant="caption" color="text.secondary" noWrap>
                          {member.email || t("clubs.editor.no-email")} • {member.dateOfBirth || t("clubs.editor.no-birth")}
                        </Typography>
                      </Box>
                      <Typography variant="body2" sx={{ minWidth: 90 }}>{t(roleLabelKey(member.hat))}</Typography>
                      <IconButton
                        aria-label={t("clubs.editor.remove-member-title")}
                        onClick={(event) => {
                          event.stopPropagation();
                          setDeleteTarget({ type: "member", id: member.id, name: member.name, hat: member.hat });
                        }}
                      >
                        <DeleteOutlineIcon />
                      </IconButton>
                    </Stack>
                  ))}
                </Stack>
              )}

            </Stack>
          )}
        </DialogContent>

        <DialogActions sx={{ px: 3, py: 2, justifyContent: "space-between", borderTop: 1, borderColor: "divider" }}>
          <Typography variant="body2" color="success.main" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
            {saved && (<><CheckIcon fontSize="small" /> {t("clubs.editor.saved")}</>)}
          </Typography>
          <Stack direction="row" spacing={1}>
            <SecondaryButton onClick={onClose}>{t("clubs.editor.cancel")}</SecondaryButton>
            <PrimaryButton startIcon={<SaveIcon />} onClick={handleSave} disabled={updateTeam.isPending}>
              {t("clubs.editor.save")}
            </PrimaryButton>
          </Stack>
        </DialogActions>
      </Dialog>

      <ClubUserDialog
        open={selectedUserId != null}
        user={accountUser}
        clubName={clubName}
        clubShortName={clubShortName}
        teams={clubTeams}
        placements={selectedUserId ? placementsForUser(selectedUserId, clubTeams) : []}
        isPlatformAdmin={actor?.role === ROLES.ADMIN}
        actorId={actor?.id}
        onClose={() => setSelectedUserId(null)}
        onChanged={refresh}
      />
      <RosterPlayerDialog
        open={rosterProfileId != null}
        profileId={rosterProfileId}
        firstName={members.find((member) => member.id === rosterProfileId)?.firstName}
        lastName={members.find((member) => member.id === rosterProfileId)?.lastName}
        hasLogin={Boolean(members.find((member) => member.id === rosterProfileId)?.userId)}
        onClose={() => setRosterProfileId(null)}
        onChanged={refresh}
      />
      <HoldToDeleteDialog
        open={deleteTarget != null}
        title={deleteTarget?.type === "member" ? t("clubs.editor.remove-member-title") : t("clubs.editor.delete-team-title")}
        itemName={deleteTarget?.name ?? ""}
        description={deleteTarget?.type === "member" ? t("clubs.editor.remove-member-body") : t("clubs.editor.delete-team-body")}
        pending={deleting}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
      />
    </>
  );
};
