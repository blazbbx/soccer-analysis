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
} from "@mui/material";
import SettingsIcon from "@mui/icons-material/Settings";
import { useTranslation } from "react-i18next";
import { useGetAllCups } from "../../../api/generated/cups/cups";
import { useGetMyTeams } from "../../../api/generated/teams/teams";
import { type CupResponse, type TeamResponse } from "../../../api/generated/model";
import { ManageCupDialog } from "../dialogs/ManageCupDialog";
import { LoadingPage } from "../../../components/LoadingPage";

export const CupsTab = () => {
  const { t } = useTranslation();
  const [managedCup, setManagedCup] = useState<CupResponse | null>(null);

  const { data: cupsData, isLoading } = useGetAllCups();
  const { data: myTeamsData } = useGetMyTeams();

  const cups = (cupsData as unknown as CupResponse[]) ?? [];
  const myTeams = (myTeamsData as unknown as TeamResponse[]) ?? [];

  if (isLoading) return <LoadingPage />;

  return (
    <Box>
      {cups.length === 0 ? (
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
                  {cup.createdBy
                    ? `${cup.createdBy.firstName ?? ""} ${cup.createdBy.lastName ?? ""}`.trim() || "—"
                    : "—"}
                </TableCell>
                <TableCell>{cup.teams?.length ?? 0}</TableCell>
                <TableCell>{cup.matches?.length ?? 0}</TableCell>
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

      <ManageCupDialog
        open={!!managedCup}
        onClose={() => setManagedCup(null)}
        cup={managedCup}
        myTeams={myTeams}
      />
    </Box>
  );
};
