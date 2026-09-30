import { useState, type ReactNode } from "react";
import { Box, Chip, Container, Stack, Tab, Tabs, Typography } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useTranslation } from "react-i18next";
import { type ClubResponse } from "../../api/generated/model";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { OverviewTab } from "./tabs/OverviewTab";
import { ClubUsersTab } from "./tabs/ClubUsersTab";
import { ClubTeamsTab } from "./tabs/ClubTeamsTab";
import { ClubAdminsTab } from "./tabs/ClubAdminsTab";
import { ClubDetailsTab } from "./tabs/ClubDetailsTab";

const TabPanel = ({
  children,
  value,
  index,
}: {
  children: ReactNode;
  value: number;
  index: number;
}) => (
  <Box role="tabpanel" hidden={value !== index} sx={{ pt: 3 }}>
    {value === index && children}
  </Box>
);

export const ClubWorkspace = ({
  club,
  clubCount,
  isPlatformAdmin,
  showBack,
  canManage,
  usersQuery,
  usersPage,
  teamsQuery,
  teamsPage,
  adminsQuery,
  adminsPage,
  onUsersQueryChange,
  onUsersPageChange,
  onTeamsQueryChange,
  onTeamsPageChange,
  onAdminsQueryChange,
  onAdminsPageChange,
  onBack,
  onAddTeam,
  onInvite,
  onLeftClub,
  onEdit,
}: {
  club: ClubResponse;
  clubCount: number;
  isPlatformAdmin: boolean;
  showBack: boolean;
  canManage: boolean;
  usersQuery: string;
  usersPage: number;
  teamsQuery: string;
  teamsPage: number;
  adminsQuery: string;
  adminsPage: number;
  onUsersQueryChange: (value: string) => void;
  onUsersPageChange: (page: number) => void;
  onTeamsQueryChange: (value: string) => void;
  onTeamsPageChange: (page: number) => void;
  onAdminsQueryChange: (value: string) => void;
  onAdminsPageChange: (page: number) => void;
  onBack: () => void;
  onAddTeam: () => void;
  onInvite: () => void;
  onLeftClub: () => void;
  onEdit: () => void;
}) => {
  const { t } = useTranslation();
  const [tab, setTab] = useState(0);
  const clubId = club.id ?? "";
  const title = isPlatformAdmin
    ? t("clubs.preview-title", { name: club.name ?? "" })
    : t("clubs.workspace-title", { name: club.name ?? "" });

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={2} sx={{ mb: 3 }}>
        {showBack && (
          <Box>
            <SecondaryButton startIcon={<ArrowBackIcon />} onClick={onBack}>
              {t("clubs.back-to-picker")}
            </SecondaryButton>
          </Box>
        )}
        <Typography
          variant="h1"
          sx={{ fontSize: "2rem", fontWeight: "bold", color: "text.primary" }}
        >
          {title}
        </Typography>
        <Chip
          size="small"
          color={isPlatformAdmin ? "error" : "warning"}
          label={t(isPlatformAdmin ? "roles.admin" : "roles.club_admin")}
          sx={{ alignSelf: "flex-start" }}
        />
      </Stack>

      <Tabs
        value={tab}
        onChange={(_event, next: number) => setTab(next)}
        variant="scrollable"
        scrollButtons="auto"
      >
        <Tab label={t("clubs.tab.overview")} />
        <Tab label={t("clubs.tab.users")} />
        <Tab label={t("clubs.tab.teams")} />
        <Tab label={t("clubs.tab.admins")} />
        <Tab label={t("clubs.tab.details")} />
      </Tabs>

      <TabPanel value={tab} index={0}>
        <OverviewTab clubId={clubId} clubCount={clubCount} />
      </TabPanel>
      <TabPanel value={tab} index={1}>
        <ClubUsersTab
          clubId={clubId}
          clubName={club.name}
          clubShortName={club.shortName}
          isPlatformAdmin={isPlatformAdmin}
          query={usersQuery}
          page={usersPage}
          onQueryChange={onUsersQueryChange}
          onPageChange={onUsersPageChange}
        />
      </TabPanel>
      <TabPanel value={tab} index={2}>
        <ClubTeamsTab
          clubId={clubId}
          clubName={club.name}
          clubShortName={club.shortName}
          canManage={canManage}
          query={teamsQuery}
          page={teamsPage}
          onQueryChange={onTeamsQueryChange}
          onPageChange={onTeamsPageChange}
          onAddTeam={onAddTeam}
        />
      </TabPanel>
      <TabPanel value={tab} index={3}>
        <ClubAdminsTab
          clubId={clubId}
          canManage={canManage}
          query={adminsQuery}
          page={adminsPage}
          onQueryChange={onAdminsQueryChange}
          onPageChange={onAdminsPageChange}
          onInvite={onInvite}
          onLeftClub={onLeftClub}
        />
      </TabPanel>
      <TabPanel value={tab} index={4}>
        <ClubDetailsTab club={club} canManage={canManage} onEdit={onEdit} />
      </TabPanel>
    </Container>
  );
};
