import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import BlockIcon from "@mui/icons-material/Block";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import CloseIcon from "@mui/icons-material/Close";
import UndoIcon from "@mui/icons-material/Undo";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../ui/PrimaryButton";
import { SecondaryButton } from "../ui/SecondaryButton";
import { flattenRedaction, RedactionError, renderForRedaction, type RedactionRect } from "./medicalRedaction";

/** A smaller drag is a slip of the hand, not a cover. */
const MIN_FRACTION = 0.005;

const COVER_ITEMS = ["taj", "address", "mother", "photo", "note"] as const;
const KEEP_ITEMS = ["name", "result", "date", "stamp"] as const;

const clamp = (value: number) => Math.min(1, Math.max(0, value));

/** The two lists side by side: what must be covered, and what the verifier needs to read. */
const RedactList = ({ kind, items }: { kind: "cover" | "keep"; items: readonly string[] }) => {
  const { t } = useTranslation();
  const color = kind === "cover" ? "error.main" : "success.main";
  const Icon = kind === "cover" ? BlockIcon : CheckCircleOutlineIcon;
  return (
    <Box sx={{ p: 1.25, borderRadius: 2, border: "1px solid", borderColor: color, borderLeftWidth: 4 }}>
      <Typography variant="body2" fontWeight={700} sx={{ color, mb: 0.5 }}>{t(`medical.redact.${kind}-title`)}</Typography>
      <Stack spacing={0.25}>
        {items.map((item) => (
          <Stack key={item} direction="row" spacing={0.75} alignItems="center">
            <Icon sx={{ fontSize: 16, color, flexShrink: 0 }} />
            <Typography variant="body2">{t(`medical.redact.${kind}.${item}`)}</Typography>
          </Stack>
        ))}
      </Stack>
    </Box>
  );
};

/**
 * The picked certificate on the screen, to be covered with black rectangles. Only the flattened image leaves this
 * dialog, so the original is never uploaded. No automatic recognition: the person doing it decides what is covered.
 */
