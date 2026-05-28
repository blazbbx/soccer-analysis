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
import { People as PeopleIcon, Groups as GroupsIcon, SportsSoccer as MatchesIcon, EmojiEvents as CupsIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useGetAllUsers } from '../../api/generated/user-controller/user-controller';
import { useGetMyTeams } from '../../api/generated/teams/teams';
import { useGetAllMatches } from '../../api/generated/match-controller/match-controller';
import { useGetAllCups } from '../../api/generated/cups/cups';
import type { UserResponse, CupResponse } from '../../api/generated/model';
import { UsersTab } from './tabs/UsersTab';
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

  const { data: usersBlob } = useGetAllUsers();
  const { data: teams = [] } = useGetMyTeams();
  const { data: matches = [] } = useGetAllMatches();
  const { data: cupsData } = useGetAllCups();

  const users = (usersBlob as unknown as UserResponse[]) ?? [];
  const cups = (cupsData as unknown as CupResponse[]) ?? [];

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Typography variant="h4" fontWeight="bold" sx={{ mb: 4 }}>
        {t('admin.title')}
      </Typography>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 4 }}>
        <StatCard icon={<PeopleIcon />} label={t('admin.total-users')} count={users.length} />
        <StatCard icon={<GroupsIcon />} label={t('admin.total-teams')} count={teams.length} />
        <StatCard icon={<MatchesIcon />} label={t('admin.total-matches')} count={matches.length} />
        <StatCard icon={<CupsIcon />} label={t('admin.total-cups')} count={cups.length} />
      </Stack>

      <Paper sx={{ borderRadius: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, v) => setActiveTab(v)}
          sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}
        >
          <Tab label={t('admin.users')} />
          <Tab label={t('admin.teams')} />
          <Tab label={t('admin.matches')} />
          <Tab label={t('admin.cups')} />
        </Tabs>

        <Box sx={{ p: 3 }}>
          <TabPanel value={activeTab} index={0}>
            <UsersTab />
          </TabPanel>
          <TabPanel value={activeTab} index={1}>
            <TeamsTab />
          </TabPanel>
          <TabPanel value={activeTab} index={2}>
            <MatchesTab />
          </TabPanel>
          <TabPanel value={activeTab} index={3}>
            <CupsTab />
          </TabPanel>
        </Box>
      </Paper>
    </Container>
  );
};
