import { useState } from "react";
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Autocomplete, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Pagination, Stack, TextField, Typography } from "@mui/material";
import VerifiedOutlinedIcon from "@mui/icons-material/VerifiedOutlined";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import EditNoteIcon from "@mui/icons-material/EditNote";
import FileUploadOutlinedIcon from "@mui/icons-material/FileUploadOutlined";
import { useTranslation } from "react-i18next";
import {
  CLUB_MEDICAL_FILTERS,
  clubMedicalAccessesQueryKey,
  clubMedicalStatusQueryKey,
  clubMedicalTeamsQueryKey,
  getClubMedicalAdminAccesses,
  getClubMedicalStatus,
  type ClubMedicalFilter,
  type ClubMedicalStatusItem,
} from "../../../api/medical";
import { useSnackbar } from "../../../context/SnackbarContext";
import { formatPersonName } from "../../../utils/stringUtils";
import { ClubMedicalSettingsPanel } from "../../../components/medical/MedicalSettings";
import { MedicalInPersonDialog, MedicalVerifyDialog, openMedicalFileInNewTab } from "../../../components/medical/MedicalDialogs";
import { MedicalSection } from "../../../components/medical/MedicalSection";
import { MedicalStateIcon } from "../../../components/medical/MedicalBits";
import { formatInstant, MEDICAL_COLORS, medicalSentence, problemDetail } from "../../../components/medical/medicalFormat";

const PAGE_SIZE = 20;
const CHILDREN_PAGE_SIZE = 10;
const ALL_TEAMS = "all";

const actionSx = { textTransform: "none", fontWeight: 700, borderRadius: 2 } as const;

/**
 * The club's "Sportorvosi" tab (docs/sportorvosi-jogszeruseg.md, Felület): the settings, the list of children on the
 * club's ON teams with verification and in-person entry, and the platform admin's accesses shown to the club. The
 * platform admin sees only the settings, read only: it has no medical view of the children.
 */
export const ClubMedicalTab = ({ clubId, isPlatformAdmin }: { clubId: string; isPlatformAdmin: boolean }) => {
  const { t } = useTranslation();
  return (
    <Stack spacing={3}>
      <ClubMedicalSettingsPanel clubId={clubId} readOnly={isPlatformAdmin} />
      {!isPlatformAdmin && <ClubMedicalChildren clubId={clubId} />}
      {!isPlatformAdmin && <ClubMedicalAccesses clubId={clubId} />}
      {isPlatformAdmin && (
        <Typography variant="caption" color="text.secondary">{t("medical.club.admin-no-list")}</Typography>
      )}
    </Stack>
  );
};

type DialogState =
  | { kind: "verify"; item: ClubMedicalStatusItem; method: "ORIGINAL" | "FILE" }
  | { kind: "in-person"; item: ClubMedicalStatusItem }
  | { kind: "upload"; item: ClubMedicalStatusItem }
  | null;