export const MedicalRedactDialog = ({
  file,
  onDone,
  onCancel,
}: {
  file: File;
  onDone: (redacted: File) => void;
  onCancel: () => void;
}) => {
  const { t } = useTranslation();
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down("sm"));
  const [pages, setPages] = useState<HTMLCanvasElement[] | null>(null);
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const [rects, setRects] = useState<RedactionRect[]>([]);
  const [draft, setDraft] = useState<RedactionRect | null>(null);
  const [busy, setBusy] = useState(false);
  const start = useRef<{ page: number; x: number; y: number } | null>(null);

  useEffect(() => {
    let cancelled = false;
    let loaded: HTMLCanvasElement[] = [];
    renderForRedaction(file)
      .then((rendered) => {
        loaded = rendered;
        if (!cancelled) setPages(rendered);
      })
      .catch((err: unknown) => {
        if (!cancelled) setErrorKey(err instanceof RedactionError ? err.key : "unreadable");
      });
    return () => {
      cancelled = true;
      // The uncovered page should not stay in memory longer than the dialog.
      loaded.forEach((canvas) => {
        canvas.width = 0;
      });
    };
  }, [file]);

  const pointAt = (event: ReactPointerEvent<HTMLElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    return { x: clamp((event.clientX - box.left) / box.width), y: clamp((event.clientY - box.top) / box.height) };
  };

  const rectFrom = (page: number, from: { x: number; y: number }, to: { x: number; y: number }): RedactionRect => ({
    page,
    x: Math.min(from.x, to.x),
    y: Math.min(from.y, to.y),
    width: Math.abs(to.x - from.x),
    height: Math.abs(to.y - from.y),
  });

  const onPointerDown = (page: number) => (event: ReactPointerEvent<HTMLElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    start.current = { page, ...pointAt(event) };
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLElement>) => {
    if (!start.current) return;
    setDraft(rectFrom(start.current.page, start.current, pointAt(event)));
  };

  const onPointerUp = (event: ReactPointerEvent<HTMLElement>) => {
    if (!start.current) return;
    const rect = rectFrom(start.current.page, start.current, pointAt(event));
    start.current = null;
    setDraft(null);
    if (rect.width >= MIN_FRACTION && rect.height >= MIN_FRACTION) setRects((current) => [...current, rect]);
  };

  const finish = async () => {
    if (!pages) return;
    setBusy(true);
    try {
      onDone(await flattenRedaction(pages, rects));
    } catch {
      setErrorKey("unreadable");
      setBusy(false);
    }
  };

  const cover = (rect: RedactionRect, key: string, removable: number | null) => (
    <Box
      key={key}
      sx={{
        position: "absolute",
        left: `${rect.x * 100}%`,
        top: `${rect.y * 100}%`,
        width: `${rect.width * 100}%`,
        height: `${rect.height * 100}%`,
        bgcolor: "#000",
      }}
    >
      {removable !== null && (
        <IconButton
          size="small"
          aria-label={t("medical.redact.remove")}
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => setRects((current) => current.filter((_, index) => index !== removable))}
          sx={{ position: "absolute", top: -10, right: -10, width: 22, height: 22, bgcolor: "error.main", color: "#fff", "&:hover": { bgcolor: "error.dark" } }}
        >
          <CloseIcon sx={{ fontSize: 14 }} />
        </IconButton>
      )}
    </Box>
  );

  return (
    <Dialog open onClose={busy ? undefined : onCancel} maxWidth="md" fullWidth fullScreen={fullScreen}>
      <DialogTitle sx={{ fontWeight: 700 }}>{t("medical.redact.title")}</DialogTitle>
      <DialogContent>
        <Stack spacing={1.5}>
          <Typography variant="body2">{t("medical.redact.hint")}</Typography>
          <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" }, gap: 1 }}>
            <RedactList kind="cover" items={COVER_ITEMS} />
            <RedactList kind="keep" items={KEEP_ITEMS} />
          </Box>
          {errorKey ? (
            <Alert severity="error">{t(`medical.redact.error.${errorKey}`)}</Alert>
          ) : !pages ? (
            <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}><CircularProgress size={28} /></Box>
          ) : (
            <>
              <Stack direction="row" spacing={1} alignItems="center">
                <Button size="small" color="inherit" startIcon={<UndoIcon />} disabled={rects.length === 0 || busy}
                  onClick={() => setRects((current) => current.slice(0, -1))} sx={{ textTransform: "none", fontWeight: 700 }}>
                  {t("medical.redact.undo")}
                </Button>
                <Typography variant="caption" color="text.secondary">{t("medical.redact.count", { count: rects.length })}</Typography>
              </Stack>
              {/* The right gutter leaves room to scroll on a phone, where a drag on the page draws. */}
              <Stack spacing={2} sx={{ pr: { xs: 3, sm: 0 } }}>
                {pages.map((canvas, page) => (
                  <Box
                    key={page}
                    data-redact-page={page}
                    onPointerDown={onPointerDown(page)}
                    onPointerMove={onPointerMove}
                    onPointerUp={onPointerUp}
                    onPointerCancel={onPointerUp}
                    sx={{ position: "relative", cursor: "crosshair", touchAction: "none", userSelect: "none", border: "1px solid", borderColor: "divider", lineHeight: 0 }}
                  >
                    <Box
                      ref={(node: HTMLDivElement | null) => {
                        if (node && node.firstChild !== canvas) {
                          canvas.style.width = "100%";
                          canvas.style.height = "auto";
                          canvas.style.display = "block";
                          node.replaceChildren(canvas);
                        }
                      }}
                    />
                    {rects.map((rect, index) => (rect.page === page ? cover(rect, `rect-${index}`, index) : null))}
                    {draft?.page === page && cover(draft, "draft", null)}
                  </Box>
                ))}
              </Stack>
              {rects.length === 0 && <Alert severity="warning">{t("medical.redact.none-warning")}</Alert>}
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <SecondaryButton onClick={onCancel} disabled={busy}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton disabled={!pages || busy || Boolean(errorKey)} onClick={() => void finish()} sx={{ py: 0.75 }}>
          {t("medical.redact.done")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
