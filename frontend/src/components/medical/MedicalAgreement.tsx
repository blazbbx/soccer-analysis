import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { useTranslation } from "react-i18next";
import {
  clubMedicalAgreementsQueryKey,
  getClubMedicalAgreements,
  MEDICAL_AGREEMENT_MAX_BYTES,
  openClubMedicalAgreement,
  recordClubMedicalAgreement,
  withdrawClubMedicalAgreement,
  type ClubMedicalAgreement,
} from "../../api/medical";
import { useSnackbar } from "../../context/SnackbarContext";
import { PrimaryButton } from "../ui/PrimaryButton";
import { SecondaryButton } from "../ui/SecondaryButton";
import { formatInstant, problemDetail } from "./medicalFormat";

const REASON_MIN = 10;

/**
 * The club's signed data processing agreement (docs/sportorvosi-jogszeruseg.md, Engedélyezés az üzemeltetőtől).
 * The platform admin uploads the signed PDF once the club's representative signed it, which makes sport
 * medical collection available to the club; the platform admin can also withdraw it. The club's own admins see it
 * and can open the signed copy. Whether the club then collects is still the club's decision, on the panel above.
 */
export const ClubMedicalAgreementSection = ({
  clubId,
  platformAdmin,
  currentDpaVersion,
  dpaDocumentUrl,
}: {
  clubId: string;
  platformAdmin: boolean;
  currentDpaVersion?: string;
  /** The text of the current agreement version, to read before signing. */
  dpaDocumentUrl?: string;
}) => {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const { showSuccess, showError } = useSnackbar();
  const { data: agreements } = useQuery({
    queryKey: clubMedicalAgreementsQueryKey(clubId),
    queryFn: () => getClubMedicalAgreements(clubId),
    enabled: clubId.length > 0,
    retry: false,
  });
  const [formOpen, setFormOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const fileInput = useRef<HTMLInputElement | null>(null);

  if (!agreements) return null;

  const live = agreements.find((agreement) => agreement.active);
  const liveIsCurrent = Boolean(live && currentDpaVersion && live.dpaVersion === currentDpaVersion);
  const lastWithdrawn = !live ? agreements.find((agreement) => agreement.endKind === "WITHDRAWN") : undefined;
  const past = agreements.filter((agreement) => !agreement.active);
  const showForm = platformAdmin && Boolean(currentDpaVersion) && (formOpen || !live);
  const canSave = Boolean(file) && !pending;

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["medical"] });

  const resetForm = () => {
    setFormOpen(false);
    setFile(null);
  };

  const fail = (err: unknown) => {
    const { status, detail } = problemDetail(err);
    if (status !== 403) showError(detail || t("medical.messages.failed"));
  };

  const record = async () => {
    if (!file) return;
    setPending(true);
    try {
      await recordClubMedicalAgreement(clubId, file);
      resetForm();
      showSuccess(t("medical.agreement.recorded"));
      await refresh();
    } catch (err) {
      fail(err);
    } finally {
      setPending(false);
    }
  };

  const withdraw = async () => {
    setPending(true);
    try {
      await withdrawClubMedicalAgreement(clubId, reason.trim());
      setWithdrawOpen(false);
      setReason("");
      showSuccess(t("medical.agreement.withdrawn"));
      await refresh();
    } catch (err) {
      fail(err);
    } finally {
      setPending(false);
    }
  };

  const open = async (agreement: ClubMedicalAgreement) => {
    const tab = window.open("", "_blank");
    try {
      const { url } = await openClubMedicalAgreement(clubId, agreement.id);
      if (tab) {
        tab.opener = null;
        tab.location.href = url;
      } else {
        window.open(url, "_blank", "noopener");
      }
    } catch (err) {
      tab?.close();
      fail(err);
    }
  };

  const pickFile = (picked: File | undefined) => {
    if (!picked) return;
    if (picked.size > MEDICAL_AGREEMENT_MAX_BYTES) {
      showError(t("medical.agreement.too-large"));
      return;
    }
    setFile(picked);
  };

  const describe = (agreement: ClubMedicalAgreement) =>
    t("medical.agreement.uploaded", {
      date: formatInstant(agreement.recordedAt, i18n.language),
      version: agreement.dpaVersion,
    });

  return (
    <Box>
      <Typography variant="body2" fontWeight={700} sx={{ mb: 0.5 }}>{t("medical.agreement.title")}</Typography>

      {live ? (
        <Stack spacing={0.5}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
            <Typography variant="caption" color="text.secondary">
              {describe(live)}
            </Typography>
            <Button size="small" startIcon={<OpenInNewIcon />} onClick={() => void open(live)} sx={{ textTransform: "none" }}>
              {t("medical.agreement.open")}
            </Button>
          </Stack>
          {!liveIsCurrent && currentDpaVersion && (
            <Typography variant="caption" sx={{ color: "warning.main" }}>
              {t("medical.agreement.old-version", { old: live.dpaVersion, version: currentDpaVersion })}
            </Typography>
          )}
        </Stack>
      ) : (
        <Stack spacing={0.75}>
          {!platformAdmin && <Alert severity="info">{t("medical.agreement.missing-club")}</Alert>}
          {lastWithdrawn && (
            <Typography variant="caption" sx={{ color: "warning.main" }}>
              {t("medical.agreement.withdrawn-note", {
                date: formatInstant(lastWithdrawn.endedAt, i18n.language),
                reason: lastWithdrawn.withdrawalReason ?? "",
                interpolation: { escapeValue: false },
              })}
            </Typography>
          )}
          {platformAdmin && !currentDpaVersion && (
            <Typography variant="caption" color="text.secondary">{t("medical.agreement.dpa-missing")}</Typography>
          )}
        </Stack>
      )}

      {dpaDocumentUrl && currentDpaVersion && (
        <Link href={dpaDocumentUrl} target="_blank" rel="noopener" variant="caption" sx={{ display: "inline-block", mt: 0.5 }}>
          {t("medical.agreement.text", { version: currentDpaVersion })}
        </Link>
      )}

      {platformAdmin && currentDpaVersion && (
        <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.75 }}>
          {t("medical.agreement.admin-hint", { version: currentDpaVersion })}
        </Typography>
      )}

      {showForm && (
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ sm: "center" }} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
          <input ref={fileInput} type="file" accept="application/pdf" hidden
            onChange={(event) => {
              const picked = event.target.files?.[0];
              event.target.value = "";
              pickFile(picked);
            }} />
          <SecondaryButton startIcon={<UploadFileIcon />} disabled={pending} onClick={() => fileInput.current?.click()}>
            {file ? file.name : t("medical.agreement.pick-file")}
          </SecondaryButton>
          <PrimaryButton size="small" disabled={!canSave} onClick={() => void record()} sx={{ py: 0.75 }}>
            {live ? t("medical.agreement.replace") : t("medical.agreement.record")}
          </PrimaryButton>
          {live && (
            <Button size="small" disabled={pending} onClick={resetForm} sx={{ textTransform: "none" }}>
              {t("common.cancel")}
            </Button>
          )}
        </Stack>
      )}

      {platformAdmin && live && !formOpen && (
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
          {currentDpaVersion && (
            <SecondaryButton disabled={pending} onClick={() => setFormOpen(true)}>{t("medical.agreement.new-copy")}</SecondaryButton>
          )}
          <Button size="small" color="error" disabled={pending} onClick={() => setWithdrawOpen(true)} sx={{ textTransform: "none" }}>
            {t("medical.agreement.withdraw")}
          </Button>
        </Stack>
      )}

      {past.length > 0 && (
        <Stack spacing={0.25} sx={{ mt: 1 }}>
          <Typography variant="caption" fontWeight={700} color="text.secondary">{t("medical.agreement.history")}</Typography>
          {past.map((agreement) => (
            <Stack key={agreement.id} direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
              <Typography variant="caption" color="text.secondary">
                {describe(agreement)} · {t(`medical.agreement.end.${agreement.endKind ?? "WITHDRAWN"}`, {
                  date: formatInstant(agreement.endedAt, i18n.language),
                })}
              </Typography>
              <Button size="small" onClick={() => void open(agreement)} sx={{ textTransform: "none", minWidth: 0, p: 0 }}>
                {t("medical.agreement.open")}
              </Button>
            </Stack>
          ))}
        </Stack>
      )}

      <Dialog open={withdrawOpen} onClose={() => setWithdrawOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{t("medical.agreement.withdraw-title")}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 1.5 }}>{t("medical.agreement.withdraw-body")}</Typography>
          <TextField fullWidth multiline minRows={2} label={t("medical.agreement.withdraw-reason")} value={reason}
            onChange={(event) => setReason(event.target.value)} slotProps={{ htmlInput: { maxLength: 500 } }} />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <SecondaryButton onClick={() => setWithdrawOpen(false)}>{t("common.cancel")}</SecondaryButton>
          <Button variant="outlined" color="error" disabled={pending || reason.trim().length < REASON_MIN}
            onClick={() => void withdraw()} sx={{ textTransform: "none", fontWeight: 700, borderRadius: 2 }}>
            {t("medical.agreement.withdraw")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
