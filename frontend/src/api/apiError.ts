import type { AxiosError } from "axios";

/**
 * The backend's own message (ProblemDetail `detail`) for a failed request, or the fallback.
 * Dialogs show it in an Alert instead of swallowing the error.
 */
export const apiErrorMessage = (error: unknown, fallback: string): string => {
  const detail = (error as AxiosError<{ detail?: string }> | undefined)?.response?.data?.detail;
  return typeof detail === "string" && detail.trim() ? detail : fallback;
};

export const apiErrorStatus = (error: unknown): number | undefined =>
  (error as AxiosError | undefined)?.response?.status;
