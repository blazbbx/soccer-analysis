import { Box, Collapse, Grid } from "@mui/material";
import MailOutlineIcon from "@mui/icons-material/MailOutline";
import EditIcon from "@mui/icons-material/Edit";


import { TeamCardHeader } from "./TeamCardHeader";
import { SquadPanel } from "./SquadPanel";
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { useTranslation } from "react-i18next";
import { useState } from "react";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { EditTeamDialog } from "./DialogComps/EditTeamDialog";
import { type TeamResponse, type UpdateTeamRequest } from "../../api/generated/model";

export const TeamCard = ({
  team,
  showInviteAction = false,
  onUpdateTeam,
  onDeleteTeam,
  onCreateInvite,
  onRemovePlayer,
}: {
  team: TeamResponse;
  showInviteAction?: boolean;
  onUpdateTeam?: (id: string, teamData: UpdateTeamRequest) => void;
  onDeleteTeam?: (id: string) => void;
  onCreateInvite?: (id: string) => void;
  onRemovePlayer?: (playerId: string) => void;
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
      <TeamCardHeader
        team={team}
        isExpanded={isExpanded}
        onToggle={() => {
          setIsExpanded(!isExpanded);
        }}
      />

      <Collapse in={isExpanded} timeout="auto" unmountOnExit>
        <Grid container spacing={3} sx={{ mt: 1 }}>
          <Grid size={{ xs: 12, md: 7, lg: 8 }}>
            <SquadPanel
              squad={team.players}
              showInviteAction={showInviteAction}
              onRemovePlayer={onRemovePlayer}
            />
          </Grid>

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
                  onClick={() => onCreateInvite?.(team.id ?? " ")}
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
