import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Link,
  MenuItem,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";
import MonitorHeartOutlinedIcon from "@mui/icons-material/MonitorHeartOutlined";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import CancelOutlinedIcon from "@mui/icons-material/CancelOutlined";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { useTranslation } from "react-i18next";
import {
  clubMedicalQueryKey,
  getClubMedicalNotice,
  getClubMedicalSettings,
  MEDICAL_NOTICE_MAX_BYTES,
  removeClubMedicalNotice,
  setTeamMedicalMode,
  teamMedicalQueryKey,
  updateClubMedicalSettings,
  uploadClubMedicalNotice,
  type ClubMedicalSettingsUpdate,
  type MedicalEvidence,
  type MedicalMode,
} from "../../api/medical";
import { MY_CHILDREN_QUERY_KEY } from "../../context/ChildrenContext";
import { useSnackbar } from "../../context/SnackbarContext";
import { PrimaryButton } from "../ui/PrimaryButton";
import { SecondaryButton } from "../ui/SecondaryButton";
import { HoldToDeleteDialog } from "../../pages/clubs/dialogs/HoldToDeleteDialog";
import { deletionCounts, formatInstant, problemDetail } from "./medicalFormat";

const RETENTION_YEARS = [1, 3, 5] as const;
const GREEN = "#10b981";

