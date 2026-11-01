import { useEffect, useState, type ReactNode } from "react";
import { useDropzone, type FileRejection } from "react-dropzone";
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
  IconButton,
  Paper,
  Stack,
  Switch,
  Tooltip,
  Typography,
} from "@mui/material";
import MonitorHeartOutlinedIcon from "@mui/icons-material/MonitorHeartOutlined";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import AddIcon from "@mui/icons-material/Add";
import AutorenewIcon from "@mui/icons-material/Autorenew";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import FileUploadOutlinedIcon from "@mui/icons-material/FileUploadOutlined";
import InsertDriveFileOutlinedIcon from "@mui/icons-material/InsertDriveFileOutlined";
import CloseIcon from "@mui/icons-material/Close";
import HistoryIcon from "@mui/icons-material/History";
import { useTranslation } from "react-i18next";
import {
  getClubMedicalNotice,
  getMedicalStatus,
  getMyMedicalStatus,
  markMedicalSeen,
  medicalQueryKey,
  MEDICAL_CONTENT_TYPES,
  MEDICAL_MAX_BYTES,
  myMedicalQueryKey,
  setMedicalParentAccess,
  submitMedicalCertificate,
  withdrawMedicalCertificate,
  type MedicalNoticeItem,
  type MedicalResult,
  type MedicalStatus,
} from "../../api/medical";
import { MY_CHILDREN_QUERY_KEY } from "../../context/ChildrenContext";
import { useSnackbar } from "../../context/SnackbarContext";
import { useView } from "../../context/ViewContext";
import { ROLES } from "../../types/roles";
import { formatPersonName } from "../../utils/stringUtils";
import { PrimaryButton } from "../ui/PrimaryButton";
import { SecondaryButton } from "../ui/SecondaryButton";
import { HoldToDeleteDialog } from "../../pages/clubs/dialogs/HoldToDeleteDialog";
import {
  beforeSuffix,
  formatInstant,
  formatLongDate,
  historyValidLabel,
  MEDICAL_COLORS,
  medicalSentence,
  problemDetail,
} from "./medicalFormat";
import { MedicalStateIcon } from "./MedicalBits";
import { MedicalAdminAccessDialog, MedicalDataRule, MedicalResultFields, openMedicalFileInNewTab, resultComplete } from "./MedicalDialogs";
import { MedicalRedactDialog } from "./MedicalRedactDialog";

const GREEN = MEDICAL_COLORS.VALID;
/** Rows in the history list before it scrolls. */
const HISTORY_VISIBLE = 3;
/** With the upload form open only this many history rows show; the rest scroll inside the list. */
const HISTORY_VISIBLE_COMPACT = 1;
/** Height of one history row, in px, including the gap under it. */
const HISTORY_ROW = 62;
const HISTORY_ROW_COMPACT = 54;

const todayIso = () => {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
};

const formatSize = (bytes: number) =>
  bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1).replace(".", ",")} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;

const panelSx = {
  p: 2,
  borderRadius: 3,
  border: "1px solid",
  borderColor: "divider",
} as const;

const actionSx = { textTransform: "none", fontWeight: 700, borderRadius: 2 } as const;

type Variant = "card" | "bare";

/** The outer frame: its own card on a page, nothing extra inside a dialog. */
const Frame = ({ variant, eqCard, compact = false, children }: { variant: Variant; eqCard: boolean; compact?: boolean; children: ReactNode }) =>
  variant === "bare" ? (
    <Box>{children}</Box>
  ) : (
    <Paper
      elevation={0}
      {...(eqCard ? { "data-eq-card": "" } : {})}
      sx={{ ...panelSx, p: compact ? 2 : { xs: 2, sm: 2.5 }, bgcolor: "background.paper", boxSizing: "border-box" }}
    >
      {children}
    </Paper>
  );

/**
 * The child's sport medical section, shared by the child profile, the club admin dialogs and the
 * player's own page. The server decides what each caller may see; this component only hides what
 * the response leaves out.
 */
