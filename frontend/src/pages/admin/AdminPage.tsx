import { useState } from 'react';
import {
  Box,
  Container,
  Typography,
  Tab,
  Tabs,
  Paper,
  Stack,
} from '@mui/material';
import { People as PeopleIcon, Groups as GroupsIcon, SportsSoccer as MatchesIcon, EmojiEvents as CupsIcon, Business as ClubsIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useRoleTeams } from '../../api/roleTeams';
import { usePagedClubs, usePagedCups, usePagedMatches, usePagedUsers } from '../../api/pagedLists';
import { UsersTab } from './tabs/UsersTab';
import { ClubsTab } from './tabs/ClubsTab';
import { TeamsTab } from './tabs/TeamsTab';
import { MatchesTab } from './tabs/MatchesTab';
import { CupsTab } from './tabs/CupsTab';

interface StatCardProps {
  icon: React.ReactNode;
  label: string;
  count: number;
}

const StatCard = ({ icon, label, count }: StatCardProps) => (
  <Paper
    sx={{
      flex: 1,
      p: 3,
      borderRadius: 3,
      display: 'flex',
      alignItems: 'center',
      gap: 2,
    }}
  >
    <Box
      sx={{
        width: 48,
        height: 48,
        borderRadius: 2,
        bgcolor: 'rgba(16, 185, 129, 0.1)',
        color: '#10b981',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {icon}
    </Box>
    <Box>
      <Typography variant="h5" fontWeight="bold">
        {count}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </Box>
  </Paper>
);

interface TabPanelProps {
  children: React.ReactNode;
  value: number;
  index: number;
}

const TabPanel = ({ children, value, index }: TabPanelProps) => (
  <Box role="tabpanel" hidden={value !== index} sx={{ pt: 3 }}>
    {value === index && children}
  </Box>
);

export const AdminPage = () => {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState(0);

  const { data: usersPage } = usePagedUsers(1, 1);
  const { data: teams } = useRoleTeams();
  const { data: matchesPage } = usePagedMatches({ page: 1, size: 1 });
  const { data: cupsPage } = usePagedCups({ page: 1, size: 1 });
  const { data: clubsPage } = usePagedClubs(1, 1);

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Typography variant="h4" fontWeight="bold" sx={{ mb: 4 }}>
        {t('admin.title')}
      </Typography>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 4 }}>
        <StatCard icon={<PeopleIcon />} label={t('admin.total-users')} count={usersPage?.total ?? 0} />
        <StatCard icon={<ClubsIcon />} label={t('admin.total-clubs')} count={clubsPage?.total ?? 0} />
        <StatCard icon={<GroupsIcon />} label={t('admin.total-teams')} count={teams?.total ?? 0} />
        <StatCard icon={<MatchesIcon />} label={t('admin.total-matches')} count={matchesPage?.total ?? 0} />
        <StatCard icon={<CupsIcon />} label={t('admin.total-cups')} count={cupsPage?.total ?? 0} />
      </Stack>

      <Paper sx={{ borderRadius: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, v) => setActiveTab(v)}
          sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}
        >
          <Tab label={t('admin.users')} />
          <Tab label={t('admin.clubs')} />
          <Tab label={t('admin.teams')} />
          <Tab label={t('admin.matches')} />
          <Tab label={t('admin.cups')} />
        </Tabs>

        <Box sx={{ p: 3 }}>
          <TabPanel value={activeTab} index={0}>
            <UsersTab />
          </TabPanel>
          <TabPanel value={activeTab} index={1}>
            <ClubsTab />
          </TabPanel>
          <TabPanel value={activeTab} index={2}>
            <TeamsTab />
          </TabPanel>
          <TabPanel value={activeTab} index={3}>
            <MatchesTab />
          </TabPanel>
          <TabPanel value={activeTab} index={4}>
            <CupsTab />
          </TabPanel>
        </Box>
      </Paper>
    </Container>
  );
};
