import { Box, Typography, Stack } from "@mui/material";
import PersonAddAlt1Icon from "@mui/icons-material/PersonAddAlt1";
import { PlayerCard } from "./PlayerCard";
import { PlayerDto } from "../../../types/team";
import { SecondaryButton } from "../ui/SecondaryButton";
import { useTranslation } from "react-i18next";

export const SquadPanel = ({
  squad,
  showInviteAction = false,
}: {
  squad: PlayerDto[];
  showInviteAction?: boolean;
}) => {
  const {t} = useTranslation();  

  return (
    <Box sx={{ flex: 1 }}>
      {/* Fejléc a címmel és az Invite gombbal */}
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 2 }}
      >
        <Typography
          variant="h3"
          sx={{ fontSize: "1rem", color: "text.primary" }}
        >
          {t("teams.squad")} ({squad.length})
        </Typography>
        {showInviteAction && (
          <SecondaryButton startIcon={<PersonAddAlt1Icon />}>
            {t("teams.invite")}
          </SecondaryButton>
        )}
      </Stack>

      {/* Játékosok listázása */}
      <Box>
        {squad.map((player) => (
          <PlayerCard key={player.id} player={player} />
        ))}
      </Box>
    </Box>
  );
};
