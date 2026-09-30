import { Box, Stack, Typography } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import { useTranslation } from "react-i18next";
import { type ClubResponse } from "../../../api/generated/model";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { STAT_COLORS } from "../../../constants/colors";
import { monogram } from "../paging";

export const ClubDetailsTab = ({
  club,
  canManage,
  onEdit,
}: {
  club: ClubResponse;
  canManage: boolean;
  onEdit: () => void;
}) => {
  const { t } = useTranslation();
  const initials = monogram(club.name, club.shortName);

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Box
          sx={{
            width: 72,
            height: 72,
            backgroundColor: STAT_COLORS.blueAccent,
            borderRadius: 3,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "white",
            fontWeight: "bold",
            fontSize: "1.25rem",
            overflow: "hidden",
            flexShrink: 0,
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
          {club.shortName?.trim() && (
            <Typography variant="body2" sx={{ color: "text.secondary" }}>
              {club.shortName}
            </Typography>
          )}
        </Box>
      </Stack>
      {canManage && (
        <Box>
          <SecondaryButton startIcon={<EditIcon />} onClick={onEdit}>
            {t("clubs.edit-club")}
          </SecondaryButton>
        </Box>
      )}
    </Stack>
  );
};
