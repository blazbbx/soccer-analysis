import { useState } from "react";
import {
  Box, Button, Checkbox, FormControlLabel, IconButton, MenuItem, Paper, Radio, Stack, Switch, TextField, Tooltip, Typography,
} from "@mui/material";
import { Add as AddIcon, ArrowDownward, ArrowUpward, Delete as DeleteIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import type { HomeworkItemType, HomeworkQuestionKind } from "../../../../api/homework";
import { ClipPickerDialog } from "../ClipPickerDialog";

const KINDS: HomeworkQuestionKind[] = ["FREE_TEXT", "SINGLE_CHOICE", "MULTI_CHOICE", "TIMESTAMP"];

/** One item in the editor. {@code key} is stable for the row: the saved id, or a key made here for a new one. */
export interface DraftBlock {
  key: string;
  id?: string;
  type: HomeworkItemType;
  body: string;
  clipId?: string | null;
  clipName?: string | null;
  clipRenderStatus?: string | null;
  focusSeconds: string;
  questionKind: HomeworkQuestionKind;
  options: string[];
  correctOptions: number[];
  required: boolean;
  refKey: string;
}

export const newBlock = (type: HomeworkItemType): DraftBlock => ({
  key: crypto.randomUUID(),
  type,
  body: "",
  focusSeconds: "",
  questionKind: "FREE_TEXT",
  options: ["", ""],
  correctOptions: [0],
  required: true,
  refKey: "",
});

const TextBlockEditor = ({ block, onChange }: { block: DraftBlock; onChange: (patch: Partial<DraftBlock>) => void }) => {
  const { t } = useTranslation();
  return (
    <TextField
      label={t("homework.editor.text")}
      multiline
      minRows={2}
      fullWidth
      size="small"
      value={block.body}
      onChange={(event) => onChange({ body: event.target.value })}
      slotProps={{ htmlInput: { maxLength: 5000 } }}
    />
  );
};

const ClipBlockEditor = ({
  block,
  teamId,
  onChange,
}: {
  block: DraftBlock;
  teamId: string;
  onChange: (patch: Partial<DraftBlock>) => void;
}) => {
  const { t } = useTranslation();
  const [picking, setPicking] = useState(false);
  return (
    <Stack spacing={1}>
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
        <Typography variant="body2" sx={{ fontWeight: 600, fontStyle: block.clipId ? "normal" : "italic" }}>
          {block.clipId ? (block.clipName ?? block.clipId) : (block.id ? t("homework.editor.clip-deleted") : "—")}
        </Typography>
        <Button size="small" onClick={() => setPicking(true)}>
          {t(block.clipId ? "homework.editor.change-clip" : "homework.editor.pick-clip")}
        </Button>
      </Stack>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
        <TextField
          size="small"
          fullWidth
          label={t("homework.editor.clip-caption")}
          value={block.body}
          onChange={(event) => onChange({ body: event.target.value })}
          slotProps={{ htmlInput: { maxLength: 5000 } }}
        />
        <TextField
          size="small"
          type="number"
          sx={{ width: { sm: 150 }, flexShrink: 0 }}
          label={t("homework.editor.focus")}
          value={block.focusSeconds}
          onChange={(event) => onChange({ focusSeconds: event.target.value })}
          slotProps={{ htmlInput: { min: 0, step: 1 } }}
        />
      </Stack>
      <ClipPickerDialog
        open={picking}
        teamId={teamId}
        onClose={() => setPicking(false)}
        onPick={(clip) => {
          onChange({ clipId: clip.id, clipName: clip.name, clipRenderStatus: clip.renderStatus });
          setPicking(false);
        }}
      />
    </Stack>
  );
};

const QuestionBlockEditor = ({
  block,
  clipBlocks,
  onChange,
}: {
  block: DraftBlock;
  clipBlocks: DraftBlock[];
  onChange: (patch: Partial<DraftBlock>) => void;
}) => {
  const { t } = useTranslation();
  const choice = block.questionKind === "SINGLE_CHOICE" || block.questionKind === "MULTI_CHOICE";
  const setOption = (index: number, value: string) =>
    onChange({ options: block.options.map((option, at) => (at === index ? value : option)) });
  const toggleCorrect = (index: number) => {
    if (block.questionKind === "SINGLE_CHOICE") {
      onChange({ correctOptions: [index] });
      return;
    }
    onChange({
      correctOptions: block.correctOptions.includes(index)
        ? block.correctOptions.filter((other) => other !== index)
        : [...block.correctOptions, index],
    });
  };
  const removeOption = (index: number) =>
    onChange({
      options: block.options.filter((_option, at) => at !== index),
      correctOptions: block.correctOptions.filter((other) => other !== index).map((other) => (other > index ? other - 1 : other)),
    });

  return (
    <Stack spacing={1}>
      <TextField
        label={t("homework.editor.question")}
        multiline
        fullWidth
        size="small"
        value={block.body}
        onChange={(event) => onChange({ body: event.target.value })}
        slotProps={{ htmlInput: { maxLength: 5000 } }}
      />
      <Stack direction="row" spacing={2} sx={{ alignItems: "center", flexWrap: "wrap" }}>
        <TextField
          select
          size="small"
          sx={{ minWidth: 200 }}
          label={t("homework.editor.question-kind")}
          value={block.questionKind}
          onChange={(event) => onChange({ questionKind: event.target.value as HomeworkQuestionKind, correctOptions: [0] })}
        >
          {KINDS.map((kind) => <MenuItem key={kind} value={kind}>{t(`homework.editor.kind-${kind}`)}</MenuItem>)}
        </TextField>
        <FormControlLabel
          control={<Switch checked={block.required} onChange={(event) => onChange({ required: event.target.checked })} />}
          label={t("homework.editor.required")}
        />
      </Stack>
      <Typography variant="caption" sx={{ color: "text.secondary" }}>
        {t(block.required ? "homework.editor.required-hint" : "homework.editor.optional-hint")}
        {block.questionKind === "TIMESTAMP" ? ` ${t("homework.editor.timestamp-hint")}` : ""}
      </Typography>
      {block.questionKind === "TIMESTAMP" && (
        clipBlocks.length === 0 ? (
          <Typography variant="body2" color="error">{t("homework.editor.no-clip-block")}</Typography>
        ) : (
          <TextField
            select
            size="small"
            label={t("homework.editor.timestamp-clip")}
            value={block.refKey}
            onChange={(event) => onChange({ refKey: event.target.value })}
          >
            {clipBlocks.map((clip, index) => (
              <MenuItem key={clip.key} value={clip.key}>
                {t("homework.editor.clip-option", { index: index + 1, name: clip.clipName ?? "" })}
              </MenuItem>
            ))}
          </TextField>
        )
      )}
      {choice && (
        <Stack spacing={0.75}>
          {block.options.map((option, index) => (
            <Stack key={index} direction="row" spacing={1} sx={{ alignItems: "center" }}>
              <Tooltip title={t("homework.editor.correct")}>
                {block.questionKind === "SINGLE_CHOICE"
                  ? <Radio size="small" checked={block.correctOptions.includes(index)} onChange={() => toggleCorrect(index)} />
                  : <Checkbox size="small" checked={block.correctOptions.includes(index)} onChange={() => toggleCorrect(index)} />}
              </Tooltip>
              <TextField
                size="small"
                fullWidth
                label={t("homework.editor.option", { index: index + 1 })}
                value={option}
                onChange={(event) => setOption(index, event.target.value)}
                slotProps={{ htmlInput: { maxLength: 200 } }}
              />
              <IconButton size="small" disabled={block.options.length <= 2} aria-label={t("homework.editor.remove")} onClick={() => removeOption(index)}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Stack>
          ))}
          {block.options.length < 6 && (
            <Button size="small" startIcon={<AddIcon />} sx={{ alignSelf: "flex-start" }} onClick={() => onChange({ options: [...block.options, ""] })}>
              {t("homework.editor.add-option")}
            </Button>
          )}
        </Stack>
      )}
    </Stack>
  );
};

export const BlockList = ({
  blocks,
  teamId,
  onChange,
}: {
  blocks: DraftBlock[];
  teamId: string;
  onChange: (blocks: DraftBlock[]) => void;
}) => {
  const { t } = useTranslation();
  const clipBlocks = blocks.filter((block) => block.type === "CLIP");
  const patch = (key: string, change: Partial<DraftBlock>) =>
    onChange(blocks.map((block) => (block.key === key ? { ...block, ...change } : block)));
  const move = (index: number, by: number) => {
    const next = [...blocks];
    const [moved] = next.splice(index, 1);
    next.splice(index + by, 0, moved);
    onChange(next);
  };

  return (
    <Stack spacing={1.25}>
      {blocks.length === 0 && <Typography variant="body2" sx={{ color: "text.secondary" }}>{t("homework.editor.no-blocks")}</Typography>}
      {blocks.map((block, index) => (
        <Paper key={block.key} variant="outlined" sx={{ p: 1.5, display: "flex", gap: 1, alignItems: "flex-start" }}>
          <Box sx={{ flexGrow: 1, minWidth: 0 }}>
            {block.type === "TEXT" && <TextBlockEditor block={block} onChange={(change) => patch(block.key, change)} />}
            {block.type === "CLIP" && <ClipBlockEditor block={block} teamId={teamId} onChange={(change) => patch(block.key, change)} />}
            {block.type === "QUESTION" && (
              <QuestionBlockEditor block={block} clipBlocks={clipBlocks} onChange={(change) => patch(block.key, change)} />
            )}
          </Box>
          <Box sx={{ display: "flex", flexShrink: 0 }}>
            <IconButton size="small" disabled={index === 0} aria-label={t("homework.editor.move-up")} onClick={() => move(index, -1)}><ArrowUpward fontSize="small" /></IconButton>
            <IconButton size="small" disabled={index === blocks.length - 1} aria-label={t("homework.editor.move-down")} onClick={() => move(index, 1)}><ArrowDownward fontSize="small" /></IconButton>
            <IconButton size="small" aria-label={t("homework.editor.remove")} onClick={() => onChange(blocks.filter((other) => other.key !== block.key))}><DeleteIcon fontSize="small" /></IconButton>
          </Box>
        </Paper>
      ))}
      <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
        <Button size="small" startIcon={<AddIcon />} disabled={blocks.length >= 30} onClick={() => onChange([...blocks, newBlock("TEXT")])}>{t("homework.editor.add-text")}</Button>
        <Button size="small" startIcon={<AddIcon />} disabled={blocks.length >= 30} onClick={() => onChange([...blocks, newBlock("CLIP")])}>{t("homework.editor.add-clip")}</Button>
        <Button size="small" startIcon={<AddIcon />} disabled={blocks.length >= 30} onClick={() => onChange([...blocks, newBlock("QUESTION")])}>{t("homework.editor.add-question")}</Button>
      </Stack>
    </Stack>
  );
};
