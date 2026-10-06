import { useCallback, useEffect, useRef, useState } from "react";
import { Box, Dialog, DialogContent, LinearProgress, Typography } from "@mui/material";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import { useTranslation } from "react-i18next";

const HOLD_MS = 5000;

export const HoldToDeleteDialog = ({
  open,
  title,
  itemName,
  description,
  pending,
  showWarning = true,
  warningText,
  onClose,
  onConfirm,
}: {
  open: boolean;
  title: string;
  itemName: string;
  description: string;
  pending?: boolean;
  showWarning?: boolean;
  warningText?: string;
  onClose: () => void;
  onConfirm: () => void;
}) => {
  const { t } = useTranslation();
  const [progress, setProgress] = useState(0);
  const frame = useRef<number | null>(null);
  const started = useRef<number | null>(null);

  const stop = useCallback(() => {
    if (frame.current != null) cancelAnimationFrame(frame.current);
    frame.current = null;
    started.current = null;
    setProgress(0);
  }, []);

  useEffect(() => {
    if (!open) stop();
  }, [open, stop]);

  const start = () => {
    if (pending) return;
    stop();
    started.current = performance.now();
    const step = (now: number) => {
      if (started.current == null) return;
      const next = Math.min(100, ((now - started.current) / HOLD_MS) * 100);
      setProgress(next);
      if (next >= 100) {
        stop();
        onConfirm();
        return;
      }
      frame.current = requestAnimationFrame(step);
    };
    frame.current = requestAnimationFrame(step);
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="xs"
      PaperProps={{
        sx: {
          border: "2px solid",
          borderColor: "error.main",
        },
      }}
    >
      <DialogContent>
        <Box sx={{ display: "flex", gap: 1.5, alignItems: "flex-start", mb: 2 }}>
          <WarningAmberIcon color="error" sx={{ fontSize: 28 }} />
          <Box>
            <Typography variant="h6" fontWeight={800}>{title}</Typography>
            {itemName && (
              <Typography variant="body1" fontWeight={700} sx={{ mt: 0.5 }}>
                {itemName}
              </Typography>
            )}
            {description && (
              <Typography variant="body1" sx={{ mt: 1 }}>
                {description}
              </Typography>
            )}
            {showWarning && (
              <Typography
                variant="body1"
                fontWeight={600}
                sx={{ mt: 1, color: "error.light" }}
              >
                {warningText ?? t("clubs.editor.hold-warning")}
              </Typography>
            )}
          </Box>
        </Box>
        <Box
          onPointerDown={start}
          onPointerUp={stop}
          onPointerLeave={stop}
          onPointerCancel={stop}
          sx={{
            position: "relative",
            borderRadius: 2,
            overflow: "hidden",
            bgcolor: "error.main",
            color: "#fff",
            textAlign: "center",
            py: 1.5,
            fontSize: "1rem",
            fontWeight: 800,
            cursor: pending ? "wait" : "pointer",
            userSelect: "none",
          }}
        >
          <LinearProgress
            variant="determinate"
            value={progress}
            sx={{
              position: "absolute",
              inset: 0,
              height: "100%",
              bgcolor: "transparent",
              "& .MuiLinearProgress-bar": { bgcolor: "rgba(0,0,0,0.25)" },
            }}
          />
          <Box sx={{ position: "relative" }}>{t("clubs.editor.hold")}</Box>
        </Box>
      </DialogContent>
    </Dialog>
  );
};
