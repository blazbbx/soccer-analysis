import { useMemo, useState, type ReactNode } from "react";
import {
  Alert, Avatar, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Menu, MenuItem, Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from "@mui/material";
import { alpha } from "@mui/material/styles";
import { CheckBoxOutlineBlank, GroupOutlined, MoreVert, RadioButtonUnchecked } from "@mui/icons-material";
import dayjs from "dayjs";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  deleteHomework,
  duplicateHomework,
  formatClipTime,
  formatHomeworkDate,
  HOMEWORK_KEY,
  archiveHomework,
  homeworkErrorMessage,
  homeworkRowsKey,
  listAssignmentRows,
  useHomeworkDetail,
  type AssignmentRow,
  type AssignmentStatus,
  type HomeworkItem,
  closedNotSubmitted,
} from "../../../api/homework";
import { useSnackbar } from "../../../context/SnackbarContext";
import { formatPersonName } from "../../../utils/stringUtils";
import { AssignmentStatusChip, DueLabel } from "../shared/AssignmentStatusChip";
import { ConfirmDeleteDialog } from "../shared/ConfirmDeleteDialog";
import { HomeworkBackButton } from "../shared/HomeworkBackButton";
import { HomeworkBlockView } from "../shared/HomeworkBlockView";
import { InnerBox, Section, SectionLabel } from "../shared/Section";
import { ManageRecipientsDialog } from "./ManageRecipientsDialog";
import { SubmissionDialog } from "./SubmissionDialog";

const STATUSES: AssignmentStatus[] = ["ASSIGNED", "SUBMITTED", "RETURNED", "COMPLETED"];
const initials = (firstName?: string | null, lastName?: string | null) =>
  `${(lastName ?? "").charAt(0)}${(firstName ?? "").charAt(0)}`.toUpperCase() || "?";

const StatTile = ({ value, label, color }: { value: ReactNode; label: string; color?: string }) => (
  <Box
    sx={(theme) => ({
      px: 2,
      py: 1.25,
      minWidth: 96,
      borderRadius: 3,
      border: 1,
      borderColor: "divider",
      bgcolor: alpha(theme.palette.background.paper, theme.palette.mode === "dark" ? 0.55 : 0.85),
    })}
  >
    <Typography sx={{ fontSize: 22, fontWeight: 800, lineHeight: 1.1, color }}>{value}</Typography>
    <Typography variant="caption" sx={{ color: "text.secondary" }}>{label}</Typography>
  </Box>
);

/** Done, waiting for a decision and overdue, each as its share of the assigned. */
const ProgressBar = ({ parts, caption }: { parts: { share: number; color: string }[]; caption: string }) => (
  <Box
    sx={(theme) => ({
      flexGrow: 1,
      minWidth: 220,
      px: 2,
      py: 1.25,
      borderRadius: 3,
      border: 1,
      borderColor: "divider",
      bgcolor: alpha(theme.palette.background.paper, theme.palette.mode === "dark" ? 0.55 : 0.85),
      display: "flex",
      flexDirection: "column",
      justifyContent: "center",
      gap: 1,
    })}
  >
    <Box sx={{ display: "flex", height: 6, borderRadius: 3, overflow: "hidden", bgcolor: "action.hover" }}>
      {parts.map((part, index) => <Box key={index} sx={{ width: `${part.share * 100}%`, bgcolor: part.color }} />)}
    </Box>
    <Typography variant="caption" sx={{ color: "text.secondary" }}>{caption}</Typography>
  </Box>
);

/** A date on two short lines, so the table stays narrow. */
const DateCell = ({ value }: { value?: string | null }) => {
  if (!value) return <Typography variant="body2" sx={{ color: "text.secondary" }}>—</Typography>;
  const date = dayjs(value);
  return (
    <Box sx={{ lineHeight: 1.15 }}>
      <Typography variant="body2" sx={{ lineHeight: 1.15 }}>{date.format("MM.DD")}</Typography>
      <Typography variant="caption" sx={{ color: "text.secondary" }}>{date.format("HH:mm")}</Typography>
    </Box>
  );
};

