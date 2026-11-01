import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import EditCalendarIcon from "@mui/icons-material/EditCalendar";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import ReportProblemOutlinedIcon from "@mui/icons-material/ReportProblemOutlined";
import { useTranslation } from "react-i18next";
import {
  MEDICAL_RESULTS,
  correctMedicalDate,
  medicalAdminAccess,
  medicalAdminWithdrawal,
  openMedicalFile,
  rejectMedical,
  resultHasDate,
  submitMedicalInPerson,
  verifyMedical,
  type MedicalAdminAccessResponse,
  type MedicalRejectionReason,
  type MedicalResult,
  type MedicalVerificationMethod,
} from "../../api/medical";
import { MY_CHILDREN_QUERY_KEY } from "../../context/ChildrenContext";
import { useSnackbar } from "../../context/SnackbarContext";
import { PrimaryButton } from "../ui/PrimaryButton";
import { SecondaryButton } from "../ui/SecondaryButton";
import { beforeSuffix, dateLabelKey, formatInstant, formatLongDate, problemDetail } from "./medicalFormat";

const dateFieldSx = {
  width: 190,
  "& input::-webkit-calendar-picker-indicator": { cursor: "pointer", opacity: 0.7 },
  "& input": { colorScheme: (theme: { palette: { mode: string } }) => theme.palette.mode },
} as const;

/** Refreshes every sport medical view after a change. */
export const useMedicalRefresh = () => {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["medical"] }),
      queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY }),
    ]);
};

/**
 * Opens the current file in a new tab. The tab is opened before the request, so the browser does not treat it as an
 * unrequested pop-up. The server writes the OPENED audit row, which a FILE verification needs.
 */
export const openMedicalFileInNewTab = async (profileId: string, certificateId: string) => {
  const tab = window.open("", "_blank");
  try {
    const { url } = await openMedicalFile(profileId, certificateId);
    if (tab) {
      tab.opener = null;
      tab.location.href = url;
    } else {
      window.open(url, "_blank", "noopener");
    }
  } catch (err) {
    tab?.close();
    throw err;
  }
};

/** "Eredmény a papíron" and the date that follows it: the end of validity, the end of the suspension, or none. */
export const MedicalResultFields = ({
  result,
  validUntil,
  disabled,
  onResultChange,
  onValidUntilChange,
}: {
  result: MedicalResult | "";
  validUntil: string;
  disabled?: boolean;
  onResultChange: (value: MedicalResult) => void;
  onValidUntilChange: (value: string) => void;
}) => {
  const { t } = useTranslation();
  return (
    <Stack spacing={1}>
      <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
        <TextField
          select
          size="small"
          required
          label={t("medical.form.result")}
          value={result}
          disabled={disabled}
          onChange={(event) => {
            const next = event.target.value as MedicalResult;
            onResultChange(next);
            if (!resultHasDate(next)) onValidUntilChange("");
          }}
          sx={{ minWidth: 220 }}
        >
          {MEDICAL_RESULTS.map((value) => (
            <MenuItem key={value} value={value}>{t(`medical.result.${value}`)}</MenuItem>
          ))}
        </TextField>
        {resultHasDate(result) && (
          <TextField
            type="date"
            size="small"
            required
            label={t(dateLabelKey(result))}
            value={validUntil}
            disabled={disabled}
            onChange={(event) => onValidUntilChange(event.target.value)}
            sx={dateFieldSx}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        )}
      </Stack>
      {result === "ALKALMAS" && (
        <Typography variant="caption" sx={{ color: "warning.main" }}>{t("medical.form.alkalmas-warning")}</Typography>
      )}
    </Stack>
  );
};

/** Whether the chosen result and date are complete. */
export const resultComplete = (result: MedicalResult | "", validUntil: string) =>
  Boolean(result) && (!resultHasDate(result) || Boolean(validUntil));

const reviewButtonSx = { textTransform: "none", fontWeight: 700, borderRadius: 2, color: "#fff", py: 1 } as const;

const DATA_RULE_ITEMS = ["person", "sport", "date", "result", "validity"] as const;

