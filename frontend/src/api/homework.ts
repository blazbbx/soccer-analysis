import { useQuery } from "@tanstack/react-query";
import { type AxiosError } from "axios";
import dayjs from "dayjs";
import i18n from "../i18n";
import { customInstance } from "./axiosInstance";
import type { PageResponse } from "./paging";

export type HomeworkStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type HomeworkItemType = "TEXT" | "CLIP" | "QUESTION";
export type HomeworkQuestionKind = "FREE_TEXT" | "SINGLE_CHOICE" | "MULTI_CHOICE" | "TIMESTAMP";
/** RETURNED: the coach sent the work back for a fix. It is on the to-do list again and the deadline no longer closes it. */
export type AssignmentStatus = "ASSIGNED" | "SUBMITTED" | "RETURNED" | "COMPLETED";
export type CoachTab = "active" | "draft" | "archived";
export type RecipientTab = "todo" | "submitted" | "done";

/** Archived while still on the to-do list: never handed in, or the fix the coach asked for never was. */
export const closedNotSubmitted = (archived: boolean, status: AssignmentStatus) =>
  archived && (status === "ASSIGNED" || status === "RETURNED");

export interface HomeworkItem {
  id: string;
  position: number;
  type: HomeworkItemType;
  body?: string | null;
  clipId?: string | null;
  clipName?: string | null;
  clipRenderStatus?: string | null;
  focusMs?: number | null;
  questionKind?: HomeworkQuestionKind | null;
  options?: string[] | null;
  correctOptions?: number[] | null;
  required: boolean;
  refItemId?: string | null;
}

export interface HomeworkSummary {
  id: string;
  teamId: string;
  title: string;
  status: HomeworkStatus;
  dueAt?: string | null;
  allowLate: boolean;
  updatedAt: string;
  assignedCount: number;
  submittedCount: number;
  completedCount: number;
  overdueCount: number;
  toReviewCount: number;
}

export interface HomeworkDetail {
  id: string;
  teamId: string;
  title: string;
  description?: string | null;
  status: HomeworkStatus;
  dueAt?: string | null;
  allowLate: boolean;
  createdAt: string;
  updatedAt: string;
  draftEntireRoster: boolean;
  draftRecipientIds: string[];
  items: HomeworkItem[];
  omittedItemCount?: number | null;
}

export interface HomeworkItemBody {
  id?: string | null;
  clientKey?: string | null;
  type: HomeworkItemType;
  body?: string | null;
  clipId?: string | null;
  focusMs?: number | null;
  questionKind?: HomeworkQuestionKind | null;
  options?: string[] | null;
  correctOptions?: number[] | null;
  required?: boolean | null;
  refItemId?: string | null;
  refClientKey?: string | null;
}

export interface UpsertHomeworkBody {
  title: string;
  description?: string | null;
  dueAt?: string | null;
  allowLate: boolean;
  items: HomeworkItemBody[];
  entireRoster: boolean;
  playerProfileIds?: string[] | null;
  expectedUpdatedAt?: string | null;
}

export interface PublishHomeworkBody {
  entireRoster: boolean;
  playerProfileIds?: string[] | null;
  expectedUpdatedAt: string;
}

export interface AssignmentRow {
  id: string;
  playerProfileId: string;
  firstName?: string | null;
  lastName?: string | null;
  status: AssignmentStatus;
  effectiveDueAt?: string | null;
  firstViewedAt?: string | null;
  submittedAt?: string | null;
  submittedByFirstName?: string | null;
  submittedByLastName?: string | null;
  submittedByParent?: boolean | null;
  reviewedAt?: string | null;
  overdue: boolean;
  late: boolean;
  dueSoon: boolean;
  timestamps: Record<string, number>;
}

export interface HomeworkRecipient {
  playerProfileId: string;
  firstName?: string | null;
  lastName?: string | null;
  hasLogin: boolean;
}

export interface MyHomework {
  assignmentId: string;
  homeworkId: string;
  teamId: string;
  teamName?: string | null;
  playerProfileId: string;
  title: string;
  homeworkStatus: HomeworkStatus;
  status: AssignmentStatus;
  effectiveDueAt?: string | null;
  submittedAt?: string | null;
  overdue: boolean;
  dueSoon: boolean;
  late: boolean;
  lateBlocked: boolean;
}

export interface HomeworkAnswer {
  itemId: string;
  textAnswer?: string | null;
  selectedOptions?: number[] | null;
  timestampMs?: number | null;
}

