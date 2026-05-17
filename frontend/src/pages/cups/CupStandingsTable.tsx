import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { type CupStandingsRowResponse } from "../../api/generated/model";
import { STAT_COLORS } from "../../constants/colors";

interface CupStandingsTableProps {
  rows: CupStandingsRowResponse[];
}

const gdColor = (gd: number) => {
  if (gd > 0) return STAT_COLORS.wins;
  if (gd < 0) return STAT_COLORS.losses;
  return "text.secondary";
};

export const CupStandingsTable = ({ rows }: CupStandingsTableProps) => {
  const sorted = [...rows].sort((a, b) => {
    const pts = (b.points ?? 0) - (a.points ?? 0);
    if (pts !== 0) return pts;
    return (b.goalDifference ?? 0) - (a.goalDifference ?? 0);
  });

  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell sx={{ fontWeight: "bold", width: 32, color: "text.secondary" }}>#</TableCell>
            <TableCell sx={{ fontWeight: "bold" }}>Team</TableCell>
            <TableCell align="center" sx={{ fontWeight: "bold", color: "text.secondary" }}>P</TableCell>
            <TableCell align="center" sx={{ fontWeight: "bold", color: STAT_COLORS.wins }}>W</TableCell>
            <TableCell align="center" sx={{ fontWeight: "bold", color: STAT_COLORS.draws }}>D</TableCell>
            <TableCell align="center" sx={{ fontWeight: "bold", color: STAT_COLORS.losses }}>L</TableCell>
            <TableCell align="center" sx={{ fontWeight: "bold", color: "text.secondary" }}>GF</TableCell>
            <TableCell align="center" sx={{ fontWeight: "bold", color: "text.secondary" }}>GA</TableCell>
            <TableCell align="center" sx={{ fontWeight: "bold", color: "text.secondary" }}>GD</TableCell>
            <TableCell align="center" sx={{ fontWeight: "bold", color: STAT_COLORS.points }}>Pts</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {sorted.map((row, index) => (
            <TableRow key={row.teamId ?? index} hover>
              <TableCell>
                <Typography variant="body2" sx={{ color: "text.secondary" }}>
                  {index + 1}
                </Typography>
              </TableCell>
              <TableCell>{row.teamName}</TableCell>
              <TableCell align="center">{row.played ?? 0}</TableCell>
              <TableCell align="center">
                <Typography variant="body2" sx={{ color: STAT_COLORS.wins }}>
                  {row.wins ?? 0}
                </Typography>
              </TableCell>
              <TableCell align="center">
                <Typography variant="body2" sx={{ color: STAT_COLORS.draws }}>
                  {row.draws ?? 0}
                </Typography>
              </TableCell>
              <TableCell align="center">
                <Typography variant="body2" sx={{ color: STAT_COLORS.losses }}>
                  {row.losses ?? 0}
                </Typography>
              </TableCell>
              <TableCell align="center">{row.goalsFor ?? 0}</TableCell>
              <TableCell align="center">{row.goalsAgainst ?? 0}</TableCell>
              <TableCell align="center">
                <Typography variant="body2" sx={{ color: gdColor(row.goalDifference ?? 0) }}>
                  {row.goalDifference ?? 0}
                </Typography>
              </TableCell>
              <TableCell align="center">
                <Typography variant="body2" fontWeight="bold" sx={{ color: STAT_COLORS.points }}>
                  {row.points ?? 0}
                </Typography>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};
