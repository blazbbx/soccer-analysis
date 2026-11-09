import { Fragment, useEffect, useState, type ReactNode } from "react";
import {
  Alert, Avatar, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Divider,
  IconButton, Stack, TextField, Typography,
} from "@mui/material";
import { ArrowForward, ChatBubbleOutline, Check, Close, Undo } from "@mui/icons-material";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  assignmentKey,
  closedNotSubmitted,
  formatClipTime,
  formatHomeworkDate,
  HOMEWORK_KEY,
  commentsKey,
  homeworkErrorMessage,
  returnAssignment,
  reviewAssignment,
  useAssignment,
  type Assignment,
  type HomeworkAnswer,
  type HomeworkItem,
} from "../../../api/homework";
import { useSnackbar } from "../../../context/SnackbarContext";
import { formatPersonName } from "../../../utils/stringUtils";
import { AssignmentStatusChip, DueLabel } from "../shared/AssignmentStatusChip";
import { CommentThread } from "../shared/CommentThread";
import { InnerBox, SectionLabel } from "../shared/Section";

/** The player's answer in a box under the question, as the player wrote it. */
const AnswerBox = ({ children, empty }: { children: ReactNode; empty?: boolean }) => (
  <Box sx={{ px: 1.5, py: 1.25, borderRadius: 2, border: 1, borderColor: "divider", bgcolor: "action.hover", color: empty ? "text.secondary" : "text.primary" }}>
    {children}
  </Box>
);