/** What the decree lets the licence hold, highlighted wherever someone enters or checks the data. */
export const MedicalDataRule = () => {
  const { t } = useTranslation();
  return (
    <Alert severity="info" icon={false} sx={{ borderLeft: "4px solid", borderColor: "info.main" }}>
      <Typography variant="body2" fontWeight={700}>{t("medical.data-rule.title")}</Typography>
      <Typography variant="caption" sx={{ display: "block", mt: 0.5 }}>{t("medical.data-rule.intro")}</Typography>
      <Box component="ul" sx={{ m: 0, mt: 0.5, pl: 2.25 }}>
        {DATA_RULE_ITEMS.map((item) => (
          <Typography key={item} component="li" variant="caption">{t(`medical.data-rule.items.${item}`)}</Typography>
        ))}
      </Box>
      <Typography variant="caption" fontWeight={700} sx={{ display: "block", mt: 0.5 }}>{t("medical.data-rule.important")}</Typography>
    </Alert>
  );
};

const FILE_REJECTION_REASONS: readonly MedicalRejectionReason[] = ["ILLEGIBLE", "NOT_REDACTED", "WRONG_PERSON", "RESULT_MISMATCH", "OTHER"];
const PAPER_REJECTION_REASONS: readonly MedicalRejectionReason[] = ["WRONG_PERSON", "RESULT_MISMATCH", "OTHER"];

/**
 * "Ellenőrzés": the coach or the club admin confirms that the result and the date on the screen match the paper
 * (ORIGINAL) or the uploaded file (FILE), corrects a mistyped date from it, or rejects the row with a reason. For FILE
 * the dialog shows the file beside the entered data, which is the opening the server asks for: it accepts these only
 * after the caller's own opening within 30 minutes.
 */