/** The confirmation of a change that deletes certificates, files, or both. */
const ConfirmDeletion = ({
  counts,
  pending,
  onClose,
  onConfirm,
}: {
  counts: { profiles: number; files: number } | null;
  pending: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) => {
  const { t } = useTranslation();
  const lines = [
    counts && counts.profiles > 0 ? t("medical.settings.confirm-body", { count: counts.profiles }) : null,
    counts && counts.files > 0 ? t("medical.settings.confirm-files", { count: counts.files }) : null,
  ].filter(Boolean).join(" ");
  return (
    <HoldToDeleteDialog
      open={counts != null}
      title={t("medical.settings.confirm-title")}
      itemName=""
      description={lines}
      pending={pending}
      onClose={onClose}
      onConfirm={onConfirm}
    />
  );
};

const ChecklistItem = ({ ok, label }: { ok: boolean; label: string }) => (
  <Stack direction="row" spacing={0.5} alignItems="center">
    {ok
      ? <CheckCircleOutlineIcon sx={{ fontSize: 18, color: GREEN }} />
      : <CancelOutlinedIcon sx={{ fontSize: 18, color: "error.main" }} />}
    <Typography variant="body2" fontWeight={700}>{label}</Typography>
  </Stack>
);

/**
 * The club's sport medical settings on the club tab (docs/sportorvosi-jogszeruseg.md, Felület): the switch, the
 * evidence mode, the coaches' file access, the retention, the legal basis, the privacy notice and the processing
 * agreement, with a checklist of what is missing. Only the club's own admins write it; the platform admin sees it
 * read only, with the controls disabled.
 */
export const ClubMedicalSettingsPanel = ({ clubId, readOnly }: { clubId: string; readOnly: boolean }) => {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const { showSuccess, showError } = useSnackbar();
  const { data, error } = useQuery({
    queryKey: clubMedicalQueryKey(clubId),
    queryFn: () => getClubMedicalSettings(clubId),
    enabled: clubId.length > 0,
    retry: false,
  });
  const [mode, setMode] = useState<"ON" | "OFF">("OFF");
  const [evidence, setEvidence] = useState<"DATE_ONLY" | "REDACTED_FILE">("DATE_ONLY");
  const [retention, setRetention] = useState(3);
  const [legalBasis, setLegalBasis] = useState("");
  const [noticeUrl, setNoticeUrl] = useState("");
  const [acceptDpa, setAcceptDpa] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [coachAccessAck, setCoachAccessAck] = useState(false);
  const [pending, setPending] = useState(false);
  const [confirm, setConfirm] = useState<{ counts: { profiles: number; files: number }; body: ClubMedicalSettingsUpdate } | null>(null);
  const [removeNoticeOpen, setRemoveNoticeOpen] = useState(false);
  const fileInput = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!data) return;
    setMode(data.mode);
    setEvidence(data.evidence);
    setRetention(data.historyRetentionYears);
    setLegalBasis(data.legalBasis ?? "");
    setNoticeUrl("");
    setAcceptDpa(false);
    setAcknowledged(false);
    setCoachAccessAck(false);
  }, [data]);

  if (error) {
    const { status } = problemDetail(error);
    if (status === 403 || status === 404) return null;
    return <Alert severity="error">{t("medical.messages.load-failed")}</Alert>;
  }
  if (!data) return null;

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["medical"] }),
      queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY }),
    ]);

  const send = async (body: ClubMedicalSettingsUpdate, successKey = "medical.settings.saved") => {
    setPending(true);
    try {
      await updateClubMedicalSettings(clubId, body);
      setConfirm(null);
      showSuccess(t(successKey));
      await refresh();
    } catch (err) {
      const counts = deletionCounts(err);
      if (counts && !body.confirmDeletion) {
        setConfirm({ counts, body: { ...body, confirmDeletion: true } });
        return;
      }
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    } finally {
      setPending(false);
    }
  };

  const turningOn = mode === "ON" && data.mode !== "ON";
  const legalChanged = legalBasis.trim() !== (data.legalBasis ?? "") && legalBasis.trim().length > 0;
  const changed = mode !== data.mode || evidence !== data.evidence || retention !== data.historyRetentionYears || legalChanged;
  const dpaReady = Boolean(data.currentDpaVersion) && data.dpaVersion === data.currentDpaVersion;
  const legalReady = Boolean(data.legalBasis);
  const noticeReady = Boolean(data.notice);
  const readyAfterSave = (legalReady || legalBasis.trim().length >= 10) && noticeReady && (dpaReady || acceptDpa);
  const fileModeBlocked = !data.storageSecure && data.evidence !== "REDACTED_FILE";
  const hasFileMode = data.evidence === "REDACTED_FILE" || evidence === "REDACTED_FILE";
  const storageReason = data.storageIssues.map((issue) => t(`medical.storage.${issue.split(":")[0]}`, { defaultValue: issue })).join(", ");

  const save = () =>
    void send({
      mode,
      acknowledged: turningOn ? acknowledged : undefined,
      evidence,
      historyRetentionYears: retention,
      legalBasis: legalChanged ? legalBasis.trim() : undefined,
      acceptDpaVersion: acceptDpa && data.currentDpaVersion ? data.currentDpaVersion : undefined,
    });

  const uploadNotice = async (file: File) => {
    if (file.size > MEDICAL_NOTICE_MAX_BYTES) {
      showError(t("medical.notice.too-large"));
      return;
    }
    setPending(true);
    try {
      await uploadClubMedicalNotice(clubId, file);
      showSuccess(t("medical.notice.saved"));
      await refresh();
    } catch (err) {
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    } finally {
      setPending(false);
    }
  };

  const removeNotice = async () => {
    setPending(true);
    try {
      await removeClubMedicalNotice(clubId);
      setRemoveNoticeOpen(false);
      showSuccess(t("medical.notice.removed"));
      await refresh();
    } catch (err) {
      setRemoveNoticeOpen(false);
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    } finally {
      setPending(false);
    }
  };

  const openNotice = async () => {
    const tab = window.open("", "_blank");
    try {
      const { url } = await getClubMedicalNotice(clubId);
      if (tab) {
        tab.opener = null;
        tab.location.href = url;
      } else {
        window.open(url, "_blank", "noopener");
      }
    } catch (err) {
      tab?.close();
      const { detail } = problemDetail(err);
      showError(detail || t("medical.messages.failed"));
    }
  };

  return (
    <Box sx={{ p: 2.5, borderRadius: 3, border: "1px solid", borderColor: "divider" }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap" useFlexGap>
          <Typography variant="body1" fontWeight={700} sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
            <MonitorHeartOutlinedIcon sx={{ fontSize: 20, color: GREEN }} />
            {t("medical.settings.title")}
          </Typography>
          <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
            <ChecklistItem ok={legalReady} label={t("medical.settings.check-legal")} />
            <ChecklistItem ok={noticeReady} label={t("medical.settings.check-notice")} />
            <ChecklistItem ok={dpaReady} label={t("medical.settings.check-dpa", { version: data.currentDpaVersion ?? "" })} />
          </Stack>
        </Stack>
        {readOnly && <Alert severity="info">{t("medical.settings.read-only")}</Alert>}
        <Typography variant="caption" color="text.secondary">{t("medical.settings.hint")}</Typography>
        {data.mode === "ON" && !data.ready && <Alert severity="warning">{t("medical.settings.not-ready-on")}</Alert>}

        <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
          <ToggleButtonGroup size="small" exclusive value={mode} disabled={readOnly || pending}
            onChange={(_event, next: "ON" | "OFF" | null) => next && setMode(next)}>
            <ToggleButton value="OFF" sx={{ px: 2, textTransform: "none" }}>{t("medical.settings.off")}</ToggleButton>
            <ToggleButton value="ON" color="success" disabled={data.mode !== "ON" && !readyAfterSave}
              sx={{ px: 2, textTransform: "none" }}>{t("medical.settings.on")}</ToggleButton>
          </ToggleButtonGroup>
          <ToggleButtonGroup size="small" exclusive value={evidence} disabled={readOnly || pending}
            onChange={(_event, next: "DATE_ONLY" | "REDACTED_FILE" | null) => next && setEvidence(next)}>
            <ToggleButton value="DATE_ONLY" sx={{ px: 2, textTransform: "none" }}>{t("medical.evidence.DATE_ONLY")}</ToggleButton>
            <ToggleButton value="REDACTED_FILE" disabled={fileModeBlocked} sx={{ px: 2, textTransform: "none" }}>
              {t("medical.evidence.REDACTED_FILE")}
            </ToggleButton>
          </ToggleButtonGroup>
          <TextField select size="small" label={t("medical.settings.retention")} value={retention} disabled={readOnly || pending}
            onChange={(event) => setRetention(Number(event.target.value))} sx={{ minWidth: 180 }}>
            {RETENTION_YEARS.map((years) => (
              <MenuItem key={years} value={years}>{t("medical.settings.years", { count: years })}</MenuItem>
            ))}
          </TextField>
        </Stack>
        {!data.storageSecure && (
          <Typography variant="caption" sx={{ color: "warning.main" }}>
            {t("medical.settings.storage-not-secure", { reason: storageReason })}
          </Typography>
        )}
        {data.mode === "ON" && data.acknowledgedAt && (
          <Typography variant="caption" color="text.secondary">
            {t("medical.settings.acknowledged-at", { date: formatInstant(data.acknowledgedAt, i18n.language) })}
          </Typography>
        )}

        <TextField
          label={t("medical.settings.legal-basis")}
          helperText={t("medical.settings.legal-basis-hint")}
          placeholder={t("medical.settings.legal-basis-example")}
          value={legalBasis}
          disabled={readOnly || pending}
          onChange={(event) => setLegalBasis(event.target.value)}
          multiline
          minRows={2}
          slotProps={{ htmlInput: { maxLength: 500 }, inputLabel: { shrink: true } }}
        />
        {legalBasis.trim().length > 0 && (
          <Typography variant="caption" color="text.secondary">
            {t("medical.settings.legal-basis-preview", { law: legalBasis.trim(), interpolation: { escapeValue: false } })}
          </Typography>
        )}

        <Box>
          <Typography variant="body2" fontWeight={700} sx={{ mb: 0.75 }}>{t("medical.notice.title")}</Typography>
          {data.notice ? (
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
              <Chip size="small" label={t(`medical.notice.kind.${data.notice.kind}`)} />
              <Typography variant="caption" color="text.secondary">
                {t("medical.notice.version", { version: data.notice.version, date: formatInstant(data.notice.updatedAt, i18n.language) })}
              </Typography>
              <Button size="small" startIcon={<OpenInNewIcon />} onClick={() => void openNotice()} sx={{ textTransform: "none" }}>
                {t("medical.notice.open")}
              </Button>
              {!readOnly && (
                <Button size="small" color="error" startIcon={<DeleteOutlineIcon />} disabled={pending}
                  onClick={() => setRemoveNoticeOpen(true)} sx={{ textTransform: "none" }}>
                  {t("medical.notice.remove")}
                </Button>
              )}
            </Stack>
          ) : (
            <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 1 }}>{t("medical.notice.none")}</Typography>
          )}
          {!readOnly && (
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
              <input ref={fileInput} type="file" accept="application/pdf" hidden
                onChange={(event) => {
                  const picked = event.target.files?.[0];
                  event.target.value = "";
                  if (picked) void uploadNotice(picked);
                }} />
              <SecondaryButton startIcon={<UploadFileIcon />} disabled={pending} onClick={() => fileInput.current?.click()}>
                {t("medical.notice.upload")}
              </SecondaryButton>
              <TextField size="small" label={t("medical.notice.url")} value={noticeUrl} disabled={pending}
                onChange={(event) => setNoticeUrl(event.target.value)} sx={{ minWidth: 260, flex: 1 }} />
              <SecondaryButton disabled={pending || !noticeUrl.trim().startsWith("https://")}
                onClick={() => void send({ noticeUrl: noticeUrl.trim() }, "medical.notice.saved")}>
                {t("medical.notice.save-url")}
              </SecondaryButton>
            </Stack>
          )}
          {data.noticeTemplateUrl && (
            <Link href={data.noticeTemplateUrl} target="_blank" rel="noopener" variant="caption" sx={{ display: "inline-block", mt: 0.75 }}>
              {t("medical.notice.template")}
            </Link>
          )}
        </Box>

        <Box>
          <Typography variant="body2" fontWeight={700} sx={{ mb: 0.5 }}>{t("medical.dpa.title")}</Typography>
          {!data.currentDpaVersion ? (
            <Typography variant="caption" color="text.secondary">{t("medical.dpa.unavailable")}</Typography>
          ) : dpaReady ? (
            <Typography variant="caption" color="text.secondary">
              {t("medical.dpa.accepted", { version: data.dpaVersion, date: formatInstant(data.dpaAcceptedAt, i18n.language) })}
            </Typography>
          ) : (
            <Stack spacing={0.5}>
              {data.dpaVersion && (
                <Typography variant="caption" sx={{ color: "warning.main" }}>
                  {t("medical.dpa.new-version", { old: data.dpaVersion, version: data.currentDpaVersion })}
                </Typography>
              )}
              {data.dpaDocumentUrl && (
                <Link href={data.dpaDocumentUrl} target="_blank" rel="noopener" variant="body2">{t("medical.dpa.open", { version: data.currentDpaVersion })}</Link>
              )}
              <FormControlLabel
                disabled={readOnly || pending}
                control={<Checkbox checked={acceptDpa} onChange={(event) => setAcceptDpa(event.target.checked)} color="success" />}
                label={<Typography variant="body2">{t("medical.dpa.accept", { version: data.currentDpaVersion })}</Typography>}
              />
            </Stack>
          )}
        </Box>

        {turningOn && (
          <FormControlLabel
            sx={{ alignItems: "flex-start", m: 0 }}
            control={<Checkbox checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} color="success" sx={{ mt: -0.75 }} />}
            label={<Typography variant="body2">{t("medical.settings.ack")}</Typography>}
          />
        )}

        <Box>
          <PrimaryButton
            size="small"
            disabled={readOnly || pending || (!changed && !acceptDpa) || (turningOn && !acknowledged)}
            onClick={save}
            sx={{ py: 0.75 }}
          >
            {t("medical.settings.save")}
          </PrimaryButton>
        </Box>

        {hasFileMode && (
          <Box sx={{ pt: 1, borderTop: "1px solid", borderColor: "divider" }}>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Switch
                checked={data.coachFileAccess}
                disabled={readOnly || pending || (!data.coachFileAccess && !coachAccessAck)}
                onChange={(event) => void send(event.target.checked
                  ? { coachFileAccess: true, coachFileAccessAcknowledged: true }
                  : { coachFileAccess: false })}
                color="success"
                slotProps={{ input: { "aria-label": t("medical.coach-access.switch") } }}
              />
              <Typography variant="body2" fontWeight={700}>{t("medical.coach-access.switch")}</Typography>
            </Stack>
            {!data.coachFileAccess && !readOnly && (
              <FormControlLabel
                sx={{ alignItems: "flex-start", m: 0, mt: 0.5 }}
                control={<Checkbox checked={coachAccessAck} onChange={(event) => setCoachAccessAck(event.target.checked)} color="success" sx={{ mt: -0.75 }} />}
                label={<Typography variant="caption">{t("medical.coach-access.ack")}</Typography>}
              />
            )}
          </Box>
        )}
      </Stack>
      <ConfirmDeletion
        counts={confirm?.counts ?? null}
        pending={pending}
        onClose={() => setConfirm(null)}
        onConfirm={() => confirm && void send(confirm.body)}
      />
      <HoldToDeleteDialog
        open={removeNoticeOpen}
        title={t("medical.notice.remove-title")}
        itemName=""
        description={t("medical.notice.remove-body")}
        pending={pending}
        onClose={() => setRemoveNoticeOpen(false)}
        onConfirm={() => void removeNotice()}
      />
    </Box>
  );
};

