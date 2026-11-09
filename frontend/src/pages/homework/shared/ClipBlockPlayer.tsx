import { forwardRef, useImperativeHandle, useRef } from "react";
import { Alert, Box, CircularProgress, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { useGetRenderedClipDownloadUrl } from "../../../api/generated/clip-controller/clip-controller";

export interface ClipPlayerHandle {
  currentMs: () => number | null;
  seekTo: (milliseconds: number) => void;
}

/** Plays the clip of an item, starting at its focus point. A deleted or unfinished clip is a line of text, not an error. */
export const ClipBlockPlayer = forwardRef<ClipPlayerHandle, {
  clipId?: string | null;
  renderStatus?: string | null;
  focusMs?: number | null;
}>(({ clipId, renderStatus, focusMs }, ref) => {
  const { t } = useTranslation();
  const video = useRef<HTMLVideoElement | null>(null);
  const ready = renderStatus === "COMPLETED";
  const { data, isError } = useGetRenderedClipDownloadUrl(clipId ?? "", {
    query: { enabled: Boolean(clipId) && ready },
  });
  const url = data ? Object.values(data as unknown as Record<string, string>)[0] : undefined;

  useImperativeHandle(ref, () => ({
    currentMs: () => (video.current ? Math.round(video.current.currentTime * 1000) : null),
    seekTo: (milliseconds) => {
      if (video.current) {
        video.current.currentTime = milliseconds / 1000;
        video.current.scrollIntoView({ behavior: "smooth", block: "center" });
      }
    },
  }));

  if (!clipId) {
    return <Typography variant="body2" sx={{ color: "text.secondary", fontStyle: "italic" }}>{t("homework.clip.deleted")}</Typography>;
  }
  if (renderStatus === "FAILED" || isError) {
    return <Alert severity="warning">{t("homework.clip.failed")}</Alert>;
  }
  if (!ready) {
    return <Typography variant="body2" sx={{ color: "text.secondary" }}>{t("homework.clip.processing")}</Typography>;
  }
  if (!url) {
    return <CircularProgress size={22} />;
  }
  return (
    <Box
      component="video"
      ref={video}
      controls
      src={url}
      onLoadedMetadata={() => {
        if (focusMs != null && video.current) video.current.currentTime = focusMs / 1000;
      }}
      sx={{ width: "100%", maxHeight: "60vh", borderRadius: 2, display: "block", bgcolor: "#000" }}
    />
  );
});
ClipBlockPlayer.displayName = "ClipBlockPlayer";
