import axios from "axios";
import { customInstance } from "./axiosInstance";

export type MedicalState =
  | "MISSING"
  | "RESULT_MISSING"
  | "UNVERIFIED"
  | "EXPIRED"
  | "EXPIRING"
  | "VALID"
  | "SUSPENDED"
  | "NOT_FIT";
export type MedicalResult = "VERSENYEZHET" | "ALKALMAS" | "NEM_ALKALMAS" | "IDEIGLENESEN_ELTILTVA" | "VEGLEGESEN_ELTILTVA";
export type MedicalRejectionReason = "ILLEGIBLE" | "NOT_REDACTED" | "WRONG_PERSON" | "RESULT_MISMATCH" | "OTHER";
export type MedicalMode = "INHERIT" | "ON" | "OFF";
export type MedicalEvidence = "INHERIT" | "DATE_ONLY" | "REDACTED_FILE";
export type MedicalSubmitterRole = "PARENT" | "CLUB_ADMIN" | "ADMIN" | "PLAYER" | "COACH";
export type MedicalVerificationMethod = "ORIGINAL" | "FILE";

export const MEDICAL_RESULTS: readonly MedicalResult[] = [
  "VERSENYEZHET",
  "ALKALMAS",
  "IDEIGLENESEN_ELTILTVA",
  "NEM_ALKALMAS",
  "VEGLEGESEN_ELTILTVA",
] as const;

/** NEM_ALKALMAS and VEGLEGESEN_ELTILTVA come without a date. */
export const resultHasDate = (result?: MedicalResult | "") => Boolean(result) && result !== "NEM_ALKALMAS" && result !== "VEGLEGESEN_ELTILTVA";

/** What the server stores: the redaction editor takes a PDF, JPEG or PNG and turns it into one JPEG. */
export const MEDICAL_CONTENT_TYPES = ["image/jpeg"] as const;
export const MEDICAL_MAX_BYTES = 10 * 1024 * 1024;
export const MEDICAL_NOTICE_MAX_BYTES = 5 * 1024 * 1024;

export interface MedicalHistoryItem {
  result?: MedicalResult;
  validUntil?: string;
  submittedAt?: string;
  submittedByFirstName?: string;
  submittedByLastName?: string;
  submittedByRole?: MedicalSubmitterRole;
  supersededAt?: string;
  withdrawnAt?: string;
  rejectedAt?: string;
  verifiedAt?: string;
}

export interface MedicalAdminAccessItem {
  id?: string;
  createdAt: string;
  adminFirstName?: string;
  adminLastName?: string;
  playerName?: string;
  action: "READ" | "WITHDRAWN";
  reason: string;
}

export interface MedicalNoticeItem {
  clubId: string;
  clubName?: string;
  legalBasis?: string;
  noticeKind?: "URL" | "FILE";
}

/**
 * Fields the caller may not see are left out. `resultAllowsTraining` and `resultAllowsCompeting` are what the paper
 * says, not a permission: they are shown only together with the status sentence.
 */
export interface MedicalStatus {
  required: boolean;
  status?: MedicalState;
  result?: MedicalResult;
  resultAllowsTraining?: boolean;
  resultAllowsCompeting?: boolean;
  validUntil?: string;
  daysRemaining?: number;
  certificateId?: string;
  hasFile?: boolean;
  submittedAt?: string;
  submittedByFirstName?: string;
  submittedByLastName?: string;
  submittedByRole?: MedicalSubmitterRole;
  history?: MedicalHistoryItem[];
  canUploadMedical?: boolean;
  /** Only on the player's own `/me/medical`: the profile the upload and file endpoints take. */
  profileId?: string;
  adminAccesses?: MedicalAdminAccessItem[];
  notices?: MedicalNoticeItem[];
  fileRequired?: boolean;
  fileRequestedNext?: boolean;
  verified?: boolean;
  verifiedAt?: string;
  verifiedByFirstName?: string;
  verifiedByLastName?: string;
  verifiedByRole?: MedicalSubmitterRole;
  verificationMethod?: MedicalVerificationMethod;
  coachFileAccess?: boolean;
  /** Only to a parent: someone else submitted the current row, or a break-glass access is new. */
  unseen?: boolean;
  adult?: boolean;
  parentAccessAllowed?: boolean;
  /** Only while there is no newer row: why a verifier rejected the last one. */
  rejectionReason?: MedicalRejectionReason;
  rejectedAt?: string;
  rejectedByRole?: MedicalSubmitterRole;
  dateCorrectedAt?: string;
  dateCorrectedByRole?: MedicalSubmitterRole;
  originalValidUntil?: string;
}

