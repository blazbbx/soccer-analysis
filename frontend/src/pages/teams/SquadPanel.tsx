import { Box, Typography, Stack } from "@mui/material";
import { PlayerCard } from "./PlayerCard";
import { useTranslation } from "react-i18next";
import { type MemberInfo } from "../../api/generated/model";

export const SquadPanel = ({
  squad
}: {
  squad?: MemberInfo[];
  showInviteAction?: boolean;
}) => {
  const {t} = useTranslation();  

  return (
    <Box sx={{ flex: 1 }}>
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
          {t("teams.squad")} ({squad?.length})
        </Typography>
      </Stack>

      {/* Játékosok listázása */}
      <Box>
        {squad?.map((player) => (
          <PlayerCard key={player.id} player={player} />
        ))}
      </Box>
    </Box>
  );
};
