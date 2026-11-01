import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Tooltip } from "@mui/material";
import MonitorHeartOutlinedIcon from "@mui/icons-material/MonitorHeartOutlined";
import VerifiedOutlinedIcon from "@mui/icons-material/VerifiedOutlined";
import EditNoteIcon from "@mui/icons-material/EditNote";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import FileUploadOutlinedIcon from "@mui/icons-material/FileUploadOutlined";
import { useTranslation } from "react-i18next";
import { getMedicalStatus, medicalQueryKey } from "../../api/medical";
import { useSnackbar } from "../../context/SnackbarContext";
import { MedicalInPersonDialog, MedicalVerifyDialog, openMedicalFileInNewTab } from "./MedicalDialogs";
import { MedicalSection } from "./MedicalSection";
import { problemDetail } from "./medicalFormat";

/**
 * The coach's sport medical actions on a roster card. The narrow state is read when the menu opens: it tells whether
 * the child needs a file and whether the club lets this coach open it. "Személyesen láttam, beírom" comes first in
 * both modes: once the paper reached the club, the date is enough. Date-only: also "Ellenőriztem" from the original
 * paper. File mode: also the upload, and with the club's file access "Ellenőriztem" from the file and "Megnyitás".
 */
export const PlayerMedicalMenu = ({ profileId, name }: { profileId: string; name: string }) => {
  const { t } = useTranslation();
  const { showError } = useSnackbar();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [dialog, setDialog] = useState<"verify" | "in-person" | "upload" | null>(null);
  const { data } = useQuery({
    queryKey: medicalQueryKey(profileId),
    queryFn: () => getMedicalStatus(profileId),
    enabled: Boolean(anchor) || dialog !== null,
    retry: false,
  });

  const fileMode = Boolean(data?.fileRequired);
  const hasRow = Boolean(data?.status) && data?.status !== "MISSING";
  const positive = data?.result === "VERSENYEZHET" || data?.result === "ALKALMAS";
  const canOpen = fileMode && Boolean(data?.coachFileAccess) && Boolean(data?.hasFile) && Boolean(data?.certificateId);
  const canVerify = Boolean(data?.required) && hasRow && !data?.verified && positive && (!fileMode || canOpen);
  const canEnter = Boolean(data?.required);
  const canUpload = canEnter && fileMode;
  const nothing = data && !canOpen && !canVerify && !canEnter;

  const open = async () => {
    setAnchor(null);
    if (!data?.certificateId) return;
    try {
      await openMedicalFileInNewTab(profileId, data.certificateId);
    } catch (err) {
      const { status, detail } = problemDetail(err);
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    }
  };

  return (
    <>
      <Tooltip title={t("medical.coach.menu")}>
        <IconButton size="small" aria-label={t("medical.coach.menu")} onClick={(event) => setAnchor(event.currentTarget)}>
          <MonitorHeartOutlinedIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
        {!data && <MenuItem disabled>{t("common.loading")}</MenuItem>}
        {canEnter && (
          <MenuItem onClick={() => { setAnchor(null); setDialog("in-person"); }}>
            <ListItemIcon><EditNoteIcon fontSize="small" /></ListItemIcon>
            <ListItemText>{t("medical.in-person.action")}</ListItemText>
          </MenuItem>
        )}
        {canVerify && (
          <MenuItem onClick={() => { setAnchor(null); setDialog("verify"); }}>
            <ListItemIcon><VerifiedOutlinedIcon fontSize="small" /></ListItemIcon>
            <ListItemText>{t("medical.verify.action")}</ListItemText>
          </MenuItem>
        )}
        {canUpload && (
          <MenuItem onClick={() => { setAnchor(null); setDialog("upload"); }}>
            <ListItemIcon><FileUploadOutlinedIcon fontSize="small" /></ListItemIcon>
            <ListItemText>{t("medical.club.upload")}</ListItemText>
          </MenuItem>
        )}
        {canOpen && (
          <MenuItem onClick={() => void open()}>
            <ListItemIcon><OpenInNewIcon fontSize="small" /></ListItemIcon>
            <ListItemText>{t("medical.actions.open")}</ListItemText>
          </MenuItem>
        )}
        {nothing && <MenuItem disabled>{t("medical.coach.nothing")}</MenuItem>}
      </Menu>
      {dialog === "verify" && data && (
        <MedicalVerifyDialog
          open
          profileId={profileId}
          name={name}
          certificateId={data.certificateId}
          result={data.result}
          validUntil={data.validUntil}
          method={fileMode ? "FILE" : "ORIGINAL"}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === "in-person" && (
        <MedicalInPersonDialog open profileId={profileId} name={name} replacing={hasRow} onClose={() => setDialog(null)} />
      )}
      {dialog === "upload" && (
        <Dialog open fullWidth maxWidth="sm" onClose={() => setDialog(null)} sx={{ "& .MuiDialog-paper": { overflow: "hidden" } }}>
          <DialogTitle sx={{ fontWeight: 700 }}>{t("medical.club.upload-title", { name })}</DialogTitle>
          <DialogContent dividers>
            <MedicalSection profileId={profileId} viewer="manager" variant="bare" coachUpload onSubmitted={() => setDialog(null)} />
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button color="inherit" onClick={() => setDialog(null)} sx={{ textTransform: "none", fontWeight: 700 }}>{t("medical.actions.close")}</Button>
          </DialogActions>
        </Dialog>
      )}
    </>
  );
};
