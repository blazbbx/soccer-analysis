import { useEffect, useMemo, useState } from "react";
import {
  Alert, Box, Button, Checkbox, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Paper, Stack, Tab, Tabs, TextField, Typography,
} from "@mui/material";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  createHomework,
  deleteHomework,
  fromLocalInput,
  HOMEWORK_KEY,
  homeworkDetailKey,
  homeworkErrorMessage,
  homeworkErrorStatus,
  publishHomework,
  toLocalInput,
  updateHomework,
  useHomeworkDetail,
  type HomeworkDetail,
  type HomeworkItem,
  type HomeworkItemBody,
  type UpsertHomeworkBody,
} from "../../../api/homework";
import { useSnackbar } from "../../../context/SnackbarContext";
import { ConfirmDeleteDialog } from "../shared/ConfirmDeleteDialog";
import { AnswerField } from "../assignee/AnswerField";
import { HomeworkBackButton } from "../shared/HomeworkBackButton";
import { HomeworkBlockView } from "../shared/HomeworkBlockView";
import { HomeworkDateTimeField } from "../shared/HomeworkDateTimeField";
import { BlockList, type DraftBlock } from "./blocks/BlockList";
import { PublishDialog } from "./PublishDialog";
import { RecipientPicker } from "./RecipientPicker";

const fromItem = (item: HomeworkItem): DraftBlock => ({
  key: item.id,
  id: item.id,
  type: item.type,
  body: item.body ?? "",
  clipId: item.clipId,
  clipName: item.clipName,
  clipRenderStatus: item.clipRenderStatus,
  focusSeconds: item.focusMs != null ? String(item.focusMs / 1000) : "",
  questionKind: item.questionKind ?? "FREE_TEXT",
  options: item.options ?? ["", ""],
  correctOptions: item.correctOptions ?? [0],
  required: item.required,
  refKey: item.refItemId ?? "",
});

const toItemBody = (block: DraftBlock, blocks: DraftBlock[]): HomeworkItemBody => {
  const target = blocks.find((other) => other.key === block.refKey);
  const timestamp = block.type === "QUESTION" && block.questionKind === "TIMESTAMP";
  // Only a choice question sends options: the server validates every option it gets, even on a block that ignores them.
  const choice = block.type === "QUESTION" && (block.questionKind === "SINGLE_CHOICE" || block.questionKind === "MULTI_CHOICE");
  return {
    id: block.id ?? null,
    clientKey: block.id ? null : block.key,
    type: block.type,
    body: block.body,
    clipId: block.clipId ?? null,
    focusMs: block.focusSeconds === "" ? null : Math.round(Number(block.focusSeconds) * 1000),
    questionKind: block.type === "QUESTION" ? block.questionKind : null,
    options: choice ? block.options : null,
    correctOptions: choice ? block.correctOptions : null,
    required: block.required,
    refItemId: timestamp && target?.id ? target.id : null,
    refClientKey: timestamp && target && !target.id ? target.key : null,
  };
};

const previewItem = (block: DraftBlock, position: number): HomeworkItem => ({
  id: block.key,
  position,
  type: block.type,
  body: block.body,
  clipId: block.clipId,
  clipName: block.clipName,
  clipRenderStatus: block.clipRenderStatus,
  focusMs: block.focusSeconds === "" ? null : Number(block.focusSeconds) * 1000,
  questionKind: block.questionKind,
  options: block.options,
  required: block.required,
});

