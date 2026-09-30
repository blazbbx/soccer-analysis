import { useEffect, useState } from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';
import { useQueries } from '@tanstack/react-query';
import { getListClubTeamsQueryKey, listClubTeams, useListClubs } from '../../api/generated/clubs/clubs';
import { useGetMyTeams } from '../../api/generated/teams/teams';
import type { TeamResponse } from '../../api/generated/model';
import { useAuth } from '../../context/AuthContext';
import { useChildScope } from '../../hooks/useChildScope';
import { ROLES } from '../../types/roles';
import { ChatLeftPanel } from './ChatLeftPanel';
import { ChatMainPanel } from './ChatMainPanel';
import { useChatMessages } from './hooks/useChatMessages';
import { useTranslation } from 'react-i18next';

export const Chat = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const isClubAdmin = user?.role === ROLES.CLUB_ADMIN;
  const { data: teamsData, isLoading: isLoadingMyTeams } = useGetMyTeams({
    query: { enabled: !isClubAdmin },
  });
  const { data: clubs, isLoading: isLoadingClubs } = useListClubs({
    query: { enabled: isClubAdmin },
  });
  const clubIds = (clubs ?? []).map((club) => club.id).filter((id): id is string => Boolean(id));
  const clubTeamQueries = useQueries({
    queries: isClubAdmin
      ? clubIds.map((clubId) => ({
          queryKey: getListClubTeamsQueryKey(clubId),
          queryFn: () => listClubTeams(clubId),
        }))
      : [],
  });
  const { scopeTeams } = useChildScope();

  const clubTeams = clubTeamQueries.flatMap((query) => query.data ?? []);
  const teams = scopeTeams(isClubAdmin ? clubTeams : ((teamsData as unknown as TeamResponse[]) ?? []));
  const isLoadingTeams = isClubAdmin
    ? isLoadingClubs || clubTeamQueries.some((query) => query.isLoading)
    : isLoadingMyTeams;

  const [selectedTeamId, setSelectedTeamId] = useState<string>('');

  useEffect(() => {
    if (teams.length === 0) {
      setSelectedTeamId('');
      return;
    }
    if (!teams.some((team) => team.id === selectedTeamId)) {
      setSelectedTeamId(teams[0].id ?? '');
    }
  }, [teams, selectedTeamId]);

  const selectedTeam = teams.find(t => t.id === selectedTeamId) ?? null;

  const { messages, sendMessage, hasMore, loadMore, isLoadingMore } = useChatMessages(selectedTeamId);

  if (isLoadingTeams) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <CircularProgress sx={{ color: '#14b8a6' }} />
      </Box>
    );
  }

  if (teams.length === 0) {
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
        onSelectTeam={setSelectedTeamId}
        selectedTeam={selectedTeam}
        sharedClips={[]}
      />
      <ChatMainPanel
        messages={messages}
        currentUserId={user?.id ?? ''}
        teamName={selectedTeam?.name}
        teamClubName={selectedTeam?.clubName}
        onSendMessage={sendMessage}
        hasMore={hasMore}
        onLoadMore={loadMore}
        isLoadingMore={isLoadingMore}
      />
    </Box>
  );
};