/** The question as the player gets it, with the kind of answer in place of the field. */
const QuestionPreview = ({ item, number }: { item: HomeworkItem; number: number }) => {
  const { t } = useTranslation();
  const choice = item.questionKind === "SINGLE_CHOICE" || item.questionKind === "MULTI_CHOICE";
  const correct = new Set(item.correctOptions ?? []);
  return (
    <InnerBox>
      <Stack spacing={1}>
        <Stack direction="row" spacing={1} sx={{ alignItems: "baseline" }}>
          <SectionLabel>{t("homework.assignment.question-number", { index: number })}</SectionLabel>
          {item.required && <Typography variant="caption" sx={{ color: "warning.main" }}>{t("homework.assignment.required")}</Typography>}
        </Stack>
        <Typography sx={{ fontWeight: 700, whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{item.body}</Typography>
        {choice ? (
          <Stack spacing={0.5}>
            {(item.options ?? []).map((option, index) => (
              <Stack key={index} direction="row" spacing={1} sx={{ alignItems: "center", color: correct.has(index) ? "success.main" : "text.secondary" }}>
                {item.questionKind === "SINGLE_CHOICE" ? <RadioButtonUnchecked fontSize="small" /> : <CheckBoxOutlineBlank fontSize="small" />}
                <Typography variant="body2" sx={{ color: "inherit" }}>{option}</Typography>
              </Stack>
            ))}
          </Stack>
        ) : (
          <Box sx={{ px: 1.5, py: 1, borderRadius: 2, border: 1, borderColor: "divider", color: "text.secondary" }}>
            <Typography variant="body2" sx={{ color: "inherit" }}>{t(`homework.editor.kind-${item.questionKind ?? "FREE_TEXT"}`)}</Typography>
          </Box>
        )}
      </Stack>
    </InnerBox>
  );
};

export const HomeworkDetail = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { showError, showSuccess } = useSnackbar();
  const queryClient = useQueryClient();
  const { homeworkId } = useParams<{ homeworkId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const openId = searchParams.get("assignment");
  const { data: homework, isLoading, isError } = useHomeworkDetail(homeworkId);
  const teamId = homework?.teamId ?? "";
  const rows = useQuery({
    queryKey: homeworkRowsKey(homeworkId ?? ""),
    queryFn: ({ signal }) => listAssignmentRows(teamId, homeworkId ?? "", signal),
    enabled: Boolean(teamId && homeworkId),
  });

  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [onlyReview, setOnlyReview] = useState(false);
  const [statusFilter, setStatusFilter] = useState<AssignmentStatus | "">("");
  const [managing, setManaging] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [archiving, setArchiving] = useState(false);

  const refresh = () => queryClient.invalidateQueries({ queryKey: HOMEWORK_KEY });
  const fail = (error: unknown) => {
    showError(homeworkErrorMessage(error, t("homework.save-failed")));
    void refresh();
  };
  const archive = useMutation({
    mutationFn: () => archiveHomework(teamId, homeworkId ?? ""),
    onSuccess: () => {
      setArchiving(false);
      void refresh();
    },
    onError: fail,
  });
  const remove = useMutation({
    mutationFn: () => deleteHomework(teamId, homeworkId ?? ""),
    onSuccess: () => {
      void refresh();
      navigate(`/homework?teamId=${teamId}`);
    },
    onError: fail,
  });
  const copy = useMutation({
    mutationFn: () => duplicateHomework(teamId, homeworkId ?? ""),
    onSuccess: (saved) => {
      void refresh();
      showSuccess(saved.omittedItemCount ? t("homework.copy.omitted", { count: saved.omittedItemCount }) : t("homework.copy.done"));
      navigate(`/homework/${saved.id}/edit`);
    },
    onError: fail,
  });
  const allRows = useMemo(() => rows.data ?? [], [rows.data]);
  const visibleRows: AssignmentRow[] = allRows
    .filter((row) => !onlyReview || row.status === "SUBMITTED")
    .filter((row) => !statusFilter || row.status === statusFilter);
  const timestampQuestions = (homework?.items ?? []).filter((item) => item.questionKind === "TIMESTAMP");

  const openRow = (id: string | null) => {
    setSearchParams(id ? { assignment: id } : {}, { replace: true });
  };
  const nextSubmitted = () => {
    const others = allRows.filter((row) => row.status === "SUBMITTED" && row.id !== openId);
    openRow(others[0]?.id ?? null);
  };

  if (isLoading) return <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}><CircularProgress /></Box>;
  if (isError || !homework) {
    return (
      <Box sx={{ p: 3 }}>
        <HomeworkBackButton onClick={() => navigate("/homework")} />
        <Alert severity="warning">{t("homework.gone")}</Alert>
      </Box>
    );
  }

  const live = homework.status === "PUBLISHED";
  const archived = homework.status === "ARCHIVED";
  const canWrite = homework.status !== "ARCHIVED";
  const canManageRecipients = homework.status === "PUBLISHED";
  const handedIn = allRows.some((row) => row.submittedAt);
  const closeMenu = () => setMenuAnchor(null);
  const listTab = live ? "active" : homework.status.toLowerCase();
  const counts = {
    assigned: allRows.length,
    submitted: allRows.filter((row) => row.status === "SUBMITTED" || row.status === "COMPLETED").length,
    completed: allRows.filter((row) => row.status === "COMPLETED").length,
    toReview: allRows.filter((row) => row.status === "SUBMITTED").length,
    overdue: allRows.filter((row) => row.overdue).length,
  };
  const share = (count: number) => (counts.assigned > 0 ? Math.max(0, count) / counts.assigned : 0);
  const questionItems = homework.items.filter((item) => item.type === "QUESTION");
  const clipCount = homework.items.filter((item) => item.type === "CLIP").length;

  return (
    <Box sx={{ py: { xs: 2, md: 2.5 }, px: { xs: 2, md: 3 } }}>
      <HomeworkBackButton onClick={() => navigate(`/homework?teamId=${homework.teamId}&tab=${listTab}`)} />
      <Stack direction="row" spacing={1} sx={{ alignItems: "flex-start", mb: 1 }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography sx={{ fontSize: { xs: 22, md: 26 }, fontWeight: 800, overflowWrap: "anywhere" }}>{homework.title}</Typography>
          <Typography variant="body2" sx={{ color: "text.secondary" }}>
            {homework.dueAt ? t("homework.due", { date: formatHomeworkDate(homework.dueAt) }) : t("homework.no-due")}
          </Typography>
          {!live && <Chip size="small" variant="outlined" sx={{ mt: 0.5 }} label={t(`homework.tabs.${listTab}`)} />}
        </Box>
        <Button startIcon={<MoreVert />} onClick={(event) => setMenuAnchor(event.currentTarget)}>{t("homework.menu.actions")}</Button>
      </Stack>
      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={closeMenu}>
        {canWrite && <MenuItem onClick={() => { closeMenu(); navigate(`/homework/${homework.id}/edit`); }}>{t("homework.menu.edit")}</MenuItem>}
        <MenuItem disabled={copy.isPending} onClick={() => { closeMenu(); copy.mutate(); }}>{t("homework.menu.copy")}</MenuItem>
        {live && <MenuItem onClick={() => { closeMenu(); setArchiving(true); }}>{t("homework.menu.archive")}</MenuItem>}
        {!handedIn && <MenuItem onClick={() => { closeMenu(); setDeleting(true); }}>{t("homework.menu.delete")}</MenuItem>}
      </Menu>

      {/* The numbers at a glance. */}
      {homework.status !== "DRAFT" && (
        <Stack direction="row" spacing={1.5} useFlexGap sx={{ flexWrap: "wrap", mt: 2 }}>
          <StatTile value={`${counts.submitted}/${counts.assigned}`} label={t("homework.detail.stat-submitted")} />
          <StatTile value={counts.completed} label={t("homework.detail.stat-completed")} />
          <StatTile value={counts.toReview} label={t("homework.detail.stat-to-review")} color={counts.toReview > 0 ? "warning.main" : undefined} />
          <StatTile value={counts.overdue} label={t("homework.detail.stat-overdue")} color={counts.overdue > 0 ? "error.main" : undefined} />
          <ProgressBar
            parts={[
              { share: share(counts.completed), color: "success.main" },
              { share: share(counts.submitted - counts.completed), color: "info.main" },
              { share: share(counts.overdue), color: "error.main" },
            ]}
            caption={t("homework.progress", {
              submitted: counts.submitted,
              assigned: counts.assigned,
              completed: counts.completed,
              overdue: counts.overdue,
            })}
          />
        </Stack>
      )}

      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "minmax(0, 1fr)", lg: "minmax(300px, 1fr) minmax(0, 2.2fr)" },
          gap: 2,
          mt: 2,
          alignItems: "start",
        }}
      >
        <Section
          title={t("homework.detail.content")}
          action={(
            <Typography variant="body2" sx={{ color: "text.secondary" }}>
              {t("homework.detail.content-summary", { clips: clipCount, questions: questionItems.length })}
            </Typography>
          )}
        >
          {homework.description && <Typography sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere", color: "text.secondary" }}>{homework.description}</Typography>}
          {homework.items.map((item) => (item.type === "QUESTION"
            ? <QuestionPreview key={item.id} item={item} number={questionItems.indexOf(item) + 1} />
            : <HomeworkBlockView key={item.id} item={item} />))}
        </Section>

        <Section
          title={t("homework.detail.recipients", { count: allRows.length })}
          action={(
            <Stack direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap", rowGap: 1 }}>
              <TextField
                select
                size="small"
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value as AssignmentStatus | "")}
                sx={{ minWidth: 170 }}
                slotProps={{ select: { displayEmpty: true } }}
              >
                <MenuItem value="">{t("homework.detail.all-statuses")}</MenuItem>
                {STATUSES.map((status) => <MenuItem key={status} value={status}>{t(`homework.status.${status}`)}</MenuItem>)}
              </TextField>
              <FormControlLabel
                sx={{ mr: 0 }}
                control={<Switch size="small" checked={onlyReview} onChange={(event) => setOnlyReview(event.target.checked)} />}
                label={t("homework.table.only-review")}
              />
              {canManageRecipients && (
                <Button size="small" variant="outlined" startIcon={<GroupOutlined />} onClick={() => setManaging(true)}>
                  {t("homework.recipients.manage")}
                </Button>
              )}
            </Stack>
          )}
        >
          {/* Every row at full height, the page scrolls: no second scrollbar inside the card. */}
          <TableContainer sx={{ overflowX: "auto" }}>
            <Table
              size="small"
              sx={(theme) => ({
                // A clear line between players: the default divider all but vanishes on the dark background.
                "& .MuiTableCell-root": { borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.14)}` },
                "& .MuiTableCell-head": { borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.28)}` },
                "& .MuiTableBody-root .MuiTableRow-root:last-of-type .MuiTableCell-root": { borderBottom: 0 },
                "& .MuiTableBody-root .MuiTableCell-root": { py: 1.25 },
              })}
            >
              <TableHead>
                <TableRow>
                  <TableCell>{t("homework.table.name")}</TableCell>
                  <TableCell>{t("homework.table.status")}</TableCell>
                  <TableCell>{t("homework.table.viewed")}</TableCell>
                  <TableCell>{t("homework.table.submitted")}</TableCell>
                  <TableCell>{t("homework.detail.late")}</TableCell>
                  {timestampQuestions.length > 0 && <TableCell>{t("homework.table.marks")}</TableCell>}
                </TableRow>
              </TableHead>
              <TableBody>
                {visibleRows.map((row) => {
                  const byOther = row.submittedByParent
                    ? t("homework.detail.by-parent", { name: formatPersonName(row.submittedByFirstName, row.submittedByLastName) })
                    : null;
                  return (
                    <TableRow key={row.id} hover sx={{ cursor: "pointer" }} onClick={() => openRow(row.id)}>
                      {/* Orange for what waits for a decision. */}
                      <TableCell sx={(theme) => (row.status === "SUBMITTED" ? { boxShadow: `inset 3px 0 0 ${theme.palette.warning.main}` } : {})}>
                        <Stack direction="row" spacing={1.25} sx={{ alignItems: "center", minWidth: 0 }}>
                          <Avatar sx={{ width: 26, height: 26, fontSize: 11, fontWeight: 700, bgcolor: "action.selected", color: "text.primary" }}>
                            {initials(row.firstName, row.lastName)}
                          </Avatar>
                          <Box sx={{ minWidth: 0 }}>
                            <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>{formatPersonName(row.firstName, row.lastName)}</Typography>
                            {byOther && <Typography variant="caption" component="div" sx={{ color: "text.secondary" }} noWrap>{byOther}</Typography>}
                          </Box>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5} sx={{ flexWrap: "wrap", rowGap: 0.5 }}>
                          <AssignmentStatusChip status={row.status} notSubmitted={closedNotSubmitted(archived, row.status) ? "staff" : undefined} />
                        </Stack>
                      </TableCell>
                      <TableCell><DateCell value={row.firstViewedAt} /></TableCell>
                      <TableCell><DateCell value={row.submittedAt} /></TableCell>
                      <TableCell>
                        {row.late || row.overdue || row.dueSoon ? (
                          <DueLabel overdue={row.overdue} dueSoon={row.dueSoon} late={row.late} dueAt={row.effectiveDueAt} />
                        ) : (
                          <Typography variant="body2" sx={{ color: "text.secondary" }}>—</Typography>
                        )}
                      </TableCell>
                      {timestampQuestions.length > 0 && (
                        <TableCell>{timestampQuestions.map((item) => formatClipTime(row.timestamps[item.id])).filter(Boolean).join(", ")}</TableCell>
                      )}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
          {rows.data && visibleRows.length === 0 && <Typography variant="body2" sx={{ color: "text.secondary" }}>{t("homework.empty")}</Typography>}
        </Section>
      </Box>

      <SubmissionDialog
        assignmentId={openId}
        homeworkTitle={homework.title}
        onClose={() => openRow(null)}
        onNext={allRows.some((row) => row.status === "SUBMITTED" && row.id !== openId) ? nextSubmitted : undefined}
        nextCount={allRows.filter((row) => row.status === "SUBMITTED" && row.id !== openId).length}
      />

      <Dialog open={archiving} onClose={() => setArchiving(false)} fullWidth maxWidth="xs">
        <DialogTitle>{t("homework.menu.archive")}</DialogTitle>
        <DialogContent><Typography>{t("homework.archive-body")}</Typography></DialogContent>
        <DialogActions>
          <Button onClick={() => setArchiving(false)}>{t("homework.editor.cancel")}</Button>
          <Button variant="contained" disabled={archive.isPending} onClick={() => archive.mutate()}>{t("homework.menu.archive")}</Button>
        </DialogActions>
      </Dialog>
      <ManageRecipientsDialog
        open={managing}
        teamId={teamId}
        homeworkId={homework.id}
        rows={allRows}
        onClose={() => setManaging(false)}
        onSaved={() => void refresh()}
      />
      <ConfirmDeleteDialog
        open={deleting}
        title={t("homework.menu.delete")}
        body={t("homework.delete-body", { title: homework.title })}
        pending={remove.isPending}
        onClose={() => setDeleting(false)}
        onConfirm={() => remove.mutate()}
      />
    </Box>
  );
};
