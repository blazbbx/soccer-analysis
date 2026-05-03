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
import { People as PeopleIcon, Groups as GroupsIcon, SportsSoccer as MatchesIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useGetAllUsers } from '../../api/generated/user-controller/user-controller';
import { useGetMyTeams } from '../../api/generated/teams/teams';
import { useGetAllMatches } from '../../api/generated/match-controller/match-controller';
import type { UserResponse } from '../../api/generated/model';
import { UsersTab } from './tabs/UsersTab';
import { TeamsTab } from './tabs/TeamsTab';
import { MatchesTab } from './tabs/MatchesTab';

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

  const users = (usersBlob as unknown as UserResponse[]) ?? [];

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Typography variant="h4" fontWeight="bold" sx={{ mb: 4 }}>
        {t('admin.title')}
      </Typography>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 4 }}>
        <StatCard icon={<PeopleIcon />} label={t('admin.total-users')} count={users.length} />
        <StatCard icon={<GroupsIcon />} label={t('admin.total-teams')} count={teams.length} />
        <StatCard icon={<MatchesIcon />} label={t('admin.total-matches')} count={matches.length} />
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
        </Box>
      </Paper>
    </Container>
  );
};
