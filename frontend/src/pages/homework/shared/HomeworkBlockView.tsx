import type { ReactNode, Ref } from "react";
import { Box, Typography } from "@mui/material";
import type { HomeworkItem } from "../../../api/homework";
import { ClipBlockPlayer, type ClipPlayerHandle } from "./ClipBlockPlayer";

/** One item read-only, shared by the preview, the coach's detail and the recipient. Plain text: React escapes it. */
export const HomeworkBlockView = ({
  item,
  playerRef,
  clipNumber,
  children,
}: {
  item: HomeworkItem;
  playerRef?: Ref<ClipPlayerHandle>;
  /** Shown when a homework has more clips, so a question can say which one it means. */
  clipNumber?: number;
  children?: ReactNode;
}) => (
  <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
    {item.type === "TEXT" && <Typography sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{item.body}</Typography>}
    {item.type === "CLIP" && (
      <>
        {clipNumber != null && (
          <Typography variant="caption" sx={{ fontWeight: 700, color: "text.secondary" }}>
            {`${clipNumber}. ${item.clipName ?? ""}`}
          </Typography>
        )}
        <ClipBlockPlayer ref={playerRef} clipId={item.clipId} renderStatus={item.clipRenderStatus} focusMs={item.focusMs} />
        {item.body && <Typography variant="body2" sx={{ color: "text.secondary", whiteSpace: "pre-wrap" }}>{item.body}</Typography>}
      </>
    )}
    {item.type === "QUESTION" && (
      <Typography sx={{ fontWeight: 600, whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
        {item.body}{item.required ? " *" : ""}
      </Typography>
    )}
    {children}
  </Box>
);