const ClubMedicalChildren = ({ clubId }: { clubId: string }) => {
  const { t, i18n } = useTranslation();
  const { showError } = useSnackbar();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<ClubMedicalFilter | null>(null);
  const [page, setPage] = useState(1);
  const [dialog, setDialog] = useState<DialogState>(null);
  const [teamChoice, setTeamChoice] = useState<string | null>(null);
  // The tab opens on one team, not on the whole club, so the team list comes first.
  const { data: overview, error: overviewError } = useQuery({
    queryKey: clubMedicalTeamsQueryKey(clubId),
    queryFn: () => getClubMedicalStatus(clubId, null, null, 1, 1),
    enabled: clubId.length > 0,
    retry: false,
  });
  const teams = overview?.teams ?? [];
  const selectedTeam = teamChoice === ALL_TEAMS || teams.some((team) => team.teamId === teamChoice)
    ? teamChoice
    : teams[0]?.teamId ?? null;
  const teamId = selectedTeam === ALL_TEAMS ? null : selectedTeam;
  const { data, error: listError } = useQuery({
    queryKey: clubMedicalStatusQueryKey(clubId, filter, teamId, page),
    queryFn: () => getClubMedicalStatus(clubId, filter, teamId, page, CHILDREN_PAGE_SIZE),
    enabled: teams.length > 0,
    placeholderData: keepPreviousData,
    retry: false,
  });
  const error = overviewError ?? listError;

  if (error) {
    const { status } = problemDetail(error);
    if (status === 403) return null;
    return <Alert severity="error">{t("medical.messages.load-failed")}</Alert>;
  }
  if (!overview) return null;
  if (teams.length === 0) {
    return <Typography variant="body2" color="text.secondary">{t("medical.club.no-on-teams")}</Typography>;
  }
  if (!data) return null;
  const teamName = (teamId: string) => data.teams.find((team) => team.teamId === teamId)?.name ?? "";
  const teamOptions = [{ teamId: ALL_TEAMS, name: t("medical.club.all-teams") }, ...teams];

  // The section refreshes its own state; the list behind the dialog is read again when it closes.
  const closeUpload = () => {
    setDialog(null);
    void queryClient.invalidateQueries({ queryKey: ["medical", "club", clubId] });
  };

  const open = async (item: ClubMedicalStatusItem) => {
    if (!item.certificateId) return;
    try {
      await openMedicalFileInNewTab(item.profileId, item.certificateId);
    } catch (err) {
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    }
  };

  return (
    <Box sx={{ p: 2.5, borderRadius: 3, border: "1px solid", borderColor: "divider" }}>
      <Stack spacing={2}>
        <Typography variant="body1" fontWeight={700}>{t("medical.club.children-title")}</Typography>
        {data.teamsWithoutCoach.length > 0 && (
          <Alert severity="warning">
            {t("medical.club.no-coach", {
              count: data.teamsWithoutCoach.length,
              teams: data.teamsWithoutCoach.map((team) => team.name).filter(Boolean).join(", "),
            })}
          </Alert>
        )}
        {teams.length > 1 && (
          <Autocomplete
            size="small"
            disableClearable
            options={teamOptions}
            value={teamOptions.find((option) => option.teamId === selectedTeam) ?? teamOptions[0]}
            getOptionLabel={(option) => option.name ?? ""}
            getOptionKey={(option) => option.teamId}
            isOptionEqualToValue={(option, value) => option.teamId === value.teamId}
            onChange={(_event, option) => { setTeamChoice(option.teamId); setPage(1); }}
            noOptionsText={t("medical.club.team-no-match")}
            slotProps={{ listbox: { sx: { maxHeight: 240 } } }}
            renderInput={(params) => <TextField {...params} label={t("medical.club.team-filter")} />}
            sx={{ maxWidth: 320 }}
          />
        )}
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Chip label={t("medical.club.filter.ALL")} color={filter === null ? "primary" : "default"}
            onClick={() => { setFilter(null); setPage(1); }} />
          {CLUB_MEDICAL_FILTERS.map((name) => (
            <Chip key={name} label={`${t(`medical.club.filter.${name}`)} (${data.counts[name] ?? 0})`}
              color={filter === name ? "primary" : "default"}
              onClick={() => { setFilter(name); setPage(1); }} />
          ))}
        </Stack>
        {data.page.items.length === 0 ? (
          <Typography variant="body2" color="text.secondary">{t("medical.club.empty")}</Typography>
        ) : (
          <Stack spacing={1}>
            {data.page.items.map((item) => {
              const name = formatPersonName(item.firstName, item.lastName);
              const color = MEDICAL_COLORS[item.status];
              const hasRow = item.status !== "MISSING";
              const positive = item.result === "VERSENYEZHET" || item.result === "ALKALMAS";
              // Where this club asks for the file and there is one, the check is from the file; otherwise from the paper.
              const method = item.fileRequired && item.hasFile ? "FILE" : "ORIGINAL";
              const canVerify = hasRow && !item.verified && Boolean(item.result) && (method === "ORIGINAL" || item.hasFile !== undefined);
              return (
                <Stack key={item.profileId} direction={{ xs: "column", md: "row" }} spacing={1.5} alignItems={{ md: "center" }}
                  sx={{ p: 1.5, borderRadius: 2, border: "1px solid", borderColor: `${color}55`, borderLeft: `4px solid ${color}` }}>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <MedicalStateIcon state={item.status} sx={{ fontSize: 18, color }} />
                      <Typography fontWeight={700}>{name}</Typography>
                      {item.verified && <VerifiedOutlinedIcon sx={{ fontSize: 16, color: MEDICAL_COLORS.VALID }} />}
                    </Stack>
                    <Typography variant="body2">
                      {medicalSentence(t, i18n.language, item.status, item.validUntil, item.daysRemaining, item.result)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {item.teamIds.map(teamName).filter(Boolean).join(", ")}
                      {hasRow && positive && !item.verified ? ` · ${t("medical.verify.waiting")}` : ""}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {item.hasFile && (
                      <Button size="small" variant="outlined" startIcon={<OpenInNewIcon />} onClick={() => void open(item)} sx={actionSx}>
                        {t("medical.actions.open")}
                      </Button>
                    )}
                    {canVerify && (
                      <Button size="small" variant="outlined" color="success" startIcon={<VerifiedOutlinedIcon />}
                        onClick={() => setDialog({ kind: "verify", item, method })} sx={actionSx}>
                        {t("medical.verify.action")}
                      </Button>
                    )}
                    {/* Once the paper reached the club the date is enough; the copy stays the lesser choice. */}
                    <Button size="small" variant="outlined" color="inherit" startIcon={<EditNoteIcon />}
                      onClick={() => setDialog({ kind: "in-person", item })} sx={{ ...actionSx, borderColor: "divider" }}>
                      {t("medical.in-person.action")}
                    </Button>
                    {item.fileRequired && (
                      <Button size="small" color="inherit" startIcon={<FileUploadOutlinedIcon />}
                        onClick={() => setDialog({ kind: "upload", item })} sx={{ ...actionSx, color: "text.secondary" }}>
                        {t("medical.club.upload")}
                      </Button>
                    )}
                  </Stack>
                </Stack>
              );
            })}
          </Stack>
        )}
        {data.page.totalPages > 1 && (
          <Pagination count={data.page.totalPages} page={page} onChange={(_event, next) => setPage(next)} />
        )}
      </Stack>
      {dialog?.kind === "verify" && (
        <MedicalVerifyDialog
          open
          profileId={dialog.item.profileId}
          name={formatPersonName(dialog.item.firstName, dialog.item.lastName)}
          certificateId={dialog.item.certificateId}
          result={dialog.item.result}
          validUntil={dialog.item.validUntil}
          method={dialog.method}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "in-person" && (
        <MedicalInPersonDialog
          open
          profileId={dialog.item.profileId}
          name={formatPersonName(dialog.item.firstName, dialog.item.lastName)}
          replacing={dialog.item.status !== "MISSING"}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "upload" && (
        <Dialog
          open
          fullWidth
          maxWidth="sm"
          onClose={closeUpload}
          sx={{ "& .MuiDialog-paper": { overflow: "hidden" } }}
        >
          <DialogTitle sx={{ fontWeight: 700 }}>
            {t("medical.club.upload-title", { name: formatPersonName(dialog.item.firstName, dialog.item.lastName) })}
          </DialogTitle>
          <DialogContent dividers>
            <MedicalSection profileId={dialog.item.profileId} viewer="manager" variant="bare" />
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button onClick={closeUpload} color="inherit" sx={actionSx}>{t("medical.actions.close")}</Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
};

/** The platform admin's accesses shown to this club, newest first. */
const ClubMedicalAccesses = ({ clubId }: { clubId: string }) => {
  const { t, i18n } = useTranslation();
  const [page, setPage] = useState(1);
  const { data, error } = useQuery({
    queryKey: clubMedicalAccessesQueryKey(clubId, page),
    queryFn: () => getClubMedicalAdminAccesses(clubId, page, PAGE_SIZE),
    enabled: clubId.length > 0,
    retry: false,
  });
  if (error || !data) return null;
  return (
    <Box sx={{ p: 2.5, borderRadius: 3, border: "1px solid", borderColor: "divider" }}>
      <Stack spacing={1.5}>
        <Typography variant="body1" fontWeight={700}>{t("medical.club.accesses-title")}</Typography>
        {data.items.length === 0 ? (
          <Typography variant="body2" color="text.secondary">{t("medical.club.accesses-empty")}</Typography>
        ) : (
          data.items.map((access) => (
            <Box key={access.id ?? access.createdAt} sx={{ p: 1.25, borderRadius: 2, border: "1px solid", borderColor: "divider" }}>
              <Typography variant="body2" fontWeight={700}>
                {formatInstant(access.createdAt, i18n.language)} · {t(`medical.admin.action.${access.action}`)} · {access.playerName}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                {formatPersonName(access.adminFirstName, access.adminLastName) || t("medical.club.unknown-admin")}: {access.reason}
              </Typography>
            </Box>
          ))
        )}
        {data.totalPages > 1 && <Pagination count={data.totalPages} page={page} onChange={(_event, next) => setPage(next)} />}
      </Stack>
    </Box>
  );
};