export interface TeamMedicalStatusItem {
  profileId: string;
  status: MedicalState;
  result?: MedicalResult;
  resultAllowsTraining?: boolean;
  resultAllowsCompeting?: boolean;
  validUntil?: string;
  daysRemaining?: number;
  verified?: boolean;
}

export interface TeamMedicalStatus {
  enabled: boolean;
  mode: MedicalMode;
  evidence: MedicalEvidence;
  items: TeamMedicalStatusItem[];
}

export type ClubMedicalMissing = "legalBasis" | "notice" | "dpa";

export interface ClubMedicalSettings {
  clubId: string;
  mode: "ON" | "OFF";
  acknowledgedAt?: string;
  historyRetentionYears: number;
  evidence: "DATE_ONLY" | "REDACTED_FILE";
  legalBasis?: string;
  notice?: { kind: "URL" | "FILE"; updatedAt?: string; version: number };
  dpaVersion?: string;
  dpaAcceptedAt?: string;
  currentDpaVersion?: string;
  dpaDocumentUrl?: string;
  noticeTemplateUrl?: string;
  ready: boolean;
  missing: ClubMedicalMissing[];
  storageSecure: boolean;
  storageIssues: string[];
  coachFileAccess: boolean;
  coachFileAccessAcknowledgedAt?: string;
}

export interface ClubMedicalSettingsUpdate {
  mode?: "ON" | "OFF";
  acknowledged?: boolean;
  confirmDeletion?: boolean;
  historyRetentionYears?: number;
  legalBasis?: string;
  noticeUrl?: string;
  acceptDpaVersion?: string;
  evidence?: "DATE_ONLY" | "REDACTED_FILE";
  coachFileAccess?: boolean;
  coachFileAccessAcknowledged?: boolean;
}

export type ClubMedicalFilter = "UNVERIFIED" | "EXPIRING" | "NOT_COMPETING" | "BANNED" | "RESULT_MISSING" | "MISSING";
export const CLUB_MEDICAL_FILTERS: readonly ClubMedicalFilter[] = [
  "UNVERIFIED",
  "EXPIRING",
  "NOT_COMPETING",
  "BANNED",
  "RESULT_MISSING",
  "MISSING",
] as const;

export interface ClubMedicalStatusItem {
  profileId: string;
  firstName?: string;
  lastName?: string;
  teamIds: string[];
  status: MedicalState;
  result?: MedicalResult;
  resultAllowsTraining?: boolean;
  resultAllowsCompeting?: boolean;
  validUntil?: string;
  daysRemaining?: number;
  verified: boolean;
  certificateId?: string;
  hasFile?: boolean;
  fileRequired: boolean;
}

export interface MedicalTeamRef {
  teamId: string;
  name?: string;
}

export interface ClubMedicalStatus {
  teamsWithoutCoach: MedicalTeamRef[];
  teams: MedicalTeamRef[];
  counts: Record<ClubMedicalFilter, number>;
  page: { items: ClubMedicalStatusItem[]; page: number; size: number; total: number; totalPages: number };
}

