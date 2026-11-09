import { useEffect, useState } from "react";
import { Alert, Box, Card, CardActionArea, CircularProgress, Stack, Tab, Tabs, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  formatHomeworkDate, listMyHomeworks, myHomeworkKey, type MyHomework, type RecipientTab,
  closedNotSubmitted,
} from "../../../api/homework";
import { ListPager } from "../../../components/ListPager";
import { useChildren } from "../../../context/ChildrenContext";
import { useView } from "../../../context/ViewContext";
import { ROLES } from "../../../types/roles";
import { AssignmentStatusChip, DueLabel } from "../shared/AssignmentStatusChip";

const TABS: RecipientTab[] = ["todo", "submitted", "done"];

const MyHomeworkCard = ({ homework }: { homework: MyHomework }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Card variant="outlined">
      <CardActionArea onClick={() => navigate(`/homework/assignments/${homework.assignmentId}`)} sx={{ p: 2 }}>
        <Stack spacing={0.5}>
          <Typography sx={{ fontWeight: 700, overflowWrap: "anywhere" }}>{homework.title}</Typography>
          <Typography variant="body2" sx={{ color: "text.secondary" }}>
            {homework.teamName}
            {" · "}
            {homework.effectiveDueAt ? t("homework.due", { date: formatHomeworkDate(homework.effectiveDueAt) }) : t("homework.no-due")}
          </Typography>
          <Stack direction="row" spacing={0.75} sx={{ flexWrap: "wrap", rowGap: 0.5, pt: 0.5 }}>
            <AssignmentStatusChip status={homework.status} notSubmitted={closedNotSubmitted(homework.homeworkStatus === "ARCHIVED", homework.status) ? "recipient" : undefined} />
            <DueLabel overdue={homework.overdue} dueSoon={homework.dueSoon} late={homework.late} lateBlocked={homework.lateBlocked} dueAt={homework.effectiveDueAt} />
          </Stack>
        </Stack>
      </CardActionArea>
    </Card>
  );
};

export const MyHomeworkList = () => {
  const { t } = useTranslation();
  const { activeView } = useView();
  const { selected, isLoading: loadingChildren } = useChildren();
  const parentView = activeView === ROLES.PARENT;
  const profileId = parentView ? selected?.profileId : undefined;
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<RecipientTab>(() => {
    const requested = searchParams.get("tab") as RecipientTab | null;
    return requested && TABS.includes(requested) ? requested : "todo";
  });
  const [page, setPage] = useState(1);

  useEffect(() => {
    setPage(1);
  }, [tab, profileId]);

  const list = useQuery({
    queryKey: myHomeworkKey(tab, profileId ?? "", page),
    queryFn: ({ signal }) => listMyHomeworks(tab, profileId, page, signal),
    enabled: !parentView || Boolean(profileId),
    retry: false,
  });

  if (parentView && loadingChildren) {
    return <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}><CircularProgress /></Box>;
  }

  return (
    <Box sx={{ py: { xs: 2, md: 2.5 }, px: { xs: 2, md: 3 }, maxWidth: 860 }}>
      <Typography sx={{ fontSize: { xs: 24, md: 28 }, fontWeight: 800, mb: 1 }}>{t("sidebar.homework")}</Typography>
      <Tabs value={tab} onChange={(_event, next: RecipientTab) => setTab(next)} sx={{ mb: 2 }}>
        {TABS.map((value) => (
          <Tab key={value} value={value} label={t(`homework.tabs.${value}`)} />
        ))}
      </Tabs>
      {list.isError && <Alert severity="error">{t("homework.load-failed")}</Alert>}
      {list.isLoading && <CircularProgress size={28} />}
      <Stack spacing={1.25}>
        {(list.data?.items ?? []).map((homework) => <MyHomeworkCard key={homework.assignmentId} homework={homework} />)}
      </Stack>
      {list.data && list.data.items.length === 0 && <Typography sx={{ color: "text.secondary" }}>{t("homework.empty")}</Typography>}
      <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}>
        <ListPager page={page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />
      </Box>
    </Box>
  );
};
