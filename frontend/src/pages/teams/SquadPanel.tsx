import { useState } from "react";
import { Box, Typography, Stack } from "@mui/material";
import { PlayerCard } from "./PlayerCard";
import { useTranslation } from "react-i18next";
import { type MemberInfo } from "../../api/generated/model";
import { ConfirmDeleteDialog } from "../admin/dialogs/ConfirmDeleteDialog";
import { formatPersonName } from "../../utils/stringUtils";
import type { TeamMedicalStatusItem } from "../../api/medical";
import { MedicalSummaryLine } from "../../components/medical/MedicalBits";

interface SquadPanelProps {
  squad?: MemberInfo[];
  showInviteAction?: boolean;
  onRemovePlayer?: (playerId: string) => void;
  /** The team's sport medical summary; absent when the team does not collect it or the view does not read it. */
  medical?: TeamMedicalStatusItem[];
}

export const SquadPanel = ({ squad, onRemovePlayer, medical }: SquadPanelProps) => {
  const { t } = useTranslation();
  const [playerToRemove, setPlayerToRemove] = useState<MemberInfo | null>(null);
  // One click shows only the players waiting for verification, so the coach can go through them at training.
  const [unverifiedOnly, setUnverifiedOnly] = useState(false);
  const waiting = new Set((medical ?? []).filter((item) => item.status === "UNVERIFIED").map((item) => item.profileId));
  const shown = unverifiedOnly ? (squad ?? []).filter((player) => player.id && waiting.has(player.id)) : squad;

  return (
    <Box sx={{ flex: 1 }}>
      <Stack
        direction="row"
        alignItems="center"
        spacing={2}
        flexWrap="wrap"
        useFlexGap
        sx={{ mb: 2, rowGap: 0.5 }}
      >
        <Typography variant="h3" sx={{ fontSize: "1rem", color: "text.primary" }}>
          {t("teams.squad")} ({squad?.length})
        </Typography>
        <MedicalSummaryLine
          items={medical}
          unverifiedOnly={unverifiedOnly}
          onToggleUnverified={() => setUnverifiedOnly((value) => !value)}
        />
      </Stack>

      <Box>
        {shown?.map((player) => (
          <PlayerCard
            key={player.id}
            player={player}
            medical={medical?.find((item) => item.profileId === player.id)}
            onRemove={onRemovePlayer ? () => setPlayerToRemove(player) : undefined}
          />
        ))}
      </Box>

      <ConfirmDeleteDialog
        open={!!playerToRemove}
        title={t("teams.remove-player")}
        description={t("teams.remove-player-confirm", {
          name: playerToRemove ? formatPersonName(playerToRemove.firstName, playerToRemove.lastName) : "",
        })}
        onConfirm={() => {
          if (playerToRemove?.id) onRemovePlayer?.(playerToRemove.id);
        }}
        onClose={() => setPlayerToRemove(null)}
      />
    </Box>
  );
};
