import { Chip } from "@mui/material";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";
import type { AssignmentStatus } from "../../../api/homework";

const COLORS: Record<AssignmentStatus, "default" | "info" | "warning" | "success" | "primary"> = {
  ASSIGNED: "primary",
  SUBMITTED: "info",
  RETURNED: "warning",
  COMPLETED: "success",
};

/**
 * {@code notSubmitted}: the homework was archived before a hand-in, which says more than the last status. The wording
 * follows who reads it: "not handed in" for the coach, "you did not hand it in" for the player's side.
 */
export const AssignmentStatusChip = ({ status, notSubmitted }: { status: AssignmentStatus; notSubmitted?: "staff" | "recipient" }) => {
  const { t } = useTranslation();
  if (notSubmitted) {
    return <Chip size="small" color="error" variant="outlined" label={t(notSubmitted === "staff" ? "homework.not-submitted-staff" : "homework.not-submitted")} />;
  }
  return <Chip size="small" label={t(`homework.status.${status}`)} color={COLORS[status]} />;
};

/** The computed deadline flags as one label. Lateness is never a stored status. */
export const DueLabel = ({
  overdue,
  dueSoon,
  late,
  lateBlocked,
  dueAt,
}: {
  overdue?: boolean;
  dueSoon?: boolean;
  late?: boolean;
  lateBlocked?: boolean;
  dueAt?: string | null;
}) => {
  const { t } = useTranslation();
  if (lateBlocked) return <Chip size="small" color="error" label={t("homework.flag.blocked")} />;
  if (overdue) return <Chip size="small" color="error" label={t("homework.flag.overdue")} />;
  if (dueSoon) {
    const today = dayjs(dueAt).isSame(dayjs(), "day");
    return <Chip size="small" color="warning" label={t(today ? "homework.flag.today" : "homework.flag.tomorrow")} />;
  }
  if (late) return <Chip size="small" color="warning" variant="outlined" label={t("homework.flag.late")} />;
  return null;
};
