import type { TFunction } from "i18next";
import type { MedicalResult, MedicalState } from "../../api/medical";

const GREEN = "#10b981";
const AMBER = "#f59e0b";
const RED = "#ef4444";
const GREY = "#a1a1aa";

export const MEDICAL_COLORS: Record<MedicalState, string> = {
  VALID: GREEN,
  EXPIRING: AMBER,
  UNVERIFIED: AMBER,
  EXPIRED: RED,
  SUSPENDED: RED,
  NOT_FIT: RED,
  RESULT_MISSING: GREY,
  MISSING: GREY,
};

/** The blue mark of an unread state on a parent's child card, apart from the light's colours. */
export const MEDICAL_UNSEEN_COLOR = "#3b82f6";

const localeOf = (language: string) => (language.toLowerCase().startsWith("hu") ? "hu-HU" : "en-GB");

/** A calendar date from the server ("2027-05-03"), read without a time zone shift. */
const parseDate = (iso: string) => {
  const [year, month, day] = iso.split("-").map(Number);
  return new Date(year, month - 1, day);
};

/** "2027. május 3." in Hungarian, "3 May 2027" in English. */
export const formatLongDate = (iso: string | undefined, language: string) =>
  iso ? new Intl.DateTimeFormat(localeOf(language), { year: "numeric", month: "long", day: "numeric" }).format(parseDate(iso)) : "";

/** "2027. 05. 03." in Hungarian, "03/05/2027" in English. */
export const formatShortDate = (iso: string | undefined, language: string) =>
  iso ? new Intl.DateTimeFormat(localeOf(language), { year: "numeric", month: "2-digit", day: "2-digit" }).format(parseDate(iso)) : "";

/** A date before a Hungarian suffix ("-ig"): the trailing dot of the date goes. */
export const beforeSuffix = (text: string) => text.replace(/\.$/, "");

export const formatInstant = (iso: string | undefined, language: string) =>
  iso ? new Intl.DateTimeFormat(localeOf(language), { year: "numeric", month: "long", day: "numeric" }).format(new Date(iso)) : "";

/** "Versenyezhet, 2027. május 3-ig." or the ALKALMAS form, with the days left when it runs out within 30 days. */
const positiveSentence = (
  t: TFunction,
  result: MedicalResult | undefined,
  date: string,
  daysRemaining: number | undefined,
  expiring: boolean,
) => {
  const prefix = result === "ALKALMAS" ? "medical.sentence.training" : "medical.sentence.competing";
  if (!expiring) return t(`${prefix}-until`, { date: beforeSuffix(date) });
  return daysRemaining === 0 ? t(`${prefix}-today`, { date }) : t(`${prefix}-expiring`, { days: daysRemaining, date });
};

/**
 * The sentence that tells the day and what the paper says. The two result flags are never shown without it: an
 * unverified row always starts with "Leadva, ellenőrzésre vár".
 */
export const medicalSentence = (
  t: TFunction,
  language: string,
  status: MedicalState | undefined,
  validUntil: string | undefined,
  daysRemaining: number | undefined,
  result?: MedicalResult,
) => {
  const date = formatLongDate(validUntil, language);
  switch (status) {
    case "VALID":
      return positiveSentence(t, result, date, daysRemaining, false);
    case "EXPIRING":
      return positiveSentence(t, result, date, daysRemaining, true);
    case "UNVERIFIED":
      return t("medical.sentence.unverified", {
        sentence: positiveSentence(t, result, date, daysRemaining, (daysRemaining ?? 31) <= 30),
      });
    case "EXPIRED":
      return result === "IDEIGLENESEN_ELTILTVA"
        ? t("medical.sentence.suspension-ended", { date })
        : t("medical.sentence.expired", { days: Math.abs(daysRemaining ?? 0), date });
    case "SUSPENDED":
      return t("medical.sentence.suspended", { date: beforeSuffix(date) });
    case "NOT_FIT":
      return result === "VEGLEGESEN_ELTILTVA" ? t("medical.sentence.banned") : t("medical.sentence.not-fit");
    case "RESULT_MISSING":
      return t("medical.sentence.result-missing", { date: beforeSuffix(date) });
    default:
      return t("medical.sentence.missing");
  }
};

/** The short roster label: the date, "18 nap", "ma", "ellenőrzésre vár", "lejárt", "eltiltva", "nem alkalmas" or "nincs". */
export const medicalShortLabel = (
  t: TFunction,
  language: string,
  status: MedicalState,
  validUntil: string | undefined,
  daysRemaining: number | undefined,
) => {
  switch (status) {
    case "VALID":
      return formatShortDate(validUntil, language);
    case "EXPIRING":
      return daysRemaining === 0 ? t("medical.short.today") : t("medical.short.days", { days: daysRemaining });
    case "UNVERIFIED":
      return t("medical.short.unverified");
    case "EXPIRED":
      return t("medical.short.expired");
    case "SUSPENDED":
      return t("medical.short.suspended");
    case "NOT_FIT":
      return t("medical.short.not-fit");
    case "RESULT_MISSING":
      return t("medical.short.result-missing");
    default:
      return t("medical.short.missing");
  }
};

export const historyValidLabel = (t: TFunction, language: string, validUntil: string | undefined, result?: MedicalResult) => {
  const label = result ? t(`medical.result.${result}`) : "";
  if (!validUntil) return label;
  const valid = t("medical.history.valid", { date: beforeSuffix(formatShortDate(validUntil, language)) });
  return label ? `${label} · ${valid}` : valid;
};

/** The label of the date field: the end of validity, or the end of the suspension. */
export const dateLabelKey = (result: MedicalResult | "" | undefined) =>
  result === "IDEIGLENESEN_ELTILTVA" ? "medical.form.suspended-until" : "medical.form.valid-until";

export const problemDetail = (error: unknown): { status?: number; detail?: string } => {
  const response = (error as { response?: { status?: number; data?: { detail?: string } } })?.response;
  return { status: response?.status, detail: response?.data?.detail };
};

/** The count of a 409 that asks for confirmation: certificates withdrawn and files removed. */
export const deletionCounts = (error: unknown): { profiles: number; files: number } | null => {
  const response = (error as { response?: { status?: number; data?: { affectedProfiles?: number; affectedFiles?: number } } })?.response;
  if (response?.status !== 409) return null;
  const profiles = response.data?.affectedProfiles;
  const files = response.data?.affectedFiles;
  if (typeof profiles !== "number" && typeof files !== "number") return null;
  return { profiles: profiles ?? 0, files: files ?? 0 };
};
