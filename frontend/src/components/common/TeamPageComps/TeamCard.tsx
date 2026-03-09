import { Box, Grid } from "@mui/material";
import MailOutlineIcon from "@mui/icons-material/MailOutline";

import { TeamDto } from "../../../types/team";

import { TeamCardHeader } from "./TeamCardHeader";
import { SquadPanel } from "./SquadPanel";
import { TeamStatsPanel } from "./TeamStatsPanel";
import { FormationPanel } from "./FormationPanel";
import { PrimaryButton } from "../ui/PrimaryButton"; 

export const TeamCard = ({ team, showInviteAction=false }: { team: TeamDto, showInviteAction?: boolean }) => {

  return (
    <Box
      sx={{
        backgroundColor: "background.paper",
        borderRadius: 3, 
        p: 3, 
        border: "1px solid",
        borderColor: "divider", 
      }}
    >
      {/* 1. Felső rész: Fejléc a logóval és a rövid statisztikával */}
      <TeamCardHeader team={team} />

      {/* 2. Alsó rész: Kétoszlopos elrendezés a Grid segítségével */}
      {/* A spacing(3) adja meg a távolságot az oszlopok között */}
      <Grid container spacing={3} sx={{ mt: 1 }}>
        {/* Bal oszlop: Játékosok listája (Nagyobb képernyőn a hely 7/12-ed részét foglalja el) */}
        <Grid size={{ xs: 12, md: 7, lg: 8 }}>
          <SquadPanel squad={team.squad} showInviteAction = {showInviteAction} />
        </Grid>

        {/* Jobb oszlop: Statisztikák, Formáció és Gomb (Nagyobb képernyőn 5/12-ed rész) */}
        <Grid size={{ xs: 12, md: 5, lg: 4 }}>
          <Box
            sx={{ display: "flex", flexDirection: "column", height: "100%" }}
          >
            <TeamStatsPanel stats={team.stats} />
            <FormationPanel formation={team.formation} />

            {showInviteAction && (
              <PrimaryButton
                fullWidth
                startIcon={<MailOutlineIcon />}
                sx={{ mt: 2 }}
              >
                Send Invite
              </PrimaryButton>
            )}
          </Box>
        </Grid>
      </Grid>
    </Box>
  );
};