export const MedicalVerifyDialog = ({
  open,
  profileId,
  name,
  certificateId,
  result,
  validUntil,
  method,
  onClose,
}: {
  open: boolean;
  profileId: string;
  name: string;
  certificateId?: string;
  result?: MedicalResult;
  validUntil?: string;
  method: MedicalVerificationMethod;
  onClose: () => void;
}) => {
  const { t, i18n } = useTranslation();
  const { showSuccess, showError } = useSnackbar();
  const refresh = useMedicalRefresh();
  const [checked, setChecked] = useState(false);
  const [opened, setOpened] = useState(false);
  const [busy, setBusy] = useState(false);
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [fileFailed, setFileFailed] = useState(false);
  const [mode, setMode] = useState<"verify" | "date" | "reject">("verify");
  const [correctedDate, setCorrectedDate] = useState("");
  const [reason, setReason] = useState<MedicalRejectionReason | "">("");
  // Every request writes an access row, so one dialog opening asks for the file once.
  const fileRequested = useRef(false);
  const showsFile = method === "FILE";

  useEffect(() => {
    if (!open || !showsFile || !certificateId || fileRequested.current) return;
    fileRequested.current = true;
    openMedicalFile(profileId, certificateId)
      .then(({ url }) => {
        setFileUrl(url);
        setOpened(true);
      })
      .catch(() => setFileFailed(true));
  }, [open, showsFile, profileId, certificateId]);

  const resultLabel = result ? t(`medical.result.${result}`) : "";
  const dateText = validUntil ? t("medical.verify.until", { date: beforeSuffix(formatLongDate(validUntil, i18n.language)) }) : "";
  const text = method === "FILE"
    ? t("medical.verify.file-text", { result: resultLabel, until: dateText })
    : t("medical.verify.original-text", { name, result: resultLabel, until: dateText });

  const canCorrectDate = Boolean(result) && resultHasDate(result ?? "") && Boolean(validUntil);
  const reasons = showsFile ? FILE_REJECTION_REASONS : PAPER_REJECTION_REASONS;

  const close = () => {
    setChecked(false);
    setOpened(false);
    onClose();
  };

  const switchMode = (next: "verify" | "date" | "reject") => {
    setMode(next);
    setChecked(false);
  };

  const openFile = async () => {
    if (!certificateId) return;
    try {
      await openMedicalFileInNewTab(profileId, certificateId);
      setOpened(true);
    } catch (err) {
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    }
  };

  const save = async () => {
    if (!result) return;
    setBusy(true);
    try {
      if (mode === "reject") {
        if (!reason) return;
        await rejectMedical(profileId, { result, validUntil, method, reason });
        showSuccess(t("medical.review.rejected"));
      } else if (mode === "date") {
        await correctMedicalDate(profileId, { result, validUntil, method, correctedValidUntil: correctedDate });
        showSuccess(t("medical.review.date-saved"));
      } else {
        await verifyMedical(profileId, { result, validUntil, method });
        showSuccess(t("medical.verify.saved"));
      }
      await refresh();
      close();
    } catch (err) {
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
      if (status === 409) await refresh();
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={close} maxWidth={showsFile ? "lg" : "xs"} fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>{t("medical.verify.title", { name })}</DialogTitle>
      <DialogContent>
        <Stack direction={{ xs: "column", md: "row" }} spacing={2} alignItems="stretch">
          {showsFile && (
            <Box sx={{ flex: 2, minWidth: 0 }}>
              {fileUrl ? (
                <Box component="iframe" src={fileUrl} title={t("medical.verify.preview-title")} referrerPolicy="no-referrer"
                  sx={{ display: "block", width: "100%", height: { xs: "50vh", md: "72vh" }, border: "1px solid", borderColor: "divider", borderRadius: 2, bgcolor: "#fff" }} />
              ) : fileFailed ? (
                <Alert severity="warning">{t("medical.verify.preview-failed")}</Alert>
              ) : (
                <Typography variant="body2" color="text.secondary">{t("common.loading")}</Typography>
              )}
            </Box>
          )}
          <Stack spacing={1.5} sx={{ flex: 1, minWidth: 0 }}>
            <MedicalDataRule />
            {result && (
              <Box sx={{ p: 1.25, borderRadius: 2, bgcolor: "action.hover" }}>
                <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>{t("medical.verify.result-label")}</Typography>
                <Typography variant="body2" fontWeight={700}>{resultLabel}</Typography>
                {validUntil && (
                  <>
                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.75 }}>{t(dateLabelKey(result))}</Typography>
                    <Typography variant="body2" fontWeight={700}>{formatLongDate(validUntil, i18n.language)}</Typography>
                  </>
                )}
              </Box>
            )}
            {mode === "date" && (
              <TextField
                type="date"
                size="small"
                required
                label={t("medical.review.date-label")}
                value={correctedDate}
                onChange={(event) => setCorrectedDate(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            )}
            {mode === "reject" ? (
              <>
                <TextField
                  select
                  size="small"
                  required
                  label={t("medical.review.reason-label")}
                  value={reason}
                  onChange={(event) => setReason(event.target.value as MedicalRejectionReason)}
                >
                  {reasons.map((name) => (
                    <MenuItem key={name} value={name}>{t(`medical.review.reason.${name}`)}</MenuItem>
                  ))}
                </TextField>
                <Alert severity="warning">{t(showsFile ? "medical.review.reject-warning-file" : "medical.review.reject-warning-paper")}</Alert>
              </>
            ) : (
              <FormControlLabel
                sx={{ alignItems: "flex-start", m: 0 }}
                control={<Checkbox checked={checked} onChange={(event) => setChecked(event.target.checked)} color="success" sx={{ mt: -0.75 }} />}
                label={<Typography variant="body2">{mode === "date" ? t("medical.review.date-confirm") : text}</Typography>}
              />
            )}
            {mode === "verify" ? (
              <Stack spacing={1}>
                {canCorrectDate && (
                  <Button fullWidth variant="contained" startIcon={<EditCalendarIcon />} onClick={() => switchMode("date")}
                    sx={{ ...reviewButtonSx, bgcolor: "#d97706", "&:hover": { bgcolor: "#b45309" } }}>
                    {t("medical.review.date-problem")}
                  </Button>
                )}
                <Button fullWidth variant="contained" startIcon={<ReportProblemOutlinedIcon />} onClick={() => switchMode("reject")}
                  sx={{ ...reviewButtonSx, bgcolor: "#dc2626", "&:hover": { bgcolor: "#b91c1c" } }}>
                  {t(showsFile ? "medical.review.file-problem" : "medical.review.paper-problem")}
                </Button>
              </Stack>
            ) : (
              <Box>
                <Button size="small" color="inherit" onClick={() => switchMode("verify")} sx={{ textTransform: "none", fontWeight: 700 }}>
                  {t("medical.review.back")}
                </Button>
              </Box>
            )}
            {showsFile && (
              <Box>
                <Button variant="outlined" size="small" startIcon={<OpenInNewIcon />} onClick={() => void openFile()}
                  sx={{ textTransform: "none", fontWeight: 700, borderRadius: 2 }}>
                  {t("medical.verify.open-file")}
                </Button>
                {!opened && (
                  <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.5 }}>
                    {t("medical.verify.open-first")}
                  </Typography>
                )}
              </Box>
            )}
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <SecondaryButton onClick={close}>{t("common.cancel")}</SecondaryButton>
        {mode === "reject" ? (
          <Button variant="contained" color="error" disabled={!reason || busy || (method === "FILE" && !opened)} onClick={() => void save()}
            sx={{ textTransform: "none", fontWeight: 700, borderRadius: 2 }}>
            {t("medical.review.reject-confirm")}
          </Button>
        ) : (
          <PrimaryButton
            disabled={!checked || busy || (method === "FILE" && !opened) || (mode === "date" && !correctedDate)}
            onClick={() => void save()}
            sx={{ py: 0.75 }}
          >
            {t(mode === "date" ? "medical.review.date-confirm-button" : "medical.verify.confirm")}
          </PrimaryButton>
        )}
      </DialogActions>
    </Dialog>
  );
};