export interface MedicalAdminAccessPage {
  items: MedicalAdminAccessItem[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

/** Technical facts only: no result, no validity, no file. */
export interface MedicalAdminAccessResponse {
  required: boolean;
  fileRequired: boolean;
  certificateId?: string;
  hasFile: boolean;
  evidence?: string;
  contentType?: string;
  sizeBytes?: number;
  submittedAt?: string;
  verified: boolean;
  historyCount: number;
  pendingCount: number;
  notRequiredSince?: string;
  fileNotRequiredSince?: string;
}

interface MedicalUpload {
  certificateId: string;
  uploadUrl?: string;
  expiresInSeconds?: number;
  completed: boolean;
}

const json = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const medicalQueryKey = (profileId: string) => ["medical", profileId] as const;
export const myMedicalQueryKey = ["medical", "me"] as const;
export const teamMedicalQueryKey = (teamId: string) => ["medical", "team", teamId] as const;
export const clubMedicalQueryKey = (clubId: string) => ["medical", "club", clubId] as const;
export const clubMedicalTeamsQueryKey = (clubId: string) => ["medical", "club", clubId, "teams"] as const;
export const clubMedicalStatusQueryKey = (clubId: string, filter: ClubMedicalFilter | null, teamId: string | null, page: number) =>
  ["medical", "club", clubId, "status", filter, teamId, page] as const;
export const clubMedicalAccessesQueryKey = (clubId: string, page: number) => ["medical", "club", clubId, "accesses", page] as const;

export const getMedicalStatus = (profileId: string) =>
  customInstance<MedicalStatus>(`/api/player-profiles/${profileId}/medical`, { method: "GET" });

export const getMyMedicalStatus = () =>
  customInstance<MedicalStatus>("/api/me/medical", { method: "GET" });

export const getTeamMedicalStatus = (teamId: string) =>
  customInstance<TeamMedicalStatus>(`/api/teams/${teamId}/medical-status`, { method: "GET" });

export const setTeamMedicalMode = (
  teamId: string,
  body: { mode?: MedicalMode; evidence?: MedicalEvidence; confirmDeletion?: boolean; acknowledged?: boolean },
) =>
  customInstance<TeamMedicalStatus>(`/api/teams/${teamId}/medical-mode`, { method: "PUT", ...json(body) });

export const getClubMedicalSettings = (clubId: string) =>
  customInstance<ClubMedicalSettings>(`/api/clubs/${clubId}/medical-settings`, { method: "GET" });

export const updateClubMedicalSettings = (clubId: string, body: ClubMedicalSettingsUpdate) =>
  customInstance<ClubMedicalSettings>(`/api/clubs/${clubId}/medical-settings`, { method: "PUT", ...json(body) });

/** The club's privacy notice as a PDF; it goes through the server (not health data). */
export const uploadClubMedicalNotice = (clubId: string, file: File) => {
  const form = new FormData();
  form.append("file", file);
  return customInstance<ClubMedicalSettings>(`/api/clubs/${clubId}/medical-notice`, { method: "PUT", body: form });
};

export const removeClubMedicalNotice = (clubId: string) =>
  customInstance<void>(`/api/clubs/${clubId}/medical-notice`, { method: "DELETE" });

export const getClubMedicalNotice = (clubId: string) =>
  customInstance<{ kind: "URL" | "FILE"; url: string; expiresInSeconds?: number }>(`/api/clubs/${clubId}/medical-notice`, {
    method: "GET",
  });

export const getClubMedicalStatus = (clubId: string, filter: ClubMedicalFilter | null, teamId: string | null, page: number, size: number) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filter) params.set("filter", filter);
  if (teamId) params.set("teamId", teamId);
  return customInstance<ClubMedicalStatus>(`/api/clubs/${clubId}/medical-status?${params.toString()}`, { method: "GET" });
};

export const getClubMedicalAdminAccesses = (clubId: string, page: number, size: number) =>
  customInstance<MedicalAdminAccessPage>(`/api/clubs/${clubId}/medical-admin-accesses?page=${page}&size=${size}`, {
    method: "GET",
  });

export const setMedicalUploadRight = (profileId: string, allowed: boolean) =>
  customInstance<{ allowed: boolean }>(`/api/player-profiles/${profileId}/medical-upload-right`, {
    method: "PUT",
    ...json({ allowed }),
  });

export const setMedicalParentAccess = (allowed: boolean) =>
  customInstance<{ allowed: boolean }>("/api/me/medical/parent-access", { method: "PUT", ...json({ allowed }) });

export const markMedicalSeen = (profileId: string, certificateId: string) =>
  customInstance<void>(`/api/player-profiles/${profileId}/medical/seen`, { method: "POST", ...json({ certificateId }) });

