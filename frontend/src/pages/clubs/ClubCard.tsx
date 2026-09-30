import { Box, Stack, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { STAT_COLORS } from "../../constants/colors";
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { type ClubResponse } from "../../api/generated/model";
import { monogram } from "./paging";

export const ClubCard = ({
  club,
  onOpen,
}: {
  club: ClubResponse;
  onOpen: (clubId: string) => void;
}) => {
  const { t } = useTranslation();
  const clubId = club.id ?? "";
  const initials = monogram(club.name, club.shortName);

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
      <Stack
        direction={{ xs: "column", sm: "row" }}
        alignItems={{ xs: "flex-start", sm: "center" }}
        justifyContent="space-between"
        spacing={2}
      >
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
        <PrimaryButton onClick={() => onOpen(clubId)} disabled={!clubId}>
          {t("clubs.open-club")}
        </PrimaryButton>
      </Stack>
    </Box>
  );
};
