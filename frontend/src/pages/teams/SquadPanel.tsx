import { useState } from "react";
import { Box, Typography, Stack } from "@mui/material";
import { PlayerCard } from "./PlayerCard";
import { useTranslation } from "react-i18next";
import { type MemberInfo } from "../../api/generated/model";
import { ConfirmDeleteDialog } from "../admin/dialogs/ConfirmDeleteDialog";

interface SquadPanelProps {
  squad?: MemberInfo[];
  showInviteAction?: boolean;
  onRemovePlayer?: (playerId: string) => void;
}

export const SquadPanel = ({ squad, onRemovePlayer }: SquadPanelProps) => {
  const { t } = useTranslation();
  const [playerToRemove, setPlayerToRemove] = useState<MemberInfo | null>(null);

  return (
    <Box sx={{ flex: 1 }}>
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 2 }}
      >
        <Typography variant="h3" sx={{ fontSize: "1rem", color: "text.primary" }}>
          {t("teams.squad")} ({squad?.length})
        </Typography>
      </Stack>

      <Box>
        {squad?.map((player) => (
          <PlayerCard
            key={player.id}
            player={player}
            onRemove={onRemovePlayer ? () => setPlayerToRemove(player) : undefined}
          />
        ))}
      </Box>

      <ConfirmDeleteDialog
        open={!!playerToRemove}
        title={t("teams.remove-player")}
        description={t("teams.remove-player-confirm", {
          name: playerToRemove ? `${playerToRemove.firstName} ${playerToRemove.lastName}` : "",
        })}
        onConfirm={() => {
          if (playerToRemove?.id) onRemovePlayer?.(playerToRemove.id);
        }}
        onClose={() => setPlayerToRemove(null)}
      />
    </Box>
  );
};