export const HomeworkEditor = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { showError } = useSnackbar();
  const queryClient = useQueryClient();
  const { homeworkId } = useParams<{ homeworkId: string }>();
  const [searchParams] = useSearchParams();
  const detail = useHomeworkDetail(homeworkId);
  const stored = detail.data;
  const teamId = stored?.teamId ?? searchParams.get("teamId") ?? "";

  const [step, setStep] = useState<"content" | "recipients">("content");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [dueAt, setDueAt] = useState("");
  const [allowLate, setAllowLate] = useState(true);
  const [blocks, setBlocks] = useState<DraftBlock[]>([]);
  const [entireRoster, setEntireRoster] = useState(true);
  const [recipients, setRecipients] = useState<string[]>([]);
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const load = (homework: HomeworkDetail) => {
    setTitle(homework.title);
    setDescription(homework.description ?? "");
    setDueAt(toLocalInput(homework.dueAt));
    setAllowLate(homework.allowLate);
    setBlocks(homework.items.map(fromItem));
    setEntireRoster(homework.draftEntireRoster);
    setRecipients(homework.draftRecipientIds);
    setUpdatedAt(homework.updatedAt);
  };

  useEffect(() => {
    if (stored && stored.updatedAt !== updatedAt) load(stored);
    // Only a newly loaded version resets the form, never the person's typing.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stored?.updatedAt]);

  const isDraft = !stored || stored.status === "DRAFT";
  const body = useMemo<UpsertHomeworkBody>(() => ({
    title,
    description: description || null,
    dueAt: fromLocalInput(dueAt),
    allowLate,
    items: blocks.map((block) => toItemBody(block, blocks)),
    entireRoster,
    playerProfileIds: entireRoster ? [] : recipients,
    expectedUpdatedAt: updatedAt,
  }), [title, description, dueAt, allowLate, blocks, entireRoster, recipients, updatedAt]);

  const saveRequest = () => (stored
    ? updateHomework(stored.teamId, stored.id, body)
    : homeworkId
      ? Promise.reject(new Error("not loaded"))
      : createHomework(teamId, body));

  const finish = (saved: HomeworkDetail) => {
    queryClient.setQueryData(homeworkDetailKey(saved.id), saved);
    void queryClient.invalidateQueries({ queryKey: HOMEWORK_KEY });
  };

  const fail = (failure: unknown) => {
    const message = homeworkErrorMessage(failure, t("homework.save-failed"));
    // Inside the dialog when it is open, otherwise as one message: the buttons are at the bottom of a long form.
    if (publishing) setError(message);
    else showError(message);
    if (homeworkErrorStatus(failure) === 409 && homeworkId) {
      void queryClient.invalidateQueries({ queryKey: homeworkDetailKey(homeworkId) });
    }
  };

  const save = useMutation({
    mutationFn: saveRequest,
    onSuccess: (saved) => {
      finish(saved);
      setError(null);
      navigate(saved.status === "DRAFT" ? `/homework?teamId=${saved.teamId}&tab=draft` : `/homework/${saved.id}`);
    },
    onError: fail,
  });

  // Saved first, so what goes out is what the coach sees, and the publish uses the new version.
  const publish = useMutation({
    mutationFn: async () => {
      const saved = await saveRequest();
      // The stored version reloads the form, with real ids. A new homework also moves to its own address,
      // so trying again after a refused publish updates this draft instead of creating a second one.
      finish(saved);
      if (!homeworkId) navigate(`/homework/${saved.id}/edit`, { replace: true });
      return publishHomework(saved.teamId, saved.id, {
        entireRoster,
        playerProfileIds: entireRoster ? [] : recipients,
        expectedUpdatedAt: saved.updatedAt,
      });
    },
    onSuccess: (published) => {
      finish(published);
      navigate(`/homework/${published.id}`);
    },
    onError: fail,
  });

  // Only a saved draft: an unsaved one is simply left with the cancel button.
  const remove = useMutation({
    mutationFn: () => deleteHomework(teamId, stored?.id ?? ""),
    onSuccess: () => {
      setDeleting(false);
      void queryClient.invalidateQueries({ queryKey: HOMEWORK_KEY });
      navigate(`/homework?teamId=${teamId}&tab=draft`);
    },
    onError: (failure) => {
      setDeleting(false);
      fail(failure);
    },
  });

  if (homeworkId && detail.isLoading) {
    return <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}><CircularProgress /></Box>;
  }
  if (homeworkId && detail.isError) {
    return <Box sx={{ p: 3 }}><Alert severity="warning">{t("homework.gone")}</Alert></Box>;
  }

  // Back to where this homework lives, with its team, whatever way the person got here.
  const leave = () => {
    if (stored && stored.status !== "DRAFT") navigate(`/homework/${stored.id}`);
    else navigate(`/homework?teamId=${teamId}${stored ? "&tab=draft" : ""}`);
  };

  /** Says which item is wrong, which the server's general answer cannot. */
  const blockProblem = (): string | null => {
    for (const [at, block] of blocks.entries()) {
      const index = at + 1;
      if (block.type === "TEXT" && !block.body.trim()) return t("homework.editor.error-text", { index });
      if (block.type === "CLIP" && !block.clipId && !block.id) return t("homework.editor.error-clip", { index });
      if (block.type !== "QUESTION") continue;
      if (!block.body.trim()) return t("homework.editor.error-question", { index });
      if (block.questionKind === "TIMESTAMP" && !blocks.some((other) => other.key === block.refKey && other.type === "CLIP")) {
        return t("homework.editor.error-timestamp", { index });
      }
      if (block.questionKind === "SINGLE_CHOICE" || block.questionKind === "MULTI_CHOICE") {
        if (block.options.some((option) => !option.trim())) return t("homework.editor.error-options", { index });
        if (block.correctOptions.length === 0) return t("homework.editor.error-correct", { index });
      }
    }
    return null;
  };

  const whenValid = (action: () => void) => {
    const problem = blockProblem();
    if (problem) {
      showError(problem);
      return;
    }
    setError(null);
    action();
  };

  const pending = save.isPending || publish.isPending;
  return (
    <Box sx={{ py: { xs: 2, md: 2.5 }, px: { xs: 2, md: 3 }, maxWidth: 860 }}>
      <HomeworkBackButton onClick={leave} />
      <Typography sx={{ fontSize: { xs: 22, md: 26 }, fontWeight: 800, mb: 1.5 }}>
        {t(homeworkId ? "homework.editor.edit-title" : "homework.editor.new-title")}
      </Typography>
      {isDraft && (
        <Tabs value={step} onChange={(_event, next) => setStep(next)} sx={{ mb: 2 }}>
          <Tab value="content" label={t("homework.editor.content")} />
          <Tab value="recipients" label={t("homework.editor.recipients")} />
        </Tabs>
      )}
      {step === "content" && (
        <Stack spacing={2}>
          <TextField required label={t("homework.editor.title")} value={title} onChange={(event) => setTitle(event.target.value)} slotProps={{ htmlInput: { maxLength: 200 } }} />
          <TextField multiline minRows={2} label={t("homework.editor.description")} value={description} onChange={(event) => setDescription(event.target.value)} slotProps={{ htmlInput: { maxLength: 10000 } }} />
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <HomeworkDateTimeField label={t("homework.editor.due")} value={dueAt} onChange={setDueAt} />
            <FormControlLabel
              control={<Checkbox checked={allowLate} onChange={(event) => setAllowLate(event.target.checked)} />}
              label={t("homework.editor.allow-late")}
            />
          </Stack>
          <BlockList blocks={blocks} teamId={teamId} onChange={setBlocks} />
        </Stack>
      )}
      {step === "recipients" && teamId && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <RecipientPicker
            teamId={teamId}
            entireRoster={entireRoster}
            selectedIds={recipients}
            onChange={(entire, ids) => {
              setEntireRoster(entire);
              setRecipients(ids);
            }}
          />
        </Paper>
      )}
      <Stack direction="row" spacing={1} sx={{ mt: 2.5, flexWrap: "wrap", rowGap: 1 }}>
        <Button onClick={() => setPreviewing(true)}>{t("homework.editor.preview")}</Button>
        {stored && isDraft && (
          <Button color="error" disabled={pending || remove.isPending} onClick={() => setDeleting(true)}>{t("homework.editor.delete-draft")}</Button>
        )}
        <Box sx={{ flexGrow: 1 }} />
        <Button onClick={leave}>{t("homework.editor.cancel")}</Button>
        <Button variant="outlined" disabled={pending || !title.trim()} onClick={() => whenValid(() => save.mutate())}>
          {t(isDraft ? "homework.editor.save-draft" : "homework.editor.save")}
        </Button>
        {isDraft && (
          <Button variant="contained" disabled={pending || !title.trim()} onClick={() => whenValid(() => setPublishing(true))}>{t("homework.editor.publish-now")}</Button>
        )}
      </Stack>
      <PublishDialog
        open={publishing}
        entireRoster={entireRoster}
        recipientCount={recipients.length}
        itemCount={blocks.length}
        pending={pending}
        error={publishing ? error : null}
        onClose={() => setPublishing(false)}
        onConfirm={() => publish.mutate()}
      />
      <ConfirmDeleteDialog
        open={deleting}
        title={t("homework.editor.delete-draft")}
        body={t("homework.delete-draft-body", { title: stored?.title ?? "" })}
        pending={remove.isPending}
        onClose={() => setDeleting(false)}
        onConfirm={() => remove.mutate()}
      />
      <Dialog open={previewing} onClose={() => setPreviewing(false)} fullWidth maxWidth="md">
        <DialogTitle>{title || t("homework.editor.preview")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2}>
            {description && <Typography sx={{ whiteSpace: "pre-wrap" }}>{description}</Typography>}
            {blocks.map((block, index) => (block.type === "CLIP" && !block.clipId && !block.id ? (
              // Not chosen yet. Only a saved block without a clip means that its clip was deleted.
              <Typography key={block.key} variant="body2" sx={{ color: "text.secondary", fontStyle: "italic" }}>
                {t("homework.editor.no-clip-selected")}
              </Typography>
            ) : (
              <HomeworkBlockView key={block.key} item={previewItem(block, index)}>
                {/* The answer input the player sees, disabled. The preview item has no correct options, so none is marked. */}
                {block.type === "QUESTION" && block.questionKind !== "TIMESTAMP" && (
                  <AnswerField item={previewItem(block, index)} items={[]} disabled playerOf={() => undefined} onChange={() => {}} />
                )}
              </HomeworkBlockView>
            )))}
          </Stack>
        </DialogContent>
        <DialogActions><Button onClick={() => setPreviewing(false)}>{t("homework.editor.close")}</Button></DialogActions>
      </Dialog>
    </Box>
  );
};