const AnswerView = ({ item, number, answer }: { item: HomeworkItem; number: number; answer?: HomeworkAnswer }) => {
  const { t } = useTranslation();
  const correct = new Set(item.correctOptions ?? []);
  const selected = new Set(answer?.selectedOptions ?? []);
  const choice = item.questionKind === "SINGLE_CHOICE" || item.questionKind === "MULTI_CHOICE";
  return (
    <InnerBox>
      <Stack spacing={1}>
        <Stack direction="row" spacing={1} sx={{ alignItems: "baseline" }}>
          <SectionLabel>{t("homework.assignment.question-number", { index: number })}</SectionLabel>
          {item.required && <Typography variant="caption" sx={{ color: "warning.main" }}>{t("homework.assignment.required")}</Typography>}
        </Stack>
        <Typography sx={{ fontWeight: 700, overflowWrap: "anywhere", whiteSpace: "pre-wrap" }}>{item.body}</Typography>
        {item.questionKind === "FREE_TEXT" && (
          <AnswerBox empty={!answer?.textAnswer}>
            <Typography variant="body2" sx={{ color: "inherit", whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
              {answer?.textAnswer ?? t("homework.review.no-answer")}
            </Typography>
          </AnswerBox>
        )}
        {item.questionKind === "TIMESTAMP" && (
          <AnswerBox empty={answer?.timestampMs == null}>
            <Typography variant="body2" sx={{ color: "inherit" }}>
              {answer?.timestampMs != null ? formatClipTime(answer.timestampMs) : t("homework.review.no-answer")}
            </Typography>
          </AnswerBox>
        )}
        {choice && (
          <Stack spacing={0.25}>
            {(item.options ?? []).map((option, index) => (
              <Typography
                key={index}
                variant="body2"
                sx={{ color: correct.has(index) ? "success.main" : "text.primary", fontWeight: selected.has(index) ? 700 : 400 }}
              >
                {selected.has(index) ? "● " : "○ "}{option}
              </Typography>
            ))}
          </Stack>
        )}
      </Stack>
    </InnerBox>
  );
};

/** One player's work and the coach's decision on it, over the list instead of beside it. */
export const SubmissionDialog = ({
  assignmentId,
  homeworkTitle,
  onClose,
  onNext,
  nextCount = 0,
}: {
  assignmentId: string | null;
  homeworkTitle?: string;
  onClose: () => void;
  onNext?: () => void;
  /** How many other handed-in ones wait, for the "next" link. */
  nextCount?: number;
}) => {
  const { t } = useTranslation();
  const { showError } = useSnackbar();
  const queryClient = useQueryClient();
  const { data, isLoading, isError } = useAssignment(assignmentId ?? undefined);
  const [feedback, setFeedback] = useState("");
  const [returning, setReturning] = useState(false);
  const [returnNote, setReturnNote] = useState("");

  useEffect(() => {
    setFeedback("");
    setReturning(false);
    setReturnNote("");
  }, [data?.id]);

  const refresh = (saved?: Assignment) => {
    if (saved) queryClient.setQueryData(assignmentKey(saved.id), saved);
    void queryClient.invalidateQueries({ queryKey: HOMEWORK_KEY });
  };
  const fail = (error: unknown) => {
    showError(homeworkErrorMessage(error, t("homework.save-failed")));
    void queryClient.invalidateQueries({ queryKey: HOMEWORK_KEY });
  };
  const review = useMutation({
    mutationFn: () => reviewAssignment(assignmentId ?? "", { feedback: feedback || null }),
    onSuccess: refresh,
    onError: fail,
  });

  const sendBack = useMutation({
    mutationFn: () => returnAssignment(assignmentId ?? "", { note: returnNote.trim() }),
    onSuccess: (saved) => {
      setReturning(false);
      setReturnNote("");
      refresh(saved);
      // The note also went into the conversation.
      void queryClient.invalidateQueries({ queryKey: commentsKey(saved.id) });
    },
    onError: (error) => {
      setReturning(false);
      fail(error);
    },
  });

  const pending = review.isPending || sendBack.isPending;
  const answers = new Map((data?.answers ?? []).map((answer) => [answer.itemId, answer]));
  const questions = (data?.items ?? []).filter((item) => item.type === "QUESTION");
  const archived = data?.homeworkStatus === "ARCHIVED";
  const canAccept = data?.status === "SUBMITTED" && !archived;
  // Nothing is stored before the hand-in, so there is nothing to show until then.
  const handedIn = Boolean(data?.submittedAt);
  const name = formatPersonName(data?.firstName, data?.lastName);
  const initials = `${(data?.lastName ?? "").charAt(0)}${(data?.firstName ?? "").charAt(0)}`.toUpperCase() || "?";

  const facts: [string, ReactNode][] = data ? [
    [t("homework.table.status"), (
      <Stack direction="row" spacing={0.5} sx={{ flexWrap: "wrap", rowGap: 0.5 }}>
        <AssignmentStatusChip status={data.status} notSubmitted={closedNotSubmitted(archived, data.status) ? "staff" : undefined} />
        <DueLabel overdue={data.overdue} dueSoon={data.dueSoon} late={data.late} dueAt={data.effectiveDueAt} />
      </Stack>
    )],
    ...(data.submittedAt ? [[t("homework.table.submitted"), [
      formatHomeworkDate(data.submittedAt),
      data.submittedByParent
        ? t("homework.review.by-parent", { name: formatPersonName(data.submittedByFirstName, data.submittedByLastName) })
        : null,
    ].filter(Boolean).join(" · ")] as [string, ReactNode]] : []),
    [t("homework.assignment.deadline"), data.effectiveDueAt ? formatHomeworkDate(data.effectiveDueAt) : t("homework.no-due")],
  ] : [];

  return (
    <Dialog
      open={Boolean(assignmentId)}
      onClose={onClose}
      fullWidth
      maxWidth="lg"
      slotProps={{ paper: { sx: { height: { md: "min(86vh, 820px)" }, borderRadius: 3 } } }}
    >
      <DialogTitle sx={{ pr: 6, py: 1.75 }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: "center", minWidth: 0 }}>
          <Avatar sx={{ width: 36, height: 36, fontSize: 14, fontWeight: 700, bgcolor: "action.selected", color: "text.primary" }}>{initials}</Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ fontWeight: 800, lineHeight: 1.2 }} noWrap>{name || "—"}</Typography>
            {homeworkTitle && <Typography variant="body2" sx={{ color: "text.secondary" }} noWrap>{homeworkTitle}</Typography>}
          </Box>
        </Stack>
        <IconButton aria-label={t("homework.review.close")} onClick={onClose} sx={{ position: "absolute", right: 12, top: 12 }}>
          <Close />
        </IconButton>
      </DialogTitle>
      <Divider />

      <DialogContent sx={{ p: 0, display: "flex", minHeight: 0 }}>
        {isLoading && <Box sx={{ p: 3 }}><CircularProgress size={24} /></Box>}
        {isError && <Box sx={{ p: 3 }}><Alert severity="warning">{t("homework.gone")}</Alert></Box>}
        {data && (
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: { xs: "minmax(0, 1fr)", md: "minmax(0, 1fr) minmax(0, 1fr)" },
              gridTemplateRows: { md: "minmax(0, 1fr)" },
              width: "100%",
              minHeight: 0,
              overflowY: { xs: "auto", md: "hidden" },
            }}
          >
            {/* The work. Scrolls on its own when it is long. */}
            <Stack spacing={1.5} sx={{ p: 2.5, overflowY: { md: "auto" }, minHeight: 0 }}>
              <SectionLabel>{t("homework.review.submitted-work")}</SectionLabel>
              {!handedIn && (
                <Typography variant="body2" sx={{ color: "text.secondary" }}>{t("homework.review.not-handed-in")}</Typography>
              )}
              {handedIn && (
                <>
                  {questions.map((item, index) => <AnswerView key={item.id} item={item} number={index + 1} answer={answers.get(item.id)} />)}
                  <SectionLabel>{t("homework.review.general-answer")}</SectionLabel>
                  <AnswerBox empty={!data.responseText}>
                    <Typography variant="body2" sx={{ color: "inherit", whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
                      {data.responseText || t("homework.review.none")}
                    </Typography>
                  </AnswerBox>
                </>
              )}
            </Stack>

            {/* Where it stands, the decision, and the conversation that takes the height left. */}
            <Stack
              spacing={2}
              sx={{ p: 2.5, borderLeft: { md: 1 }, borderTop: { xs: 1, md: 0 }, borderColor: "divider", minHeight: 0, display: "flex" }}
            >
              <Box sx={{ display: "grid", gridTemplateColumns: "auto minmax(0, 1fr)", columnGap: 2, rowGap: 0.75, alignItems: "center", flexShrink: 0 }}>
                {facts.map(([label, value]) => (
                  <Fragment key={label}>
                    <Typography variant="body2" sx={{ color: "text.secondary" }}>{label}</Typography>
                    {typeof value === "string"
                      ? <Typography variant="body2" sx={{ fontWeight: 600, overflowWrap: "anywhere" }}>{value}</Typography>
                      : value}
                  </Fragment>
                ))}
              </Box>
              <Divider />

              {(canAccept || data.coachFeedback || data.returnNote) && (
                <Stack spacing={1.25} sx={{ flexShrink: 0 }}>
                  <SectionLabel>{t("homework.review.review-title")}</SectionLabel>
                  {data.coachFeedback && (
                    <Box>
                      <Typography variant="caption" sx={{ color: "text.secondary" }}>{t("homework.review.last-feedback")}</Typography>
                      <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{data.coachFeedback}</Typography>
                    </Box>
                  )}
                  {data.returnNote && data.returnedAt && (
                    <Box>
                      <Typography variant="caption" sx={{ color: "text.secondary" }}>
                        {t("homework.review.last-return", { date: formatHomeworkDate(data.returnedAt) })}
                      </Typography>
                      <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{data.returnNote}</Typography>
                    </Box>
                  )}
                  {canAccept && (
                    <TextField
                      multiline
                      minRows={3}
                      placeholder={t("homework.review.feedback-placeholder")}
                      value={feedback}
                      onChange={(event) => setFeedback(event.target.value)}
                      slotProps={{ htmlInput: { maxLength: 5000 } }}
                    />
                  )}
                </Stack>
              )}

              <Stack spacing={1} sx={{ flexGrow: 1, minHeight: { md: 0 }, display: "flex" }}>
                <SectionLabel icon={<ChatBubbleOutline />}>{t("homework.comments.title")}</SectionLabel>
                <CommentThread
                  assignmentId={data.id}
                  canWrite={!archived}
                  hideTitle
                  fill="always"
                />
              </Stack>
            </Stack>
          </Box>
        )}
      </DialogContent>

      {data && (
        <>
          <Divider />
          <DialogActions sx={{ px: 2.5, py: 1.5, justifyContent: "space-between", flexWrap: "wrap", rowGap: 1 }}>
            <Box>
              {onNext && (
                <Button color="primary" endIcon={<ArrowForward />} onClick={onNext} sx={{ fontWeight: 700 }}>
                  {t("homework.review.next-count", { count: nextCount })}
                </Button>
              )}
            </Box>
            <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", rowGap: 1 }}>
              {canAccept && (
                <Button variant="outlined" color="warning" startIcon={<Undo />} disabled={pending} onClick={() => setReturning(true)}>
                  {t("homework.review.return")}
                </Button>
              )}
              {canAccept && (
                <Button variant="contained" color="success" startIcon={<Check />} disabled={pending} onClick={() => review.mutate()}>
                  {t("homework.review.accept")}
                </Button>
              )}
            </Stack>
          </DialogActions>
        </>
      )}

      <Dialog open={returning} onClose={() => !sendBack.isPending && setReturning(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t("homework.review.return-title")}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>{t("homework.review.return-body")}</DialogContentText>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            label={t("homework.review.return-note-label")}
            placeholder={t("homework.review.return-note-placeholder")}
            value={returnNote}
            onChange={(event) => setReturnNote(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReturning(false)} disabled={sendBack.isPending}>{t("homework.review.return-cancel")}</Button>
          <Button
            variant="contained"
            color="warning"
            disabled={!returnNote.trim() || sendBack.isPending}
            onClick={() => sendBack.mutate()}
          >
            {t("homework.review.return-confirm")}
          </Button>
        </DialogActions>
      </Dialog>
    </Dialog>
  );
};
