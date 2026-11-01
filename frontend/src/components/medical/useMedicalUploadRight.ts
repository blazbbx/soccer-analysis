import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { medicalQueryKey, setMedicalUploadRight } from "../../api/medical";
import { MY_CHILDREN_QUERY_KEY } from "../../context/ChildrenContext";
import { useSnackbar } from "../../context/SnackbarContext";
import { problemDetail } from "./medicalFormat";

/**
 * Turns the child's own medical upload right on or off. The parent and the club admin may; the server
 * refuses everyone else, the platform admin too.
 */
export const useMedicalUploadRight = (profileId: string) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { showSuccess, showError } = useSnackbar();
  const [busy, setBusy] = useState(false);

  const setAllowed = async (allowed: boolean) => {
    setBusy(true);
    try {
      await setMedicalUploadRight(profileId, allowed);
      showSuccess(t("medical.right.saved"));
    } catch (err) {
      const { status, detail } = problemDetail(err);
      // A non-GET 403 is already shown by the global listener.
      if (status !== 403) showError(detail || t("medical.messages.failed"));
    } finally {
      setBusy(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: medicalQueryKey(profileId) }),
        queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY }),
      ]);
    }
  };

  return { setAllowed, busy };
};
