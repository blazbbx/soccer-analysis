import { useState } from "react";
import { useTranslation } from "react-i18next";
import { apiErrorMessage } from "../../../api/apiError";

/**
 * Saving state for a cup dialog (plan 1.6). `run` awaits the request: on success it returns true,
 * on failure it keeps the dialog open with the backend's message, and the typed fields stay.
 */
export const useDialogSubmit = () => {
  const { t } = useTranslation();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (action: () => Promise<unknown>): Promise<boolean> => {
    setSaving(true);
    setError(null);
    try {
      await action();
      return true;
    } catch (e) {
      setError(apiErrorMessage(e, t("cups.save-error")));
      return false;
    } finally {
      setSaving(false);
    }
  };

  return { saving, error, run, clearError: () => setError(null) };
};
