import { Box, Collapse, Grid } from "@mui/material";
import MailOutlineIcon from "@mui/icons-material/MailOutline";
import EditIcon from "@mui/icons-material/Edit";


import { TeamCardHeader } from "./TeamCardHeader";
import { SquadPanel } from "./SquadPanel";
import { PrimaryButton } from "../ui/PrimaryButton";
import { useTranslation } from "react-i18next";
import { useState } from "react";
import { SecondaryButton } from "../ui/SecondaryButton";
import { EditTeamDialog } from "./EditTeamDialog";
import { TeamResponse, UpdateTeamRequest } from "../../../api/generated/model";

export const TeamCard = ({
  team,
  showInviteAction = false,
  onUpdateTeam,
  onDeleteTeam,
}: {
  team: TeamResponse;
  showInviteAction?: boolean;
  onUpdateTeam?: (id: string, data: UpdateTeamRequest) => void;
  onDeleteTeam?: (id: string) => void;
}) => {
  const { t } = useTranslation();
  const [isExpanded, setIsExpanded] = useState(true);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);

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
      <TeamCardHeader
        team={team}
        isExpanded={isExpanded}
        onToggle={() => {
          setIsExpanded(!isExpanded);
        }}
      />

      <Collapse in={isExpanded} timeout="auto" unmountOnExit>
        {/* 2. Alsó rész: Kétoszlopos elrendezés a Grid segítségével */}
        {/* A spacing(3) adja meg a távolságot az oszlopok között */}
        <Grid container spacing={3} sx={{ mt: 1 }}>
          {/* Bal oszlop: Játékosok listája (Nagyobb képernyőn a hely 7/12-ed részét foglalja el) */}
          <Grid size={{ xs: 12, md: 7, lg: 8 }}>
            <SquadPanel
              squad={team.players}
              showInviteAction={showInviteAction}
            />
          </Grid>

          {/* Jobb oszlop: Statisztikák, Formáció és Gomb (Nagyobb képernyőn 5/12-ed rész) */}
          <Grid size={{ xs: 12, md: 5, lg: 4 }}>
            <Box
              sx={{ display: "flex", flexDirection: "column", height: "100%" }}
            >
              {showInviteAction && onUpdateTeam && (
                <SecondaryButton
                  fullWidth
                  startIcon={<EditIcon />}
                  sx={{ mt: 2 }}
                  onClick={() => setIsEditDialogOpen(true)}
                >
                  {t("teams.edit-team")}
                </SecondaryButton>
              )}

              {showInviteAction && (
                <PrimaryButton
                  fullWidth
                  startIcon={<MailOutlineIcon />}
                  sx={{ mt: 2 }}
                >
                  {t("teams.send-invite")}
                </PrimaryButton>
              )}
            </Box>
          </Grid>
        </Grid>
      </Collapse>

      {onUpdateTeam && onDeleteTeam && (
         <EditTeamDialog 
           open={isEditDialogOpen} 
           onClose={() => setIsEditDialogOpen(false)} 
           onEdit={onUpdateTeam} 
           team={team} 
           onDelete={onDeleteTeam}
         />
      )}
    </Box>
  );
};
