import { useEffect, useRef, useState } from "react";
import { Box, Button, CircularProgress, Stack, TextField, Tooltip, Typography } from "@mui/material";
import { ChatBubbleOutline, SendOutlined } from "@mui/icons-material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  addComment,
  commentsKey,
  formatHomeworkDate,
  homeworkErrorMessage,
  listComments,
} from "../../../api/homework";
import { useSnackbar } from "../../../context/SnackbarContext";
import { useView } from "../../../context/ViewContext";
import { ROLES } from "../../../types/roles";
import { formatPersonName } from "../../../utils/stringUtils";
import { SectionLabel } from "./Section";

export const CommentThread = ({
  assignmentId,
  canWrite,
  hideTitle = false,
  fill = false,
  contained = true,
}: {
  assignmentId: string;
  canWrite: boolean;
  /** When the card around it already carries the title. */
  hideTitle?: boolean;
  /**
   * The list takes the height left in its parent instead of a fixed one: on a wide screen only (true, for a page whose
   * columns stack on a phone), or on every screen ("always", for a drawer).
   */
  fill?: boolean | "always";
  /** False inside something that scrolls already, such as the drawer: one scrollbar, not two. */
  contained?: boolean;
}) => {
  const { t } = useTranslation();
  const { showError } = useSnackbar();
  const { activeView } = useView();
  const recipientView = activeView === ROLES.PLAYER || activeView === ROLES.PARENT;
  const queryClient = useQueryClient();
  const [body, setBody] = useState("");
  const { data, isLoading } = useQuery({
    queryKey: commentsKey(assignmentId),
    queryFn: ({ signal }) => listComments(assignmentId, signal),
  });
  // Only the box scrolls, never the page: the newest message sits at the bottom, next to the field, and the box goes
  // there on arrival and after each new message.
  const list = useRef<HTMLDivElement | null>(null);
  const comments = data ?? [];
  useEffect(() => {
    if (list.current) list.current.scrollTop = list.current.scrollHeight;
  }, [assignmentId, comments.length]);

  const send = useMutation({
    mutationFn: () => addComment(assignmentId, { body }),
    onSuccess: () => {
      setBody("");
      void queryClient.invalidateQueries({ queryKey: commentsKey(assignmentId) });
    },
    onError: (error) => showError(homeworkErrorMessage(error, t("homework.save-failed"))),
  });

  const grow = (on: number | string, off: number | string | undefined) =>
    (fill === "always" ? on : fill ? { md: on } : off);
  return (
    <Stack spacing={1} sx={fill ? { flexGrow: grow(1, 0), minHeight: 0 } : undefined}>
      <Box>
        {!hideTitle && <SectionLabel icon={<ChatBubbleOutline />}>{t("homework.comments.title")}</SectionLabel>}
        {canWrite && (
          <Typography variant="caption" sx={{ color: "text.secondary" }}>{t(recipientView ? "homework.comments.hint-recipient" : "homework.comments.hint")}</Typography>
        )}
      </Box>
      {isLoading && <CircularProgress size={20} />}
      {!isLoading && comments.length === 0 && (
        <Typography variant="body2" sx={{ color: "text.secondary" }}>{t("homework.comments.empty")}</Typography>
      )}
      {comments.length > 0 && (
        <Stack
          ref={list}
          spacing={1}
          sx={contained ? {
            position: "relative",
            maxHeight: fill === "always" ? "none" : { xs: "50vh", md: fill ? "none" : 360 },
            flexGrow: grow(1, 0),
            minHeight: grow(120, undefined),
            overflowY: "auto",
            overscrollBehavior: "contain",
            pr: 0.5,
          } : { position: "relative" }}
        >
          {comments.map((comment) => (
            <Box key={comment.id} sx={{ p: 1, borderRadius: 1.5, bgcolor: "action.hover", flexShrink: 0 }}>
              <Typography variant="caption" sx={{ color: "text.secondary" }}>
                {formatPersonName(comment.authorFirstName, comment.authorLastName) || "—"} · {formatHomeworkDate(comment.createdAt)}
              </Typography>
              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{comment.body}</Typography>
            </Box>
          ))}
        </Stack>
      )}
      {canWrite ? (
        <Stack direction="row" spacing={1} sx={{ alignItems: "flex-end" }}>
          <TextField
            size="small"
            fullWidth
            multiline
            maxRows={5}
            value={body}
            onChange={(event) => setBody(event.target.value)}
            placeholder={t(recipientView ? "homework.comments.placeholder-recipient" : "homework.comments.placeholder")}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
          <Tooltip title={t("homework.comments.send")}>
            <span>
              <Button
                variant="contained"
                aria-label={t("homework.comments.send")}
                disabled={!body.trim() || send.isPending}
                onClick={() => send.mutate()}
                sx={{ minWidth: 40, width: 40, height: 40, p: 0, borderRadius: 2 }}
              >
                <SendOutlined fontSize="small" />
              </Button>
            </span>
          </Tooltip>
        </Stack>
      ) : (
        <Typography variant="caption" sx={{ color: "text.secondary" }}>{t("homework.comments.closed")}</Typography>
      )}
    </Stack>
  );
};