export interface Assignment {
  id: string;
  homeworkId: string;
  teamId: string;
  teamName?: string | null;
  title: string;
  description?: string | null;
  homeworkStatus: HomeworkStatus;
  /** Sent back with the hand-in: a changed homework refuses it with a 409, and the page reloads. */
  homeworkUpdatedAt: string;
  playerProfileId: string;
  firstName?: string | null;
  lastName?: string | null;
  status: AssignmentStatus;
  responseText?: string | null;
  effectiveDueAt?: string | null;
  allowLate: boolean;
  firstViewedAt?: string | null;
  submittedAt?: string | null;
  submittedByFirstName?: string | null;
  submittedByLastName?: string | null;
  submittedByParent?: boolean | null;
  createdByFirstName?: string | null;
  createdByLastName?: string | null;
  reviewedAt?: string | null;
  reviewedByFirstName?: string | null;
  reviewedByLastName?: string | null;
  coachFeedback?: string | null;
  /** The last time the coach sent the work back, and why. Kept after the fix is handed in. */
  returnedAt?: string | null;
  returnedByFirstName?: string | null;
  returnedByLastName?: string | null;
  returnNote?: string | null;
  overdue: boolean;
  dueSoon: boolean;
  late: boolean;
  lateBlocked: boolean;
  writable: boolean;
  items: HomeworkItem[];
  answers: HomeworkAnswer[];
}

export interface HomeworkComment {
  id: string;
  authorFirstName?: string | null;
  authorLastName?: string | null;
  body: string;
  createdAt: string;
  /** The caller wrote it. */
  mine: boolean;
}

export interface HomeworkSummaryCounts {
  open: number;
  overdue: number;
  toReview: number;
  nextDueAt?: string | null;
}

const json = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

const teamBase = (teamId: string) => `/api/teams/${teamId}/homeworks`;
const assignmentBase = (assignmentId: string) => `/api/homework-assignments/${assignmentId}`;

export const HOMEWORK_KEY = ["homework"] as const;
export const homeworkListKey = (teamId: string, tab: CoachTab, page: number) => [...HOMEWORK_KEY, "list", teamId, tab, page] as const;
export const homeworkDetailKey = (homeworkId: string) => [...HOMEWORK_KEY, "detail", homeworkId] as const;
export const homeworkRowsKey = (homeworkId: string) => [...HOMEWORK_KEY, "rows", homeworkId] as const;
export const myHomeworkKey = (tab: RecipientTab, profileId: string, page: number) => [...HOMEWORK_KEY, "mine", tab, profileId, page] as const;
export const assignmentKey = (assignmentId: string) => [...HOMEWORK_KEY, "assignment", assignmentId] as const;
export const commentsKey = (assignmentId: string) => [...HOMEWORK_KEY, "comments", assignmentId] as const;
export const summaryKey = (view: string) => [...HOMEWORK_KEY, "summary", view] as const;

export const listHomeworks = (teamId: string, tab: CoachTab, page: number, signal?: AbortSignal) =>
  customInstance<PageResponse<HomeworkSummary>>(`${teamBase(teamId)}?tab=${tab}&page=${page}`, { method: "GET", signal });
export const getHomework = (homeworkId: string, signal?: AbortSignal) =>
  customInstance<HomeworkDetail>(`/api/homeworks/${homeworkId}`, { method: "GET", signal });
export const createHomework = (teamId: string, body: UpsertHomeworkBody) =>
  customInstance<HomeworkDetail>(teamBase(teamId), { method: "POST", ...json(body) });
export const updateHomework = (teamId: string, homeworkId: string, body: UpsertHomeworkBody) =>
  customInstance<HomeworkDetail>(`${teamBase(teamId)}/${homeworkId}`, { method: "PUT", ...json(body) });
export const deleteHomework = (teamId: string, homeworkId: string) =>
  customInstance<void>(`${teamBase(teamId)}/${homeworkId}`, { method: "DELETE" });
export const publishHomework = (teamId: string, homeworkId: string, body: PublishHomeworkBody) =>
  customInstance<HomeworkDetail>(`${teamBase(teamId)}/${homeworkId}/publish`, { method: "POST", ...json(body) });
export const archiveHomework = (teamId: string, homeworkId: string) =>
  customInstance<HomeworkDetail>(`${teamBase(teamId)}/${homeworkId}/archive`, { method: "POST" });
export const duplicateHomework = (teamId: string, homeworkId: string) =>
  customInstance<HomeworkDetail>(`${teamBase(teamId)}/${homeworkId}/duplicate`, { method: "POST" });
export const listAssignmentRows = (teamId: string, homeworkId: string, signal?: AbortSignal) =>
  customInstance<AssignmentRow[]>(`${teamBase(teamId)}/${homeworkId}/assignments`, { method: "GET", signal });
export const addAssignees = (teamId: string, homeworkId: string, playerProfileIds: string[]) =>
  customInstance<AssignmentRow[]>(`${teamBase(teamId)}/${homeworkId}/assignees`, { method: "POST", ...json({ playerProfileIds }) });
/** Only an assignment never handed in can be taken back. */
export const removeAssignee = (teamId: string, homeworkId: string, playerProfileId: string) =>
  customInstance<void>(`${teamBase(teamId)}/${homeworkId}/assignees/${playerProfileId}`, { method: "DELETE" });
export const listRecipients = (teamId: string, signal?: AbortSignal) =>
  customInstance<HomeworkRecipient[]>(`/api/teams/${teamId}/homework-recipients`, { method: "GET", signal });
