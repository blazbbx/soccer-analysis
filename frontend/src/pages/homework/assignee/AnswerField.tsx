import { Button, Checkbox, FormControlLabel, Radio, RadioGroup, Stack, TextField, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { formatClipTime, type HomeworkAnswer, type HomeworkItem } from "../../../api/homework";
import type { ClipPlayerHandle } from "../shared/ClipBlockPlayer";

/** The answer input for one question, by its kind. Choice options are marked correct only after acceptance. */
export const AnswerField = ({
  item,
  items,
  answer,
  disabled,
  playerOf,
  onChange,
}: {
  item: HomeworkItem;
  items: HomeworkItem[];
  answer?: HomeworkAnswer;
  disabled: boolean;
  /** The player of a clip item on the page. A timestamp question marks on that one, it has no player of its own. */
  playerOf: (itemId: string) => ClipPlayerHandle | undefined;
  onChange: (answer: HomeworkAnswer) => void;
}) => {
  const { t } = useTranslation();
  const update = (patch: Partial<HomeworkAnswer>) => onChange({ itemId: item.id, ...answer, ...patch });
  const selected = answer?.selectedOptions ?? [];
  const correct = new Set(item.correctOptions ?? []);
  // Only on a closed answer: while it can still be changed, a key that slipped through must not show.
  const optionSx = (index: number) => (disabled && correct.has(index) ? { color: "success.main" } : undefined);

  if (item.questionKind === "SINGLE_CHOICE") {
    return (
      <RadioGroup
        value={selected[0] ?? ""}
        onChange={(event) => update({ selectedOptions: [Number(event.target.value)] })}
      >
        {(item.options ?? []).map((option, index) => (
          <FormControlLabel key={index} value={index} disabled={disabled} control={<Radio size="small" />} label={option} sx={optionSx(index)} />
        ))}
      </RadioGroup>
    );
  }
  if (item.questionKind === "MULTI_CHOICE") {
    return (
      <Stack>
        {(item.options ?? []).map((option, index) => (
          <FormControlLabel
            key={index}
            disabled={disabled}
            sx={optionSx(index)}
            control={(
              <Checkbox
                size="small"
                checked={selected.includes(index)}
                onChange={(event) => update({
                  selectedOptions: event.target.checked ? [...selected, index] : selected.filter((other) => other !== index),
                })}
              />
            )}
            label={option}
          />
        ))}
      </Stack>
    );
  }
  if (item.questionKind === "TIMESTAMP") {
    const clip = items.find((other) => other.id === item.refItemId);
    const clips = items.filter((other) => other.type === "CLIP");
    return (
      <Stack spacing={0.5}>
        {!disabled && clip?.clipId && (
          <Typography variant="caption" sx={{ color: "text.secondary" }}>
            {t("homework.assignment.mark-hint", { index: clips.indexOf(clip) + 1, name: clip.clipName ?? "" })}
          </Typography>
        )}
        <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap", rowGap: 0.5 }}>
          <Button
            size="small"
            variant="outlined"
            disabled={disabled || !clip?.clipId}
            onClick={() => {
              const moment = clip ? playerOf(clip.id)?.currentMs() : null;
              if (moment != null) update({ timestampMs: moment });
            }}
          >
            {t("homework.assignment.mark-time")}
          </Button>
          {answer?.timestampMs != null && (
            <>
              <Button
                size="small"
                onClick={() => {
                  if (clip && answer.timestampMs != null) playerOf(clip.id)?.seekTo(answer.timestampMs);
                }}
              >
                {t("homework.assignment.marked", { time: formatClipTime(answer.timestampMs) })}
              </Button>
              {!disabled && <Button size="small" onClick={() => update({ timestampMs: null })}>{t("homework.assignment.clear-mark")}</Button>}
            </>
          )}
        </Stack>
      </Stack>
    );
  }
  return (
    <TextField
      multiline
      minRows={2}
      fullWidth
      size="small"
      disabled={disabled}
      value={answer?.textAnswer ?? ""}
      onChange={(event) => update({ textAnswer: event.target.value })}
      slotProps={{ htmlInput: { maxLength: 10000 } }}
    />
  );
};
