import { Box, Container, Stack, TextField, Typography } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useTranslation } from "react-i18next";
import { type ClubResponse } from "../../api/generated/model";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import { matchesQuery } from "./paging";
import { ClubCard } from "./ClubCard";

export const ClubPicker = ({
  clubs,
  isPlatformAdmin,
  query,
  onQueryChange,
  onOpen,
  onCreate,
}: {
  clubs: ClubResponse[];
  isPlatformAdmin: boolean;
  query: string;
  onQueryChange: (value: string) => void;
  onOpen: (clubId: string) => void;
  onCreate: () => void;
}) => {
  const { t } = useTranslation();
  const visible = clubs.filter((club) => matchesQuery(query, [club.name, club.shortName]));

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        justifyContent="space-between"
        alignItems={{ xs: "flex-start", sm: "center" }}
        spacing={2}
        sx={{ mb: 4 }}
      >
        <Box>
          <Typography
            variant="h1"
            sx={{ fontSize: "2rem", fontWeight: "bold", color: "text.primary", mb: 0.5 }}
          >
            {isPlatformAdmin ? t("sidebar.clubs") : t("clubs.your-clubs")}
          </Typography>
          <Typography variant="body1" sx={{ color: "text.secondary" }}>
            {`${clubs.length} ${t("clubs.clubs-available")}`}
          </Typography>
        </Box>
        {isPlatformAdmin && (
          <FilledActionButton startIcon={<AddIcon />} onClick={onCreate}>
            {t("clubs.create-club")}
          </FilledActionButton>
        )}
      </Stack>

      {clubs.length === 0 ? (
        <Typography variant="body1" sx={{ color: "text.secondary" }}>
          {isPlatformAdmin ? t("clubs.no-clubs-message") : t("clubs.no-clubs-visible")}
        </Typography>
      ) : (
        <Stack spacing={2}>
          <TextField
            fullWidth
            size="small"
            label={t("clubs.search")}
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
          />
          {visible.length === 0 ? (
            <Typography variant="body2" sx={{ color: "text.secondary" }}>
              {t("clubs.search-empty-clubs")}
            </Typography>
          ) : (
            visible.map((club) => <ClubCard key={club.id} club={club} onOpen={onOpen} />)
          )}
        </Stack>
      )}
    </Container>
  );
};