/** The team's own settings in the club admin's team dialog: collection (club / on / off) and evidence. */
export const TeamMedicalModeField = ({
  teamId,
  mode,
  evidence,
  onSaved,
}: {
  teamId: string;
  mode?: MedicalMode;
  evidence?: MedicalEvidence;
  onSaved?: () => void;
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { showSuccess, showError } = useSnackbar();
  const [pendingBody, setPendingBody] = useState<{ mode?: MedicalMode; evidence?: MedicalEvidence } | null>(null);
  const [confirmCounts, setConfirmCounts] = useState<{ profiles: number; files: number } | null>(null);
  const [ackOpen, setAckOpen] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [saving, setSaving] = useState(false);

  if (!mode) return null;

  // Turning the team on can start collection in a club that is off, so it takes the club's acknowledgement.
  const chooseMode = (next: MedicalMode) => {
    if (next === "ON" && mode !== "ON") {
      setAcknowledged(false);
      setAckOpen(true);
      return;
    }
    void save({ mode: next }, false);
  };

  const save = async (body: { mode?: MedicalMode; evidence?: MedicalEvidence }, confirmDeletion: boolean, ack = false) => {
    setSaving(true);
    try {
      await setTeamMedicalMode(teamId, { ...body, confirmDeletion, acknowledged: ack });
      setConfirmCounts(null);
      setPendingBody(null);
      setAckOpen(false);
      showSuccess(t("medical.settings.saved"));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: teamMedicalQueryKey(teamId) }),
        queryClient.invalidateQueries({ queryKey: ["medical"] }),
      ]);
      onSaved?.();
    } catch (err) {
      const counts = deletionCounts(err);
      if (counts) {
        setPendingBody(body);
        setConfirmCounts(counts);
        return;
      }
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
        <TextField
          select
          fullWidth
          label={t("medical.settings.team-label")}
          value={mode}
          disabled={saving}
          onChange={(event) => chooseMode(event.target.value as MedicalMode)}
          slotProps={{ input: { startAdornment: <MonitorHeartOutlinedIcon sx={{ mr: 1, color: GREEN }} /> } }}
        >
          <MenuItem value="INHERIT">{t("medical.settings.team-inherit")}</MenuItem>
          <MenuItem value="ON">{t("medical.settings.team-on")}</MenuItem>
          <MenuItem value="OFF">{t("medical.settings.team-off")}</MenuItem>
        </TextField>
        <TextField
          select
          fullWidth
          label={t("medical.evidence.team-label")}
          value={evidence ?? "INHERIT"}
          disabled={saving}
          onChange={(event) => void save({ evidence: event.target.value as MedicalEvidence }, false)}
        >
          <MenuItem value="INHERIT">{t("medical.evidence.INHERIT")}</MenuItem>
          <MenuItem value="DATE_ONLY">{t("medical.evidence.DATE_ONLY")}</MenuItem>
          <MenuItem value="REDACTED_FILE">{t("medical.evidence.REDACTED_FILE")}</MenuItem>
        </TextField>
      </Stack>
      <Dialog open={ackOpen} onClose={() => setAckOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{t("medical.settings.team-ack-title")}</DialogTitle>
        <DialogContent>
          <FormControlLabel
            sx={{ alignItems: "flex-start", m: 0 }}
            control={<Checkbox checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} color="success" sx={{ mt: -0.75 }} />}
            label={<Typography variant="body2">{t("medical.settings.ack")}</Typography>}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <SecondaryButton onClick={() => setAckOpen(false)}>{t("common.cancel")}</SecondaryButton>
          <PrimaryButton disabled={!acknowledged || saving} onClick={() => void save({ mode: "ON" }, false, true)} sx={{ py: 0.75 }}>
            {t("medical.settings.save")}
          </PrimaryButton>
        </DialogActions>
      </Dialog>
      <ConfirmDeletion
        counts={confirmCounts}
        pending={saving}
        onClose={() => {
          setConfirmCounts(null);
          setPendingBody(null);
        }}
        onConfirm={() => pendingBody && void save(pendingBody, true, pendingBody.mode === "ON")}
      />
    </>
  );
};