export const getClipUsage = (clipId: string) =>
  customInstance<{ count: number }>(`/api/clips/${clipId}/homework-usage`, { method: "GET" });

export const listMyHomeworks = (tab: RecipientTab, profileId: string | undefined, page: number, signal?: AbortSignal) =>
  customInstance<PageResponse<MyHomework>>(
    `/api/me/homeworks?tab=${tab}&page=${page}${profileId ? `&profileId=${profileId}` : ""}`,
    { method: "GET", signal },
  );
export const getHomeworkSummary = (view: string, signal?: AbortSignal) =>
  customInstance<HomeworkSummaryCounts>(`/api/me/homeworks/summary?view=${view}`, { method: "GET", signal });
export const getAssignment = (assignmentId: string, signal?: AbortSignal) =>
  customInstance<Assignment>(assignmentBase(assignmentId), { method: "GET", signal });
export const markAssignmentViewed = (assignmentId: string) =>
  customInstance<void>(`${assignmentBase(assignmentId)}/view`, { method: "POST" });
/** The whole answer at once. {@code expectedUpdatedAt} is the homework's update time the page was loaded with. */
export const submitAssignment = (
  assignmentId: string,
  body: { responseText?: string | null; answers: HomeworkAnswer[]; expectedUpdatedAt: string },
) =>
  customInstance<Assignment>(`${assignmentBase(assignmentId)}/submit`, { method: "POST", ...json(body) });
export const reviewAssignment = (
  assignmentId: string,
  body: { feedback?: string | null },
) => customInstance<Assignment>(`${assignmentBase(assignmentId)}/review`, { method: "POST", ...json(body) });
/** Back to the player for a fix. The note is required, and it also goes into the conversation. */
export const returnAssignment = (assignmentId: string, body: { note: string }) =>
  customInstance<Assignment>(`${assignmentBase(assignmentId)}/return`, { method: "POST", ...json(body) });
export const listComments = (assignmentId: string, signal?: AbortSignal) =>
  customInstance<HomeworkComment[]>(`${assignmentBase(assignmentId)}/comments`, { method: "GET", signal });
export const addComment = (assignmentId: string, body: { body: string }) =>
  customInstance<HomeworkComment>(`${assignmentBase(assignmentId)}/comments`, { method: "POST", ...json(body) });
export const useHomeworkDetail = (homeworkId?: string) =>
  useQuery({
    queryKey: homeworkDetailKey(homeworkId ?? ""),
    queryFn: ({ signal }) => getHomework(homeworkId ?? "", signal),
    enabled: Boolean(homeworkId),
  });

export const useAssignment = (assignmentId?: string) =>
  useQuery({
    queryKey: assignmentKey(assignmentId ?? ""),
    queryFn: ({ signal }) => getAssignment(assignmentId ?? "", signal),
    enabled: Boolean(assignmentId),
  });

const POLL_MILLIS = 60_000;

/**
 * The sidebar, the dashboard and the list share this. It is the only place outside the homework pages that tells
 * someone a coach answered or a player handed in, so it polls on a visible tab and refreshes on focus.
 */
export const useHomeworkSummary = (view: string | null | undefined) =>
  useQuery({
    queryKey: summaryKey(view ?? ""),
    queryFn: ({ signal }) => getHomeworkSummary(view ?? "", signal),
    enabled: Boolean(view),
    staleTime: 30_000,
    refetchInterval: () => (document.visibilityState === "visible" ? POLL_MILLIS : false),
    refetchOnWindowFocus: true,
  });

export const homeworkErrorMessage = (error: unknown, fallback: string) => {
  const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
  return detail || fallback;
};

export const homeworkErrorStatus = (error: unknown) => (error as AxiosError | undefined)?.response?.status;

/** The backend's message code, such as "error.homework.stale_write": one status can mean several things. */
export const homeworkErrorCode = (error: unknown) =>
  (error as AxiosError<{ code?: string }> | undefined)?.response?.data?.code;

export const STALE_WRITE = "error.homework.stale_write";

/** The target of a stale link: gone, or the right to it was lost. */
export const isGoneError = (error: unknown) => {
  const status = homeworkErrorStatus(error);
  return status === 403 || status === 404;
};

export const formatHomeworkDate = (value?: string | null) => {
  if (!value) return "";
  const parsed = dayjs(value);
  if (!parsed.isValid()) return "";
  const pattern = i18n.language.toLowerCase().startsWith("hu") ? "YYYY.MM.DD. HH:mm" : "MMM D, YYYY HH:mm";
  return parsed.format(pattern);
};

export const toLocalInput = (value?: string | null) => (value ? dayjs(value).format("YYYY-MM-DDTHH:mm") : "");
export const fromLocalInput = (value: string) => (value ? dayjs(value).format("YYYY-MM-DDTHH:mm:ss") : null);

export const formatClipTime = (milliseconds?: number | null) => {
  if (milliseconds == null) return "";
  const totalSeconds = Math.floor(milliseconds / 1000);
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, "0")}`;
};
