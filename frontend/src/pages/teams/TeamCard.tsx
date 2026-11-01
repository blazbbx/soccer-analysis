import { Box, Collapse, Grid, Typography } from "@mui/material";
import MailOutlineIcon from "@mui/icons-material/MailOutline";
import EditIcon from "@mui/icons-material/Edit";


import { TeamCardHeader } from "./TeamCardHeader";
import { SquadPanel } from "./SquadPanel";
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { useTranslation } from "react-i18next";
import { useState } from "react";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { EditTeamDialog } from "./DialogComps/EditTeamDialog";
import { type UpdateTeamRequest } from "../../api/generated/model";
import { type TeamCatalogItem } from "../../api/roleTeams";
import { useGetTeam } from "../../api/generated/teams/teams";
import { useQuery } from "@tanstack/react-query";
import { getTeamMedicalStatus, teamMedicalQueryKey } from "../../api/medical";
import { useView } from "../../context/ViewContext";
import { ROLES } from "../../types/roles";

export const TeamCard = ({
  team,
  showInviteAction = false,
  onUpdateTeam,
  onDeleteTeam,
  onCreateInvite,
  onRemovePlayer,
  startExpanded = false,
}: {
  team: TeamCatalogItem;
  showInviteAction?: boolean;
  onUpdateTeam?: (id: string, teamData: UpdateTeamRequest) => void;
  onDeleteTeam?: (id: string) => void;
  onCreateInvite?: (id: string) => void;
  onRemovePlayer?: (playerId: string) => void;
  startExpanded?: boolean;
}) => {
  const { t } = useTranslation();
  const [isExpanded, setIsExpanded] = useState(startExpanded);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const teamId = team.id ?? "";
  const { data: detail, isLoading: detailLoading, isError: detailError } = useGetTeam(teamId, {
    query: { enabled: isExpanded && teamId.length > 0 },
  });
  const { activeView } = useView();
  // Only the coach view reads the sport medical summary, together with the detail. The platform admin has none.
  const medicalView = activeView === ROLES.COACH;
  const { data: medical } = useQuery({
    queryKey: teamMedicalQueryKey(teamId),
    queryFn: () => getTeamMedicalStatus(teamId),
    enabled: isExpanded && medicalView && teamId.length > 0,
    retry: false,
  });

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
            {detailError ? (
              <Typography color="error" sx={{ mt: 2 }}>{t("teams.error.generic")}</Typography>
            ) : detailLoading || !detail ? (
              <Typography color="text.secondary" sx={{ mt: 2 }}>{t("common.loading")}</Typography>
            ) : (
              <SquadPanel
                squad={detail.players}
                showInviteAction={showInviteAction}
                onRemovePlayer={onRemovePlayer}
                medical={medical?.enabled ? medical.items : undefined}
              />
            )}
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
           team={{ id: team.id, name: team.name, shortName: team.shortName }} 
           onDelete={onDeleteTeam}
         />
      )}
    </Box>
  );
};
