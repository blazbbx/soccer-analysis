import { Fragment, useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import {
  Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Divider, Stack, TextField, Typography,
} from "@mui/material";
import { alpha } from "@mui/material/styles";
import { ChatBubbleOutline } from "@mui/icons-material";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import {
  assignmentKey,
  formatHomeworkDate,
  getAssignment,
  HOMEWORK_KEY,
  homeworkErrorMessage,
  homeworkErrorCode,
  STALE_WRITE,
  homeworkErrorStatus,
  isGoneError,
  markAssignmentViewed,
  submitAssignment,
  useAssignment,
  type Assignment,
  type HomeworkAnswer,
  closedNotSubmitted,
} from "../../../api/homework";
import { useSnackbar } from "../../../context/SnackbarContext";
import { useView } from "../../../context/ViewContext";
import { ROLES } from "../../../types/roles";
import { formatPersonName } from "../../../utils/stringUtils";
import { AssignmentStatusChip, DueLabel } from "../shared/AssignmentStatusChip";
import type { ClipPlayerHandle } from "../shared/ClipBlockPlayer";
import { CommentThread } from "../shared/CommentThread";
import { HomeworkBackButton } from "../shared/HomeworkBackButton";
import { HomeworkBlockView } from "../shared/HomeworkBlockView";
import { InnerBox, Section, SectionLabel } from "../shared/Section";
import { AnswerField } from "./AnswerField";

/**
 * The coach's answer on its own card at the top, not one line in a banner: this is what the player came back for.
 */
const CoachFeedback = ({ assignment }: { assignment: Assignment }) => {
  const { t } = useTranslation();
  if (assignment.status !== "COMPLETED" || assignment.reviewedAt == null) return null;
  const reviewer = formatPersonName(assignment.reviewedByFirstName, assignment.reviewedByLastName);
  return (
    <Box
      sx={(theme) => ({
        p: 2,
        borderRadius: 2,
        border: 1,
        borderLeft: 6,
        borderColor: "success.main",
        bgcolor: alpha(theme.palette.success.main, theme.palette.mode === "dark" ? 0.16 : 0.08),
      })}
    >
      <Typography sx={{ fontWeight: 800 }}>{t("homework.assignment.feedback-accepted")}</Typography>
      {assignment.coachFeedback && (
        <Typography sx={{ mt: 1, fontSize: 16, whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{assignment.coachFeedback}</Typography>
      )}
      <Typography variant="caption" component="div" sx={{ mt: 0.75, color: "text.secondary" }}>
        {[reviewer, formatHomeworkDate(assignment.reviewedAt)].filter(Boolean).join(" · ")}
      </Typography>
    </Box>
  );
};

/** Why the coach sent the work back, above everything else: this is what the player has to act on. */
const ReturnNotice = ({ assignment }: { assignment: Assignment }) => {
  const { t } = useTranslation();
  if (assignment.status !== "RETURNED" || !assignment.returnNote) return null;
  const coach = formatPersonName(assignment.returnedByFirstName, assignment.returnedByLastName);
  return (
    <Box
      sx={(theme) => ({
        p: 2,
        borderRadius: 2,
        border: 1,
        borderLeft: 6,
        borderColor: "warning.main",
        bgcolor: alpha(theme.palette.warning.main, theme.palette.mode === "dark" ? 0.16 : 0.08),
      })}
    >
      <Typography sx={{ fontWeight: 800 }}>{t("homework.assignment.returned-title")}</Typography>
      <Typography sx={{ mt: 1, fontSize: 16, whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{assignment.returnNote}</Typography>
      <Typography variant="caption" component="div" sx={{ mt: 0.75, color: "text.secondary" }}>
        {[coach, formatHomeworkDate(assignment.returnedAt)].filter(Boolean).join(" · ")}
      </Typography>
      <Typography variant="body2" sx={{ mt: 1, color: "text.secondary" }}>{t("homework.assignment.returned-hint")}</Typography>
    </Box>
  );
};

export const AssignmentPage = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { showError } = useSnackbar();
  const { activeView } = useView();
  // The staff may open this page too, but only a recipient answers or counts as having opened it.
  const recipientView = activeView === ROLES.PLAYER || activeView === ROLES.PARENT;
  const queryClient = useQueryClient();
  const { assignmentId } = useParams<{ assignmentId: string }>();
  const { data, isLoading, isError, error } = useAssignment(assignmentId);

  // Nothing is stored before the hand-in: what is typed lives only here until the button sends it.
  const [responseText, setResponseText] = useState("");
  const [answers, setAnswers] = useState<HomeworkAnswer[]>([]);
  const [stale, setStale] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const dirty = useRef(false);

  const adopt = useCallback((assignment: Assignment) => {
    setResponseText(assignment.responseText ?? "");
    setAnswers(assignment.answers);
    dirty.current = false;
    setStale(false);
  }, []);

  const loadedId = data?.id;
  const loadedStatus = data?.status;
  useEffect(() => {
    // Never over the person's typing: a refetch must not lose text.
    if (data && !dirty.current) adopt(data);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadedId, loadedStatus, adopt]);

  // Leaving the page with typed answers loses them, so the browser asks first.
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty.current) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, []);

  // Only this call marks it opened, for the coach's table: the GET has no side effect, and only a recipient counts.
  useEffect(() => {
    if (recipientView && loadedId) void markAssignmentViewed(loadedId).catch(() => undefined);
  }, [recipientView, loadedId]);
  const players = useRef(new Map<string, ClipPlayerHandle>());

  /** The coach changed the homework: the new questions come in, and the answers to the ones still there stay. */
  const loadNew = async () => {
    if (!assignmentId) return;
    const fresh = await queryClient.fetchQuery({
      queryKey: assignmentKey(assignmentId),
      queryFn: ({ signal }) => getAssignment(assignmentId, signal),
    });
    const questionIds = new Set(fresh.items.filter((item) => item.type === "QUESTION").map((item) => item.id));
    setAnswers((current) => current.filter((answer) => questionIds.has(answer.itemId)));
    setStale(false);
  };

  const submit = useMutation({
    mutationFn: async () => {
      if (!assignmentId || !data) throw new Error("no assignment");
      return submitAssignment(assignmentId, { responseText: responseText || null, answers, expectedUpdatedAt: data.homeworkUpdatedAt });
    },
    onSuccess: (saved) => {
      setConfirming(false);
      dirty.current = false;
      if (assignmentId) queryClient.setQueryData(assignmentKey(assignmentId), saved);
      void queryClient.invalidateQueries({ queryKey: HOMEWORK_KEY });
    },
    onError: (failure) => {
      setConfirming(false);
      if (homeworkErrorCode(failure) === STALE_WRITE) {
        // The questions changed: the banner offers to load them.
        setStale(true);
      } else if (homeworkErrorStatus(failure) === 409 && assignmentId) {
        // Handed in from elsewhere, closed, or no longer on the team: the fresh state decides what may still be done.
        void queryClient.invalidateQueries({ queryKey: assignmentKey(assignmentId) });
      }
      showError(homeworkErrorMessage(failure, t("homework.save-failed")));
    },
  });

  if (isLoading) return <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}><CircularProgress /></Box>;
  if (isError || !data) {
    return (
      <Box sx={{ p: 3 }}>
        <HomeworkBackButton onClick={() => navigate("/homework")} />
        <Alert severity={isGoneError(error) ? "warning" : "error"}>{t(isGoneError(error) ? "homework.gone" : "homework.load-failed")}</Alert>
      </Box>
    );
  }

  const clipIds = data.items.filter((item) => item.type === "CLIP").map((item) => item.id);
  // Back to the tab this assignment is on. The staff came from the homework's own page.
  const listTab = data.status === "SUBMITTED"
    ? "submitted"
    : data.status === "COMPLETED" || data.homeworkStatus !== "PUBLISHED"
      ? "done"
      : "todo";
  const goBack = () => navigate(recipientView ? `/homework?tab=${listTab}` : `/homework/${data.homeworkId}`);
  const writable = data.writable && recipientView;
  const answerOf = (itemId: string) => answers.find((answer) => answer.itemId === itemId);
  const setAnswer = (answer: HomeworkAnswer) => {
    setAnswers((current) => [...current.filter((other) => other.itemId !== answer.itemId), answer]);
    dirty.current = true;
  };
  const questions = data.items.filter((item) => item.type === "QUESTION");
  const notSubmitted = closedNotSubmitted(data.homeworkStatus === "ARCHIVED", data.status);
  const canComment = data.homeworkStatus !== "ARCHIVED";

  const questionNumber = (itemId: string) => questions.findIndex((question) => question.id === itemId) + 1;
  const coachName = formatPersonName(data.createdByFirstName, data.createdByLastName);
  const statusRows: [string, ReactNode][] = [
    [t("homework.assignment.status"), (
      <Stack direction="row" spacing={0.5} sx={{ flexWrap: "wrap", rowGap: 0.5 }}>
        <AssignmentStatusChip status={data.status} notSubmitted={notSubmitted ? "recipient" : undefined} />
        <DueLabel overdue={data.overdue} dueSoon={data.dueSoon} late={data.late} lateBlocked={data.lateBlocked} dueAt={data.effectiveDueAt} />
      </Stack>
    )],
    [t("homework.assignment.deadline"), data.effectiveDueAt ? formatHomeworkDate(data.effectiveDueAt) : t("homework.no-due")],
    [t("homework.assignment.team"), data.teamName ?? ""],
    ...(coachName ? [[t("homework.assignment.coach"), coachName] as [string, ReactNode]] : []),
  ];

  return (
    <Box sx={{ py: { xs: 2, md: 2.5 }, px: { xs: 2, md: 3 } }}>
      <HomeworkBackButton onClick={goBack} />
      <Typography sx={{ fontSize: { xs: 22, md: 26 }, fontWeight: 800, overflowWrap: "anywhere", mb: 2 }}>{data.title}</Typography>

      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "minmax(0, 1fr)", md: "minmax(0, 1fr) minmax(320px, 400px)" },
          gap: 2,
          // Both columns as tall as the taller one, so their bottoms line up.
          alignItems: "stretch",
        }}
      >
        {/* The task and the answer. */}
        <Stack spacing={2} sx={{ minWidth: 0 }}>
          <CoachFeedback assignment={data} />
          <ReturnNotice assignment={data} />
          {!writable && data.status !== "COMPLETED" && data.status !== "SUBMITTED" && (
            <Alert severity="info">{t(notSubmitted ? "homework.assignment.not-submitted" : "homework.assignment.read-only")}</Alert>
          )}
          {stale && (
            <Alert
              severity="warning"
              action={<Button color="inherit" size="small" onClick={() => void loadNew()}>{t("homework.assignment.load-new")}</Button>}
            >
              {t("homework.assignment.stale")}
            </Alert>
          )}

          <Section sx={{ flexGrow: 1 }}>
            {data.description && <Typography sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{data.description}</Typography>}

            {data.items.map((item) => (item.type === "QUESTION" ? (
              <InnerBox key={item.id}>
                <Stack spacing={1}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: "baseline" }}>
                    <SectionLabel>{t("homework.assignment.question-number", { index: questionNumber(item.id) })}</SectionLabel>
                    {item.required && (
                      <Typography variant="caption" sx={{ color: "warning.main" }}>{t("homework.assignment.required")}</Typography>
                    )}
                  </Stack>
                  <Typography sx={{ fontWeight: 700, whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{item.body}</Typography>
                  <AnswerField
                    item={item}
                    items={data.items}
                    answer={answerOf(item.id)}
                    disabled={!writable}
                    playerOf={(itemId) => players.current.get(itemId)}
                    onChange={setAnswer}
                  />
                </Stack>
              </InnerBox>
            ) : (
              <HomeworkBlockView
                key={item.id}
                item={item}
                clipNumber={clipIds.length > 1 && item.type === "CLIP" ? clipIds.indexOf(item.id) + 1 : undefined}
                playerRef={(handle) => {
                  if (handle) players.current.set(item.id, handle);
                  else players.current.delete(item.id);
                }}
              />
            )))}

            {(writable || data.responseText) && (
              <Stack spacing={1}>
                <SectionLabel>{t("homework.assignment.general-answer-optional")}</SectionLabel>
                <TextField
                  multiline
                  minRows={3}
                  disabled={!writable}
                  placeholder={t("homework.assignment.general-answer-placeholder")}
                  value={responseText}
                  onChange={(event) => {
                    setResponseText(event.target.value);
                    dirty.current = true;
                  }}
                  slotProps={{ htmlInput: { maxLength: 10000 } }}
                />
              </Stack>
            )}

            {writable && (
              <>
                <Divider />
                <Stack direction="row" spacing={2} sx={{ alignItems: "center", justifyContent: "space-between" }}>
                  <Typography variant="caption" sx={{ color: "text.secondary" }}>{t("homework.assignment.saved-on-submit")}</Typography>
                  <Button variant="contained" disabled={submit.isPending || stale} onClick={() => setConfirming(true)}>
                    {t("homework.assignment.submit")}
                  </Button>
                </Stack>
              </>
            )}
          </Section>
        </Stack>

        {/*
          Where it stands, and the conversation with the coach. On a wide screen the column is as tall as the task and the
          comments fill what is left, scrolling inside: a long thread never makes the page taller than the task.
        */}
        <Box sx={{ position: "relative", minWidth: 0, minHeight: { md: 560 } }}>
        <Stack spacing={2} sx={{ position: { md: "absolute" }, inset: { md: 0 } }}>
          <Section title={t("homework.assignment.status-title")}>
            <Box sx={{ display: "grid", gridTemplateColumns: "auto minmax(0, 1fr)", columnGap: 2, rowGap: 0.75, alignItems: "center" }}>
              {statusRows.map(([label, value]) => (
                <Fragment key={label}>
                  <Typography variant="body2" sx={{ color: "text.secondary" }}>{label}</Typography>
                  {typeof value === "string"
                    ? <Typography variant="body2" sx={{ fontWeight: 600, overflowWrap: "anywhere" }}>{value}</Typography>
                    : value}
                </Fragment>
              ))}
            </Box>
          </Section>

          <Section title={t("homework.comments.title")} icon={<ChatBubbleOutline />} fill>
            <CommentThread assignmentId={data.id} canWrite={canComment} hideTitle fill />
          </Section>
        </Stack>
        </Box>
      </Box>

      <Dialog open={confirming} onClose={() => setConfirming(false)} fullWidth maxWidth="xs">
        <DialogTitle>{t("homework.assignment.submit")}</DialogTitle>
        <DialogContent><DialogContentText>{t("homework.assignment.submit-confirm")}</DialogContentText></DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirming(false)}>{t("homework.editor.cancel")}</Button>
          <Button variant="contained" disabled={submit.isPending} onClick={() => submit.mutate()}>
            {t("homework.assignment.submit")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
