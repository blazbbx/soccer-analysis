import { Box, Typography, Stack } from "@mui/material";
import PersonAddAlt1Icon from "@mui/icons-material/PersonAddAlt1";
import { PlayerCard } from "./PlayerCard";
import { PlayerDto } from "../../../types/team";
import { SecondaryButton } from "../ui/SecondaryButton";

export const SquadPanel = ({
  squad,
  showInviteAction = false,
}: {
  squad: PlayerDto[];
  showInviteAction?: boolean;
}) => {
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
          Squad ({squad.length})
        </Typography>
        {showInviteAction && (
          <SecondaryButton startIcon={<PersonAddAlt1Icon />}>
            Invite
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
