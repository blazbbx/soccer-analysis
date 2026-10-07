import { useState } from "react";
import {
  Box,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  IconButton,
  Typography,
  Tooltip,
  Alert,
} from "@mui/material";
import SettingsIcon from "@mui/icons-material/Settings";
import { useTranslation } from "react-i18next";
import { LIST_PAGE_SIZE } from "../../../api/paging";
import { useListPage, usePagedCups, useRetreatEmptyPage } from "../../../api/pagedLists";
import { ListPager } from "../../../components/ListPager";
import { useRoleTeams } from "../../../api/roleTeams";
import { ManageCupDialog } from "../dialogs/ManageCupDialog";
import { LoadingPage } from "../../../components/LoadingPage";
import { formatPersonName } from "../../../utils/stringUtils";

export const CupsTab = () => {
  const { t } = useTranslation();
  const [managedCup, setManagedCup] = useState<{ id?: string; name?: string | null } | null>(null);
  const [page, setPage] = useListPage("cups");
  const { data: cupsData, isLoading, isError } = usePagedCups({ page, size: LIST_PAGE_SIZE });
  useRetreatEmptyPage(page, cupsData?.items.length, cupsData?.totalPages, setPage);
  const { data: myTeamsData } = useRoleTeams();

  const cups = cupsData?.items ?? [];
  const myTeams = myTeamsData?.items ?? [];

  if (isLoading) return <LoadingPage />;

  return (
    <Box>
      {isError ? (
        <Alert severity="error">{t("cups.load-error")}</Alert>
      ) : cups.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: "center", py: 4 }}>
          {t("admin.no-cups")}
        </Typography>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: "bold" }}>{t("cups.cup-name")}</TableCell>
              <TableCell sx={{ fontWeight: "bold" }}>{t("admin.created-by")}</TableCell>
              <TableCell sx={{ fontWeight: "bold" }}>{t("admin.cup-teams")}</TableCell>
              <TableCell sx={{ fontWeight: "bold" }}>{t("admin.cup-matches")}</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {cups.map((cup) => (
              <TableRow key={cup.id} hover>
                <TableCell>{cup.name}</TableCell>
                <TableCell>
                  {formatPersonName(cup.createdByFirstName, cup.createdByLastName) || "—"}
                </TableCell>
                <TableCell>{cup.teamCount}</TableCell>
                <TableCell>{cup.matchCount}</TableCell>
                <TableCell align="right">
                  <Tooltip title={t("admin.manage-cup")}>
                    <IconButton size="small" onClick={() => setManagedCup(cup)}>
                      <SettingsIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}>
        <ListPager page={page} totalPages={cupsData?.totalPages ?? 0} onChange={setPage} />
      </Box>

      <ManageCupDialog
        open={!!managedCup}
        onClose={() => setManagedCup(null)}
        cup={managedCup}
        myTeams={myTeams}
      />
    </Box>
  );
};