export const MedicalSection = ({
  profileId: profileIdProp = "",
  viewer,
  hasTeams = true,
  hideWhenForbidden = false,
  variant = "card",
  eqCard = false,
  onFormVisibleChange,
  coachUpload = false,
  onSubmitted,
}: {
  /**
   * The player profile the certificate belongs to. The player's own view leaves it out: it reads
   * `/me/medical`, whose response carries the profile id for uploading and opening.
   */
  profileId?: string;
  /**
   * "manager": parent or club admin; the platform admin view shows nothing (it uses the break-glass dialog).
   * "player": the child's own account; read only without the upload right, full for an adult player.
   */
  viewer: "manager" | "player";
  hasTeams?: boolean;
  /** Club admin dialogs: a club that does not require the certificate sees nothing at all. */
  hideWhenForbidden?: boolean;
  /** "card": its own card on a page. "bare": inside a dialog that already has a frame. */
  variant?: Variant;
  /** Lets the page's equal-columns hook stretch this card. */
  eqCard?: boolean;
  /** Tells the page whether the upload form is showing, so it can lay out its other column. */
  onFormVisibleChange?: (visible: boolean) => void;
  /** The coach's upload dialog: its narrow state has no history, but the form still shows. */
  coachUpload?: boolean;
  /** Called after a saved submission, so a dialog around the section can close. */
  onSubmitted?: () => void;
}) => {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const { showSuccess, showError } = useSnackbar();
  const isPlayer = viewer === "player";
  // The platform admin has no medical view: the server refuses it, so the section does not even ask.
  const { activeView } = useView();
  const platformAdminView = !isPlayer && activeView === ROLES.ADMIN;
  const queryKey = isPlayer ? myMedicalQueryKey : medicalQueryKey(profileIdProp);
  const { data, error, isLoading } = useQuery<MedicalStatus>({
    queryKey,
    queryFn: () => (isPlayer ? getMyMedicalStatus() : getMedicalStatus(profileIdProp)),
    enabled: !platformAdminView && (isPlayer || profileIdProp.length > 0),
    retry: false,
  });
  const profileId = isPlayer ? data?.profileId ?? "" : profileIdProp;

  // The form starts open: leaving a new certificate is the main job here.
  const [formOpen, setFormOpen] = useState(true);
  const [result, setResult] = useState<MedicalResult | "">("");
  const [validUntil, setValidUntil] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [redactSource, setRedactSource] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [acknowledged, setAcknowledged] = useState(false);
  const [redacted, setRedacted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [replaceOpen, setReplaceOpen] = useState(false);
  const [adminAccessOpen, setAdminAccessOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);

  const canAct = profileId.length > 0;
  // The child's own account without the right reads its state but does not submit; an adult player needs no right.
  const readOnly = isPlayer && Boolean(data) && !data?.canUploadMedical && !data?.adult;
  const formVisible = Boolean(
    data?.required && data.history !== undefined && !readOnly && (formOpen || !data.certificateId),
  );
  useEffect(() => {
    onFormVisibleChange?.(formVisible);
  }, [formVisible, onFormVisibleChange]);

  // The parent saw the new state someone else entered: the unread mark on the child's card goes.
  const unseenCertificate = !isPlayer && data?.unseen ? data.certificateId : undefined;
  useEffect(() => {
    if (!unseenCertificate || !profileIdProp) return;
    void markMedicalSeen(profileIdProp, unseenCertificate)
      .then(() => queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY }))
      .catch(() => undefined);
  }, [unseenCertificate, profileIdProp, queryClient]);

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey }),
      queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: ["medical", "team"] }),
    ]);
  };

  /** A non-GET 403 is already shown by the global listener. */
  const reportError = (err: unknown, fallbackKey: string) => {
    const { status, detail } = problemDetail(err);
    if (status === 403) return;
    showError(detail || t(fallbackKey));
  };

  const onDrop = (accepted: File[], rejected: FileRejection[]) => {
    setFileError(null);
    const picked = accepted[0];
    if (!picked) {
      if (rejected.length > 0) setFileError(t("medical.form.file-type"));
      return;
    }
    if (picked.size === 0) {
      setFileError(t("medical.form.file-empty"));
      return;
    }
    if (picked.size > MEDICAL_MAX_BYTES) {
      setFileError(t("medical.form.file-size"));
      return;
    }
    // Where the club asks for the redacted copy, the original goes through the editor and is never uploaded.
    setRedacted(false);
    setRedactSource(picked);
  };

  const onRedacted = (redactedFile: File) => {
    setRedactSource(null);
    if (redactedFile.size > MEDICAL_MAX_BYTES) {
      setFileError(t("medical.form.file-size"));
      return;
    }
    setFile(redactedFile);
  };

  const { getRootProps, getInputProps, isDragActive, open: browse } = useDropzone({
    onDrop,
    multiple: false,
    maxFiles: 1,
    disabled: busy || !canAct,
    accept: {
      "application/pdf": [".pdf"],
      "image/jpeg": [".jpg", ".jpeg"],
      "image/png": [".png"],
    },
  });

  const resetForm = () => {
    setResult("");
    setValidUntil("");
    setFile(null);
    setFileError(null);
    setAcknowledged(false);
    setRedacted(false);
    setFormOpen(false);
  };

  const fileRequired = Boolean(data?.fileRequired);

  const doSubmit = async () => {
    if (!result || !resultComplete(result, validUntil) || !acknowledged || !canAct) return;
    if (fileRequired && (!file || !redacted)) return;
    if (file && fileRequired && !(MEDICAL_CONTENT_TYPES as readonly string[]).includes(file.type)) {
      setFileError(t("medical.form.file-type"));
      return;
    }
    setBusy(true);
    try {
      await submitMedicalCertificate(profileId, {
        result,
        validUntil: validUntil || undefined,
        file: fileRequired ? file ?? undefined : undefined,
      });
      showSuccess(t("medical.messages.saved"));
      resetForm();
      onSubmitted?.();
    } catch (err) {
      const { detail } = problemDetail(err);
      reportError(err, detail ? "medical.messages.failed" : "medical.messages.upload-failed");
    } finally {
      setBusy(false);
      await refresh();
    }
  };

  const doWithdraw = async () => {
    if (!data?.certificateId) return;
    setBusy(true);
    try {
      await withdrawMedicalCertificate(profileId, data.certificateId);
      setWithdrawOpen(false);
      showSuccess(t("medical.messages.withdrawn"));
      await refresh();
    } catch (err) {
      setWithdrawOpen(false);
      reportError(err, "medical.messages.failed");
    } finally {
      setBusy(false);
    }
  };

  const doOpen = async () => {
    if (!data?.certificateId || !canAct) return;
    try {
      await openMedicalFileInNewTab(profileId, data.certificateId);
    } catch (err) {
      reportError(err, "medical.messages.failed");
    }
  };

  const openNotice = async (notice: MedicalNoticeItem) => {
    // Opened before the request, so the browser does not treat the new tab as an unrequested pop-up.
    const tab = window.open("", "_blank");
    try {
      const { url } = await getClubMedicalNotice(notice.clubId);
      if (tab) {
        tab.opener = null;
        tab.location.href = url;
      } else {
        window.open(url, "_blank", "noopener");
      }
    } catch (err) {
      tab?.close();
      reportError(err, "medical.messages.failed");
    }
  };

  const doParentAccess = async (allowed: boolean) => {
    setBusy(true);
    try {
      await setMedicalParentAccess(allowed);
      showSuccess(t("medical.adult.saved"));
    } catch (err) {
      reportError(err, "medical.messages.failed");
    } finally {
      setBusy(false);
      await refresh();
    }
  };

  // The platform admin has no medical view: only the break-glass path, with a reason the club sees.
  if (platformAdminView) {
    if (!profileIdProp) return null;
    return (
      <Box>
        <Button size="small" variant="outlined" color="warning" startIcon={<MonitorHeartOutlinedIcon />}
          onClick={() => setAdminAccessOpen(true)} sx={actionSx}>
          {t("medical.admin.button")}
        </Button>
        {adminAccessOpen && (
          <MedicalAdminAccessDialog open profileId={profileIdProp} onClose={() => setAdminAccessOpen(false)} />
        )}
      </Box>
    );
  }
  if (isLoading) return null;
  if (error) {
    const { status } = problemDetail(error);
    if (status === 404 || (status === 403 && (hideWhenForbidden || isPlayer))) return null;
    return <Alert severity="error">{t("medical.messages.load-failed")}</Alert>;
  }
  if (!data) return null;

  const title = (
    <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
      <MonitorHeartOutlinedIcon sx={{ color: GREEN, fontSize: 22 }} />
      <Typography variant="h3" sx={{ fontWeight: 700, fontSize: "1.15rem" }}>{t("medical.title")}</Typography>
    </Stack>
  );

  if (!data.required) {
    if (isPlayer && !data.certificateId) return null;
    if (data.certificateId) {
      return (
        <Frame variant={variant} eqCard={eqCard}>
          <Stack spacing={1.5}>
            {title}
            <Alert severity="info">{t("medical.not-required.leftover")}</Alert>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <PrimaryButton size="small" startIcon={<OpenInNewIcon />} onClick={() => void doOpen()} disabled={!canAct}
                sx={{ py: 0.5 }}>
                {t("medical.actions.open")}
              </PrimaryButton>
              <Button color="error" variant="outlined" size="small" startIcon={<DeleteOutlineIcon />} disabled={busy}
                onClick={() => setWithdrawOpen(true)} sx={actionSx}>
                {t("medical.actions.withdraw")}
              </Button>
            </Stack>
          </Stack>
          <WithdrawDialog open={withdrawOpen} pending={busy} itemName="" onClose={() => setWithdrawOpen(false)} onConfirm={doWithdraw} />
        </Frame>
      );
    }
    return (
      <Frame variant={variant} eqCard={eqCard}>
        <Stack spacing={1}>
          {title}
          <Typography variant="body2" color="text.secondary">
            {hasTeams ? t("medical.not-required.club-off") : t("medical.not-required.no-team")}
          </Typography>
        </Stack>
      </Frame>
    );
  }

  const state = data.status ?? "MISSING";
  const color = MEDICAL_COLORS[state];
  const sentence = medicalSentence(t, i18n.language, state, data.validUntil, data.daysRemaining, data.result);
  const hasCurrent = Boolean(data.certificateId);
  const isManager = !isPlayer;
  // A coach's response has no history: dates only, no file, and the form only in the upload dialog.
  const datesOnly = data.history === undefined;
  const showForm = (!datesOnly || coachUpload) && !readOnly && (formOpen || !hasCurrent);
  // A coach without file access gets no certificate id, but the upload still replaces the row.
  const replacing = hasCurrent || (coachUpload && state !== "MISSING");
  // While the form is open the status shrinks, so the whole section fits a 1280×800 screen.
  const compact = showForm;
  // On a page card the compact status also takes the detail line, so the open form fits without scrolling.
  const tight = compact && variant === "card";
  const noDays = data.daysRemaining === undefined || data.daysRemaining === null;
  const ringNumber = noDays ? "–" : String(Math.abs(data.daysRemaining ?? 0));
  const ringUnit = state === "MISSING"
    ? t("medical.ring.none")
    : state === "NOT_FIT"
      ? t(`medical.result.${data.result ?? "NEM_ALKALMAS"}`)
      : state === "RESULT_MISSING"
        ? t("medical.ring.no-result")
        : state === "EXPIRED"
          ? t("medical.ring.ago")
          : data.daysRemaining === 0 ? t("medical.ring.today") : t("medical.ring.days");
  const submitter = formatPersonName(data.submittedByFirstName, data.submittedByLastName);
  const pastDate = Boolean(validUntil) && validUntil < todayIso() && (result === "VERSENYEZHET" || result === "ALKALMAS");
  const why = !result
    ? t("medical.form.why-result")
    : !resultComplete(result, validUntil)
      ? t("medical.form.why-date")
      : fileRequired && !file
        ? t("medical.form.why-file")
        : fileRequired && !redacted
          ? t("medical.form.why-redaction")
          : !acknowledged ? t("medical.form.why-ack") : "";
  const canSave = resultComplete(result, validUntil) && acknowledged && (!fileRequired || (Boolean(file) && redacted)) && !busy && canAct;
  const notices = data.notices ?? [];
  const clubs = notices.map((notice) => notice.clubName).filter(Boolean).join(", ");
  const laws = notices.map((notice) => notice.legalBasis).filter(Boolean).join("; ");
  const adultPlayer = isPlayer && Boolean(data.adult);
  const parentsShown = !adultPlayer || Boolean(data.parentAccessAllowed);
  const canWithdraw = isManager || adultPlayer;
  const ackText = isPlayer
    ? t(fileRequired ? "medical.ack.player-file" : "medical.ack.player-date", {
        parents: parentsShown ? t("medical.ack.player-parents") : "",
        parentsAsk: parentsShown ? t("medical.ack.player-parents-ask") : "",
      })
    : coachUpload
      ? t("medical.ack.coach-file")
      : t(fileRequired ? "medical.ack.file" : "medical.ack.date", { club: clubs || "–", law: laws || "–" });
  const verifier = formatPersonName(data.verifiedByFirstName, data.verifiedByLastName);
  const verifierText = [verifier || null, data.verifiedByRole ? `(${t(`medical.roles.${data.verifiedByRole}`)})` : null]
    .filter(Boolean).join(" ");
  const enteredInPerson = data.submittedByRole === "COACH" || (data.submittedByRole === "CLUB_ADMIN" && data.verificationMethod === "ORIGINAL"
    && data.verifiedAt === data.submittedAt);
  const positive = data.result === "VERSENYEZHET" || data.result === "ALKALMAS";
  const history = data.history ?? [];
  const ringSize = compact ? 62 : { xs: 76, sm: 88 };
  const historyVisible = compact ? HISTORY_VISIBLE_COMPACT : HISTORY_VISIBLE;
  const historyRow = compact ? HISTORY_ROW_COMPACT : HISTORY_ROW;
  const submittedBy = [submitter || null, data.submittedByRole ? `(${t(`medical.roles.${data.submittedByRole}`)})` : null]
    .filter(Boolean).join(" ");

  return (
    <Frame variant={variant} eqCard={eqCard} compact={compact}>
      <Stack spacing={compact ? 1.25 : 2.25}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" flexWrap="wrap" useFlexGap spacing={1}>
          <Stack direction="row" spacing={1.25} alignItems="center">
            {title}
            <Chip
              size="small"
              icon={<Box component="span" aria-hidden sx={{ width: 7, height: 7, borderRadius: "50%", bgcolor: color, ml: "8px !important" }} />}
              label={t(`medical.badge.${state}`)}
              sx={{ fontWeight: 700, color, bgcolor: `${color}1a`, border: "1px solid", borderColor: `${color}55` }}
            />
          </Stack>
          {!datesOnly && hasCurrent && (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {data.hasFile && (
                <PrimaryButton size="small" startIcon={<OpenInNewIcon />} onClick={() => void doOpen()} disabled={!canAct}
                  sx={{ py: 0.5 }}>
                  {t("medical.actions.open")}
                </PrimaryButton>
              )}
              {!showForm && !readOnly && (
                <Button size="small" variant="outlined" color="inherit" startIcon={<AddIcon />} onClick={() => setFormOpen(true)}
                  sx={{ ...actionSx, borderColor: "divider" }}>
                  {t("medical.actions.new")}
                </Button>
              )}
              {canWithdraw && (tight ? (
                // Icon only while the form is open, so the header stays on one line.
                <Tooltip title={t("medical.actions.withdraw")}>
                  <span>
                    <IconButton size="small" color="error" aria-label={t("medical.actions.withdraw")} disabled={busy}
                      onClick={() => setWithdrawOpen(true)}
                      sx={{ border: "1px solid", borderColor: "rgba(239, 68, 68, 0.5)", borderRadius: 2, p: "5px" }}>
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
              ) : (
                <Button color="error" variant="outlined" size="small" startIcon={<DeleteOutlineIcon />} disabled={busy}
                  onClick={() => setWithdrawOpen(true)} sx={actionSx}>
                  {t("medical.actions.withdraw")}
                </Button>
              ))}
            </Stack>
          )}
        </Stack>

        {variant === "bare" ? (
          <Stack direction="row" spacing={1.25} alignItems="center"
            sx={{ px: 1.75, py: 1.25, borderRadius: 2.5, border: "1px solid", borderColor: `${color}40`, borderLeft: `4px solid ${color}`, bgcolor: `${color}0d` }}>
            <MedicalStateIcon state={state} sx={{ color, fontSize: 20, flexShrink: 0 }} />
            <Box sx={{ minWidth: 0 }}>
              <Typography sx={{ fontWeight: 700 }}>{sentence}</Typography>
              {state !== "VALID" && (
                <Typography variant="caption" color="text.secondary">{t("medical.note")}</Typography>
              )}
            </Box>
          </Stack>
        ) : (
          <Box sx={{ display: "flex", alignItems: "center", gap: compact ? 1.5 : 2.5, p: compact ? 1.25 : 2, borderRadius: 3, border: "1px solid", borderColor: `${color}40`, bgcolor: `${color}0d` }}>
            <Box
              aria-hidden
              sx={{
                width: ringSize,
                height: ringSize,
                borderRadius: "50%",
                border: "3px solid",
                borderColor: color,
                bgcolor: `${color}14`,
                boxShadow: compact ? "none" : `0 0 24px ${color}33`,
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
                transition: "width 0.2s ease, height 0.2s ease",
              }}
            >
              <Typography sx={{ fontSize: compact ? "1.2rem" : { xs: "1.5rem", sm: "1.8rem" }, fontWeight: 800, lineHeight: 1, fontVariantNumeric: "tabular-nums" }}>
                {ringNumber}
              </Typography>
              <Typography color="text.secondary" sx={{ fontSize: compact ? "0.6rem" : "0.7rem", lineHeight: 1.2, textAlign: "center", px: 0.5 }}>
                {ringUnit}
              </Typography>
            </Box>
            <Box sx={{ minWidth: 0 }}>
              <Typography sx={{ fontSize: compact ? "1rem" : "1.1rem", fontWeight: 700 }}>{sentence}</Typography>
              {state !== "VALID" && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: compact ? 0.25 : 0.5, fontSize: compact ? "0.78rem" : undefined, lineHeight: compact ? 1.4 : undefined }}>
                  {t("medical.note")}
                </Typography>
              )}
              {tight && !datesOnly && hasCurrent && (
                <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.25, lineHeight: 1.4 }}>
                  {t("medical.details.submitted")}:{" "}
                  <Box component="span" sx={{ color: "text.primary", fontWeight: 700 }}>{submittedBy}</Box>
                  {data.submittedAt ? `, ${formatInstant(data.submittedAt, i18n.language)}` : ""}
                  {data.hasFile && (
                    <Box component="span" sx={{ whiteSpace: "nowrap" }}>
                      {" · "}
                      <InsertDriveFileOutlinedIcon sx={{ fontSize: 13, verticalAlign: "-2px", mr: 0.25 }} />
                      {t("medical.details.has-file")}
                    </Box>
                  )}
                </Typography>
              )}
            </Box>
          </Box>
        )}

        {!datesOnly && !hasCurrent && data.rejectionReason && (
          <Alert severity="error">
            {t("medical.review.rejected-notice", {
              role: t(`roles.${(data.rejectedByRole ?? "").toLowerCase()}`).toLowerCase(),
              date: formatInstant(data.rejectedAt, i18n.language),
              reason: t(`medical.review.reason.${data.rejectionReason}`),
            })}
          </Alert>
        )}

        {!datesOnly && hasCurrent && !tight && (
          <Box sx={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 1.5, px: 0.5 }}>
            <Box>
              <Typography variant="caption" color="text.secondary">{t("medical.details.result")}</Typography>
              <Typography variant="body2" fontWeight={700}>
                {data.result ? t(`medical.result.${data.result}`) : t("medical.details.no-result")}
                {data.validUntil ? ` · ${formatLongDate(data.validUntil, i18n.language)}` : ""}
              </Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">{t("medical.details.submitted")}</Typography>
              <Typography variant="body2" fontWeight={700}>{submittedBy}</Typography>
              {data.submittedAt && (
                <Typography variant="caption" color="text.secondary">{formatInstant(data.submittedAt, i18n.language)}</Typography>
              )}
            </Box>
            {data.hasFile && (
              <Box>
                <Typography variant="caption" color="text.secondary">{t("medical.details.file")}</Typography>
                <Stack direction="row" spacing={0.75} alignItems="center">
                  <InsertDriveFileOutlinedIcon sx={{ fontSize: 16, color: "text.secondary" }} />
                  <Typography variant="body2" fontWeight={700}>{t("medical.details.has-file")}</Typography>
                </Stack>
              </Box>
            )}
          </Box>
        )}

        {!datesOnly && hasCurrent && (
          <Stack spacing={0.5} sx={{ px: 0.5 }}>
            {data.result === "ALKALMAS" && positive && (
              <Typography variant="body2" sx={{ color: "warning.main", fontWeight: 700 }}>{t("medical.form.alkalmas-warning")}</Typography>
            )}
            {enteredInPerson && (
              <Typography variant="body2">
                {t("medical.verify.entered-in-person", {
                  name: submittedBy || "–",
                  date: beforeSuffix(formatInstant(data.submittedAt, i18n.language)),
                })}
              </Typography>
            )}
            {!enteredInPerson && (data.verified ? (
              <Typography variant="body2">
                {t(data.verificationMethod === "FILE" ? "medical.verify.done-file" : "medical.verify.done-original", {
                  name: verifierText || "–",
                  date: beforeSuffix(formatInstant(data.verifiedAt, i18n.language)),
                })}
              </Typography>
            ) : positive && (
              <Typography variant="body2" sx={{ color: MEDICAL_COLORS.UNVERIFIED, fontWeight: 700 }}>
                {t("medical.verify.waiting")}
              </Typography>
            ))}
            {data.dateCorrectedAt && (
              <Typography variant="body2" sx={{ color: "warning.main", fontWeight: 700 }}>
                {t("medical.review.date-corrected-notice", {
                  role: t(`roles.${(data.dateCorrectedByRole ?? "").toLowerCase()}`).toLowerCase(),
                  date: formatInstant(data.dateCorrectedAt, i18n.language),
                  original: beforeSuffix(formatLongDate(data.originalValidUntil, i18n.language)),
                })}
              </Typography>
            )}
            {!data.verified && positive && !fileRequired && (
              <Typography variant="caption" color="text.secondary">{t("medical.verify.show-original")}</Typography>
            )}
            {fileRequired && data.coachFileAccess !== undefined && (
              <Typography variant="caption" color="text.secondary">
                {t(data.coachFileAccess ? "medical.coach-access.on" : "medical.coach-access.off")}
              </Typography>
            )}
            {data.fileRequestedNext && (
              <Typography variant="caption" color="text.secondary">{t("medical.form.file-requested-next")}</Typography>
            )}
          </Stack>
        )}

        {readOnly && (
          <Typography variant="body2" color="text.secondary" sx={{ px: 0.5 }}>{t("medical.player.read-only")}</Typography>
        )}

        {adultPlayer && (
          <Stack direction="row" spacing={1.5} alignItems="center" sx={{ ...panelSx, py: 1.25 }}>
            <Switch
              checked={Boolean(data.parentAccessAllowed)}
              disabled={busy}
              onChange={(event) => void doParentAccess(event.target.checked)}
              slotProps={{ input: { "aria-label": t("medical.adult.parent-access") } }}
              color="success"
            />
            <Typography variant="body2" fontWeight={700}>{t("medical.adult.parent-access")}</Typography>
          </Stack>
        )}

        {(data.adminAccesses ?? []).length > 0 && (
          <Alert severity="info">
            <Typography variant="body2" fontWeight={700}>{t("medical.admin.shown-title")}</Typography>
            {(data.adminAccesses ?? []).map((access, index) => (
              <Typography key={`${access.createdAt}-${index}`} variant="caption" sx={{ display: "block" }}>
                {formatInstant(access.createdAt, i18n.language)} · {t(`medical.admin.action.${access.action}`)} · {access.reason}
              </Typography>
            ))}
          </Alert>
        )}

        {showForm && (
          <Box sx={{ ...panelSx, p: 1.5 }}>
            <input {...getInputProps()} aria-label={t("medical.form.drop-label")} />
            <Stack spacing={1}>
              <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="body2" fontWeight={700} sx={{ fontSize: "0.95rem" }}>
                    {replacing ? t("medical.form.title-new") : t("medical.form.title-first")}
                  </Typography>
                  {replacing && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", lineHeight: 1.3 }}>{t("medical.form.replaces")}</Typography>
                  )}
                </Box>
                {hasCurrent && (
                  <SecondaryButton onClick={resetForm} disabled={busy}>{t("medical.actions.cancel")}</SecondaryButton>
                )}
              </Stack>

              <MedicalDataRule />
              <MedicalResultFields
                result={result}
                validUntil={validUntil}
                disabled={busy}
                onResultChange={setResult}
                onValidUntilChange={setValidUntil}
              />
              {pastDate && (
                <Typography variant="caption" sx={{ color: "warning.main" }}>{t("medical.form.past-warning")}</Typography>
              )}

              {!fileRequired ? null : file ? (
                <Stack direction="row" spacing={1.5} alignItems="center" sx={{ ...panelSx, py: 1.25, borderColor: `${GREEN}55`, bgcolor: `${GREEN}0a` }}>
                  <InsertDriveFileOutlinedIcon sx={{ color: GREEN }} />
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography variant="body2" fontWeight={700} noWrap>{file.name}</Typography>
                    <Typography variant="caption" color="text.secondary">{formatSize(file.size)}</Typography>
                  </Box>
                  <Button size="small" color="inherit" startIcon={<AutorenewIcon />} onClick={browse} disabled={busy}
                    sx={{ ...actionSx, color: "text.secondary" }}>
                    {t("medical.form.change-file")}
                  </Button>
                  <IconButton size="small" aria-label={t("medical.form.remove-file")} onClick={() => setFile(null)} disabled={busy}>
                    <CloseIcon fontSize="small" />
                  </IconButton>
                </Stack>
              ) : (
                <Box
                  {...getRootProps()}
                  role="button"
                  tabIndex={0}
                  aria-label={t("medical.form.drop-label")}
                  sx={{
                    // About 110 px: still plainly a drop target, with the upload arrow.
                    minHeight: 110,
                    boxSizing: "border-box",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    py: 1.5,
                    px: 2,
                    borderRadius: 3,
                    border: "2px dashed",
                    borderColor: isDragActive ? GREEN : `${GREEN}73`,
                    bgcolor: isDragActive ? `${GREEN}14` : `${GREEN}08`,
                    textAlign: "center",
                    cursor: canAct ? "pointer" : "not-allowed",
                    opacity: canAct ? 1 : 0.6,
                    transition: "background-color 0.2s ease, border-color 0.2s ease",
                    "&:hover": { bgcolor: `${GREEN}12`, borderColor: GREEN },
                    "&:focus-visible": { outline: `2px solid ${GREEN}`, outlineOffset: 2 },
                  }}
                >
                  <Box sx={{
                    width: 38,
                    height: 38,
                    mx: "auto",
                    mb: 0.75,
                    borderRadius: "50%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    bgcolor: `${GREEN}1f`,
                    color: GREEN,
                  }}>
                    <FileUploadOutlinedIcon sx={{ fontSize: 24 }} />
                  </Box>
                  <Typography variant="body2" fontWeight={700}>
                    {t("medical.form.drop")}{" "}
                    <Box component="span" sx={{ color: GREEN, textDecoration: "underline", textUnderlineOffset: 3 }}>
                      {t("medical.form.browse")}
                    </Box>
                  </Typography>
                  <Typography variant="caption" color="text.secondary">{t("medical.form.drop-hint")}</Typography>
                  <Typography variant="caption" fontWeight={700} sx={{ color: GREEN, mt: 0.5 }}>{t("medical.redact.upload-note")}</Typography>
                </Box>
              )}
              {fileError && <Alert severity="error" onClose={() => setFileError(null)}>{fileError}</Alert>}
              {redactSource && (
                <MedicalRedactDialog file={redactSource} onDone={onRedacted} onCancel={() => setRedactSource(null)} />
              )}

              {fileRequired && (
                <Stack direction="row" spacing={1.5} alignItems="flex-start"
                  sx={{ p: 1, borderRadius: 2, border: "1px solid", borderColor: "warning.main", bgcolor: "action.hover" }}>
                  <Box component="img" src="/legal/kitakaras-minta.png" alt={t("medical.redaction.sample-alt")}
                    sx={{ width: 96, borderRadius: 1, flexShrink: 0, display: { xs: "none", sm: "block" } }} />
                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 0.5 }}>{t("medical.redaction.explain")}</Typography>
                    <FormControlLabel
                      sx={{ alignItems: "flex-start", m: 0 }}
                      control={<Checkbox size="small" checked={redacted} onChange={(event) => setRedacted(event.target.checked)} sx={{ mt: -0.5, mr: 0.25 }} color="success" />}
                      label={<Typography variant="caption" sx={{ display: "block", fontSize: "0.7rem", lineHeight: 1.35 }}>{t("medical.redaction.confirm")}</Typography>}
                    />
                  </Box>
                </Stack>
              )}

              <FormControlLabel
                sx={{ alignItems: "flex-start", m: 0, py: 0.75, pl: 0.5, pr: 1.25, borderRadius: 2, border: "1px solid", borderColor: "divider", bgcolor: "action.hover" }}
                control={<Checkbox size="small" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} sx={{ mt: -0.5, mr: 0.25 }} color="success" />}
                label={<Typography variant="caption" sx={{ display: "block", fontSize: "0.7rem", lineHeight: 1.35 }}>{ackText}</Typography>}
              />
              {notices.length > 0 && (
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {notices.filter((notice) => notice.noticeKind).map((notice) => (
                    <Button key={notice.clubId} size="small" color="inherit" startIcon={<OpenInNewIcon />} onClick={() => void openNotice(notice)}
                      sx={{ ...actionSx, color: "text.secondary" }}>
                      {notices.length > 1 ? t("medical.notice.link-club", { club: notice.clubName ?? "" }) : t("medical.notice.link")}
                    </Button>
                  ))}
                </Stack>
              )}

              <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
                <Typography variant="caption" color="text.secondary">
                  {busy ? t("medical.actions.uploading") : !canAct ? t("medical.form.no-profile") : why}
                </Typography>
                <PrimaryButton
                  disabled={!canSave}
                  onClick={() => (replacing ? setReplaceOpen(true) : void doSubmit())}
                  sx={{ py: 0.5, flexShrink: 0 }}
                >
                  {busy ? t(fileRequired ? "medical.actions.uploading" : "medical.actions.saving") : t("medical.actions.save")}
                </PrimaryButton>
              </Stack>
            </Stack>
          </Box>
        )}

        {!datesOnly && (
          <Box>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: compact ? 0.75 : 1 }}>
              <HistoryIcon sx={{ fontSize: 18, color: "text.secondary" }} />
              <Typography variant="body2" fontWeight={700}>{t("medical.history.title")}</Typography>
              {history.length > 0 && <Chip size="small" label={history.length} sx={{ height: 20 }} />}
            </Stack>
            {history.length === 0 ? (
              <Typography variant="body2" color="text.secondary">{t("medical.history.empty")}</Typography>
            ) : (
              <Stack spacing={0.75} sx={{
                // Three rows stay visible (one while the form is open); more rows scroll inside the list.
                maxHeight: history.length > historyVisible ? historyVisible * historyRow : undefined,
                overflowY: history.length > historyVisible ? "auto" : "visible",
                pr: history.length > historyVisible ? 0.5 : 0,
              }}>
                {history.map((item, index) => {
                  const by = formatPersonName(item.submittedByFirstName, item.submittedByLastName);
                  const closedAt = item.withdrawnAt ?? item.supersededAt;
                  return (
                    <Stack key={`${item.submittedAt ?? item.validUntil}-${index}`} direction="row" spacing={1} alignItems="center"
                      sx={{ ...panelSx, py: compact ? 0.75 : 1, px: 1.5, flexShrink: 0 }}>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography variant="body2" fontWeight={700}>{historyValidLabel(t, i18n.language, item.validUntil, item.result)}</Typography>
                        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: "block" }}>
                          {t("medical.history.by", {
                            name: by || (item.submittedByRole ? t(`medical.roles.${item.submittedByRole}`) : "–"),
                            date: formatInstant(item.submittedAt, i18n.language),
                          })}
                        </Typography>
                      </Box>
                      {closedAt && (
                        <Chip
                          size="small"
                          variant="outlined"
                          label={t(item.rejectedAt ? "medical.history.rejected" : item.withdrawnAt ? "medical.history.withdrawn" : "medical.history.superseded", {
                            date: formatInstant(closedAt, i18n.language),
                          })}
                          sx={{
                            flexShrink: 0,
                            color: item.withdrawnAt ? "error.light" : "text.secondary",
                            borderColor: item.withdrawnAt ? "rgba(239, 68, 68, 0.4)" : "divider",
                          }}
                        />
                      )}
                    </Stack>
                  );
                })}
              </Stack>
            )}
          </Box>
        )}
      </Stack>

      <Dialog open={replaceOpen} onClose={() => setReplaceOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{t("medical.replace.title")}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>{t("medical.replace.body")}</Typography>
          <Stack spacing={0.75} sx={{ ...panelSx, py: 1.25 }}>
            <Typography variant="body2">
              <Box component="span" sx={{ color: "text.secondary", mr: 1 }}>{t("medical.replace.now")}</Box>
              <Box component="span" sx={{ fontWeight: 700 }}>{sentence}</Box>
            </Typography>
            <Typography variant="body2">
              <Box component="span" sx={{ color: "text.secondary", mr: 1 }}>{t("medical.replace.next")}</Box>
              <Box component="span" sx={{ fontWeight: 700, color: GREEN }}>
                {[result ? t(`medical.result.${result}`) : "", formatLongDate(validUntil, i18n.language)].filter(Boolean).join(" · ")}
              </Box>
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <SecondaryButton onClick={() => setReplaceOpen(false)}>{t("common.cancel")}</SecondaryButton>
          <PrimaryButton onClick={() => { setReplaceOpen(false); void doSubmit(); }} sx={{ py: 0.75 }}>
            {t("medical.replace.confirm")}
          </PrimaryButton>
        </DialogActions>
      </Dialog>
      <WithdrawDialog open={withdrawOpen} pending={busy} itemName={sentence} onClose={() => setWithdrawOpen(false)} onConfirm={doWithdraw} />
    </Frame>
  );
};

const WithdrawDialog = ({
  open,
  pending,
  itemName,
  onClose,
  onConfirm,
}: {
  open: boolean;
  pending: boolean;
  itemName: string;
  onClose: () => void;
  onConfirm: () => void;
}) => {
  const { t } = useTranslation();
  return (
    <HoldToDeleteDialog
      open={open}
      title={t("medical.withdraw.title")}
      itemName={itemName}
      description={t("medical.withdraw.body")}
      pending={pending}
      onClose={onClose}
      onConfirm={onConfirm}
    />
  );
};
