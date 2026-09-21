import { Box, Collapse, Stack, Typography, Chip, CircularProgress, IconButton } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import AddIcon from "@mui/icons-material/Add";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { STAT_COLORS } from "../../constants/colors";
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { useListClubTeams } from "../../api/generated/clubs/clubs";
import { type ClubResponse, type UpdateClubRequest } from "../../api/generated/model";
import { EditClubDialog } from "./dialogs/EditClubDialog";

export const ClubCard = ({
  club,
  canManage,
  onUpdateClub,
  onDeleteClub,
  onAddTeam,
}: {
  club: ClubResponse;
  canManage: boolean;
  onUpdateClub?: (id: string, data: UpdateClubRequest) => void;
  onDeleteClub?: (id: string) => void;
  onAddTeam?: (clubId: string) => void;
}) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [isExpanded, setIsExpanded] = useState(true);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const clubId = club.id ?? "";
  const { data: teams = [], isLoading } = useListClubTeams(clubId);

  const initials =
    club.shortName?.trim() ||
    (club.name ?? "")
      .split(" ")
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join("")
      .toUpperCase();

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
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Stack direction="row" alignItems="center" spacing={2}>
          <Box
            sx={{
              width: 56,
              height: 56,
              backgroundColor: STAT_COLORS.blueAccent,
              borderRadius: 3,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "white",
              fontWeight: "bold",
              fontSize: "1.1rem",
              overflow: "hidden",
            }}
          >
            {club.logoUrl ? (
              <Box
                component="img"
                src={club.logoUrl}
                alt={club.name}
                sx={{ width: "100%", height: "100%", objectFit: "cover" }}
              />
            ) : (
              initials
            )}
          </Box>
          <Box>
            <Typography variant="h2" sx={{ fontSize: "1.25rem", color: "text.primary" }}>
              {club.name}
            </Typography>
            <Typography variant="body2" sx={{ color: "text.secondary" }}>
              {isLoading
                ? t("common.loading")
                : t("clubs.team-count", { count: teams.length })}
            </Typography>
          </Box>
        </Stack>
        <IconButton sx={{ color: "text.secondary" }} onClick={() => setIsExpanded((open) => !open)}>
          <KeyboardArrowUpIcon
            sx={{
              transform: isExpanded ? "rotate(0deg)" : "rotate(180deg)",
              transition: "transform 0.3s",
            }}
          />
        </IconButton>
      </Stack>

      <Collapse in={isExpanded} timeout="auto" unmountOnExit>
        {isLoading ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
            <CircularProgress size={28} />
          </Box>
        ) : (
          <Stack spacing={2} sx={{ mt: 2 }}>
            {teams.length === 0 ? (
              <Typography variant="body2" sx={{ color: "text.secondary" }}>
                {t("clubs.no-teams")}
              </Typography>
            ) : (
              <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", rowGap: 1 }}>
                {teams.map((team) => (
                  <Chip
                    key={team.id}
                    label={team.name}
                    onClick={() => navigate("/teams")}
                    variant="outlined"
                  />
                ))}
              </Stack>
            )}

            {canManage && (
              <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                {onUpdateClub && (
                  <SecondaryButton
                    startIcon={<EditIcon />}
                    onClick={() => setIsEditOpen(true)}
                    sx={{ flex: 1 }}
                  >
                    {t("clubs.edit-club")}
                  </SecondaryButton>
                )}
                {onAddTeam && (
                  <PrimaryButton
                    startIcon={<AddIcon />}
                    onClick={() => onAddTeam(clubId)}
                    sx={{ flex: 1 }}
                  >
                    {t("clubs.add-team")}
                  </PrimaryButton>
                )}
              </Stack>
            )}
          </Stack>
        )}
      </Collapse>

      {onUpdateClub && onDeleteClub && (
        <EditClubDialog
          open={isEditOpen}
          onClose={() => setIsEditOpen(false)}
          onEdit={onUpdateClub}
          club={club}
          onDelete={onDeleteClub}
        />
      )}
    </Box>
  );
};
