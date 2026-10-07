import { useEffect, useState } from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';
import { useRoleTeams, type TeamCatalogItem } from '../../api/roleTeams';
import type { TeamPick } from '../../components/TeamAutocomplete';
import { useAuth } from '../../context/AuthContext';
import { useView } from '../../context/ViewContext';
import { useChildScope } from '../../hooks/useChildScope';
import { ROLES } from '../../types/roles';
import { ChatLeftPanel } from './ChatLeftPanel';
import { ChatMainPanel } from './ChatMainPanel';
import { useChatMessages } from './hooks/useChatMessages';
import { useTranslation } from 'react-i18next';

export const Chat = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { activeView } = useView();
  const isAdmin = activeView === ROLES.ADMIN;
  const { data: catalog, isLoading: isLoadingMyTeams } = useRoleTeams();
  const { scopeTeams, active: parentView, selectedTeamIds } = useChildScope();

  const teams = scopeTeams(catalog?.items ?? []);
  const needsSearch = isAdmin || Boolean(catalog?.partial);
  const hasTeams = parentView ? selectedTeamIds.size > 0 : (catalog?.total ?? 0) > 0;
  const isLoadingTeams = isLoadingMyTeams;

  const [selectedTeamId, setSelectedTeamId] = useState<string>('');
  const [pickedTeam, setPickedTeam] = useState<TeamCatalogItem | null>(null);

  useEffect(() => {
    if (teams.length === 1 && teams[0]?.id) {
      if (selectedTeamId !== teams[0].id) setSelectedTeamId(teams[0].id);
      return;
    }
    if (needsSearch) return;
    if (teams.length === 0) {
      setSelectedTeamId('');
      return;
    }
    const stillThere = teams.some((team) => team.id === selectedTeamId);
    if (stillThere) return;
    setSelectedTeamId(teams[0].id ?? '');
  }, [teams, selectedTeamId, needsSearch]);

  const selectedFromList = teams.find(t => t.id === selectedTeamId) ?? null;
  const selectedTeam = selectedFromList ?? (pickedTeam?.id === selectedTeamId ? pickedTeam : null);

  const selectTeam = (teamId: string, team?: TeamPick | null) => {
    setSelectedTeamId(teamId);
    setPickedTeam(team?.id
      ? { id: team.id, name: team.name ?? undefined, clubName: team.clubName ?? undefined }
      : null);
  };

  const { messages, sendMessage, hasMore, loadMore, isLoadingMore } = useChatMessages(selectedTeamId);

  if (isLoadingTeams) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <CircularProgress sx={{ color: '#14b8a6' }} />
      </Box>
    );
  }

  if (!hasTeams) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Typography variant="body1" color="text.secondary">
          {t('chat.noTeams')}
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <ChatLeftPanel
        teams={teams}
        selectedTeamId={selectedTeamId}
        onSelectTeam={selectTeam}
        selectedTeam={selectedTeam}
        sharedClips={[]}
        searchTeams={needsSearch}
      />
      {selectedTeam ? (
        <ChatMainPanel
          messages={messages}
          currentUserId={user?.id ?? ''}
          teamName={selectedTeam.name}
          teamClubName={selectedTeam.clubName}
          onSendMessage={sendMessage}
          hasMore={hasMore}
          onLoadMore={loadMore}
          isLoadingMore={isLoadingMore}
        />
      ) : (
        <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Typography variant="body1" color="text.secondary">
            {t('chat.selectTeam')}
          </Typography>
        </Box>
      )}
    </Box>
  );
};