export const verifyMedical = (
  profileId: string,
  body: { result: MedicalResult; validUntil?: string; method: MedicalVerificationMethod },
) =>
  customInstance<MedicalStatus>(`/api/player-profiles/${profileId}/medical/verification`, { method: "POST", ...json(body) });

export const rejectMedical = (
  profileId: string,
  body: { result?: MedicalResult; validUntil?: string; method: MedicalVerificationMethod; reason: MedicalRejectionReason },
) =>
  customInstance<MedicalStatus>(`/api/player-profiles/${profileId}/medical/rejection`, { method: "POST", ...json(body) });

export const correctMedicalDate = (
  profileId: string,
  body: { result: MedicalResult; validUntil?: string; method: MedicalVerificationMethod; correctedValidUntil: string },
) =>
  customInstance<MedicalStatus>(`/api/player-profiles/${profileId}/medical/date-correction`, { method: "POST", ...json(body) });

export const unverifyMedical = (profileId: string) =>
  customInstance<void>(`/api/player-profiles/${profileId}/medical/verification`, { method: "DELETE" });

export const submitMedicalInPerson = (profileId: string, body: { result: MedicalResult; validUntil?: string }) =>
  customInstance<MedicalStatus>(`/api/player-profiles/${profileId}/medical/in-person`, {
    method: "POST",
    ...json({ ...body, seenOriginal: true }),
  });

export const medicalAdminAccess = (profileId: string, reason: string) =>
  customInstance<MedicalAdminAccessResponse>(`/api/player-profiles/${profileId}/medical/admin-access`, {
    method: "POST",
    ...json({ reason }),
  });

export const medicalAdminWithdrawal = (profileId: string, certificateId: string, reason: string) =>
  customInstance<void>(`/api/player-profiles/${profileId}/medical/admin-withdrawal`, {
    method: "POST",
    ...json({ certificateId, reason }),
  });

export const openMedicalFile = (profileId: string, certificateId: string) =>
  customInstance<{ url: string; expiresInSeconds: number }>(
    `/api/player-profiles/${profileId}/medical-certificates/${certificateId}/file`,
    { method: "GET" },
  );

export const withdrawMedicalCertificate = (profileId: string, certificateId: string) =>
  customInstance<void>(`/api/player-profiles/${profileId}/medical-certificates/${certificateId}`, { method: "DELETE" });

/** Pauses before each new try of the last step while the file storage answers 503. */
const COMPLETE_RETRY_DELAYS_MS = [1000, 3000];

export interface MedicalSubmission {
  result: MedicalResult;
  validUntil?: string;
  /** Only where the club asks for the redacted copy. */
  file?: File;
}

/**
 * A submission. Date-only: one call, the row is current at once. With a file, three steps: the server opens a
 * pending row and a 10-minute signed URL, the browser puts the file there (the signature binds the size), and the
 * server checks the stored object. A rejected upload starts again from step one; a 503 from the last step means the
 * storage was unreachable and the pending row is kept, so only that step is tried again.
 */
export const submitMedicalCertificate = async (profileId: string, submission: MedicalSubmission) => {
  const { result, validUntil, file } = submission;
  const started = await customInstance<MedicalUpload>(`/api/player-profiles/${profileId}/medical-certificates`, {
    method: "POST",
    ...json(file
      ? { result, validUntil, acknowledged: true, contentType: file.type, sizeBytes: file.size, redactionConfirmed: true }
      : { result, validUntil, acknowledged: true }),
  });
  if (started.completed || !file || !started.uploadUrl) return undefined;
  await axios.put(started.uploadUrl, file, { headers: { "Content-Type": file.type } });
  const complete = () =>
    customInstance<MedicalStatus>(
      `/api/player-profiles/${profileId}/medical-certificates/${started.certificateId}/complete`,
      { method: "POST" },
    );
  for (const delay of COMPLETE_RETRY_DELAYS_MS) {
    try {
      return await complete();
    } catch (err) {
      if (!axios.isAxiosError(err) || err.response?.status !== 503) throw err;
      await new Promise((resolve) => setTimeout(resolve, delay));
    }
  }
  return complete();
};