/**
 * "Személyesen láttam, beírom": the coach or the club admin enters the result and the date from the original paper, in
 * both modes. When a certificate exists the replacement is confirmed first, as for the parent.
 */
export const MedicalInPersonDialog = ({
  open,
  profileId,
  name,
  replacing,
  onClose,
}: {
  open: boolean;
  profileId: string;
  name: string;
  replacing: boolean;
  onClose: () => void;
}) => {
  const { t, i18n } = useTranslation();
  const { showSuccess, showError } = useSnackbar();
  const refresh = useMedicalRefresh();
  const [result, setResult] = useState<MedicalResult | "">("");
  const [validUntil, setValidUntil] = useState("");
  const [seen, setSeen] = useState(false);
  const [confirmedReplace, setConfirmedReplace] = useState(false);
  const [busy, setBusy] = useState(false);

  const close = () => {
    setResult("");
    setValidUntil("");
    setSeen(false);
    setConfirmedReplace(false);
    onClose();
  };

  const save = async () => {
    if (!result) return;
    setBusy(true);
    try {
      await submitMedicalInPerson(profileId, { result, validUntil: validUntil || undefined });
      showSuccess(t("medical.in-person.saved"));
      await refresh();
      close();
    } catch (err) {
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    } finally {
      setBusy(false);
    }
  };

  const until = validUntil ? t("medical.verify.until", { date: beforeSuffix(formatLongDate(validUntil, i18n.language)) }) : "";
  const askReplace = replacing && !confirmedReplace;

  return (
    <Dialog open={open} onClose={close} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>{t("medical.in-person.title", { name })}</DialogTitle>
      <DialogContent>
        {askReplace ? (
          <Typography variant="body2" color="text.secondary">{t("medical.in-person.replace")}</Typography>
        ) : (
          <Stack spacing={2} sx={{ pt: 1 }}>
            <MedicalDataRule />
            <MedicalResultFields
              result={result}
              validUntil={validUntil}
              disabled={busy}
              onResultChange={setResult}
              onValidUntilChange={setValidUntil}
            />
            <FormControlLabel
              sx={{ alignItems: "flex-start", m: 0 }}
              control={<Checkbox checked={seen} onChange={(event) => setSeen(event.target.checked)} color="success" sx={{ mt: -0.75 }} />}
              label={
                <Typography variant="body2">
                  {t("medical.ack.in-person", { name, result: result ? t(`medical.result.${result}`) : "…", until })}
                </Typography>
              }
            />
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <SecondaryButton onClick={close}>{t("common.cancel")}</SecondaryButton>
        {askReplace ? (
          <PrimaryButton onClick={() => setConfirmedReplace(true)} sx={{ py: 0.75 }}>{t("medical.replace.confirm")}</PrimaryButton>
        ) : (
          <PrimaryButton disabled={!seen || !resultComplete(result, validUntil) || busy} onClick={() => void save()} sx={{ py: 0.75 }}>
            {t("medical.actions.save")}
          </PrimaryButton>
        )}
      </DialogActions>
    </Dialog>
  );
};

const REASON_MIN = 20;
const REASON_MAX = 500;

/**
 * The platform admin's only look: technical facts with a reason, and a warning that the club sees it. No result, no
 * validity and no file. The answer is gone once the dialog closes; the next look needs a new reason.
 */
export const MedicalAdminAccessDialog = ({
  open,
  profileId,
  onClose,
}: {
  open: boolean;
  profileId: string;
  onClose: () => void;
}) => {
  const { t, i18n } = useTranslation();
  const { showSuccess, showError } = useSnackbar();
  const [reason, setReason] = useState("");
  const [answer, setAnswer] = useState<MedicalAdminAccessResponse | null>(null);
  const [withdrawReason, setWithdrawReason] = useState("");
  const [withdrawing, setWithdrawing] = useState(false);
  const [busy, setBusy] = useState(false);
  const reasonOk = reason.trim().length >= REASON_MIN && reason.trim().length <= REASON_MAX;
  const withdrawOk = withdrawReason.trim().length >= REASON_MIN && withdrawReason.trim().length <= REASON_MAX;

  const close = () => {
    setReason("");
    setAnswer(null);
    setWithdrawReason("");
    setWithdrawing(false);
    onClose();
  };

  const look = async () => {
    setBusy(true);
    try {
      setAnswer(await medicalAdminAccess(profileId, reason.trim()));
    } catch (err) {
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    } finally {
      setBusy(false);
    }
  };

  const withdraw = async () => {
    if (!answer?.certificateId) return;
    setBusy(true);
    try {
      await medicalAdminWithdrawal(profileId, answer.certificateId, withdrawReason.trim());
      showSuccess(t("medical.messages.withdrawn"));
      close();
    } catch (err) {
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    } finally {
      setBusy(false);
    }
  };

  const yesNo = (value: boolean) => t(value ? "medical.admin.facts.yes" : "medical.admin.facts.no");
  const time = (value?: string) => (value ? formatInstant(value, i18n.language) : "–");
  const facts: [string, string][] = answer
    ? [
        ["required", yesNo(answer.required)],
        ["file-required", yesNo(answer.fileRequired)],
        ["current", yesNo(Boolean(answer.certificateId))],
        ["evidence", answer.evidence ?? "–"],
        ["has-file", yesNo(answer.hasFile)],
        ["content-type", answer.contentType ?? "–"],
        ["size", answer.sizeBytes != null ? String(answer.sizeBytes) : "–"],
        ["submitted-at", time(answer.submittedAt)],
        ["verified", yesNo(answer.verified)],
        ["history-count", String(answer.historyCount)],
        ["pending-count", String(answer.pendingCount)],
        ["not-required-since", time(answer.notRequiredSince)],
        ["file-not-required-since", time(answer.fileNotRequiredSince)],
      ]
    : [];

  return (
    <Dialog open={open} onClose={close} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>{t("medical.admin.title")}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="warning">{t("medical.admin.warning")}</Alert>
          {!answer ? (
            <TextField
              multiline
              minRows={2}
              label={t("medical.admin.reason")}
              helperText={t("medical.admin.reason-hint", { min: REASON_MIN, max: REASON_MAX })}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              slotProps={{ htmlInput: { maxLength: REASON_MAX } }}
            />
          ) : (
            <Box sx={{ p: 2, borderRadius: 2, border: "1px solid", borderColor: "divider" }}>
              <Stack spacing={0.5}>
                {facts.map(([key, value]) => (
                  <Typography key={key} variant="body2" color="text.secondary">
                    {t(`medical.admin.facts.${key}`)}: {value}
                  </Typography>
                ))}
              </Stack>
            </Box>
          )}
          {answer?.certificateId && (
            withdrawing ? (
              <Stack spacing={1}>
                <TextField
                  multiline
                  minRows={2}
                  label={t("medical.admin.withdraw-reason")}
                  helperText={t("medical.admin.reason-hint", { min: REASON_MIN, max: REASON_MAX })}
                  value={withdrawReason}
                  onChange={(event) => setWithdrawReason(event.target.value)}
                  slotProps={{ htmlInput: { maxLength: REASON_MAX } }}
                />
                <Box>
                  <Button color="error" variant="outlined" size="small" disabled={!withdrawOk || busy} onClick={() => void withdraw()}
                    sx={{ textTransform: "none", fontWeight: 700, borderRadius: 2 }}>
                    {t("medical.admin.withdraw-confirm")}
                  </Button>
                </Box>
              </Stack>
            ) : (
              <Box>
                <Button color="error" variant="outlined" size="small" onClick={() => setWithdrawing(true)}
                  sx={{ textTransform: "none", fontWeight: 700, borderRadius: 2 }}>
                  {t("medical.admin.withdraw")}
                </Button>
              </Box>
            )
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <SecondaryButton onClick={close}>{t("medical.actions.close")}</SecondaryButton>
        {!answer && (
          <PrimaryButton disabled={!reasonOk || busy} onClick={() => void look()} sx={{ py: 0.75 }}>
            {t("medical.admin.look")}
          </PrimaryButton>
        )}
      </DialogActions>
    </Dialog>
  );
};
