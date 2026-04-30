import { useEffect, useState } from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';
import { useGetMyTeams } from '../api/generated/teams/teams';
import type { ClipResponse, TeamResponse } from '../api/generated/model';
import type { ChatMessage } from '../types/chat';
import { chatService } from '../services/chatService';
import { useAuth } from '../context/AuthContext';
import { ChatLeftPanel } from '../components/common/ChatPageComps/ChatLeftPanel';
import { ChatMainPanel } from '../components/common/ChatPageComps/ChatMainPanel';
import { useTranslation } from 'react-i18next';
import { ROLES } from '../types/roles';

export const Chat = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { data: teamsData, isLoading: isLoadingTeams } = useGetMyTeams();

  const teams = (teamsData as unknown as TeamResponse[]) ?? [];

  const [selectedTeamId, setSelectedTeamId] = useState<string>('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sharedClips, setSharedClips] = useState<ClipResponse[]>([]);

  useEffect(() => {
    if (teams.length > 0 && !selectedTeamId) {
      setSelectedTeamId(teams[0].id ?? '');
    }
  }, [teams, selectedTeamId]);

  useEffect(() => {
    if (!selectedTeamId) return;

    chatService.getMessages(selectedTeamId).then(setMessages);
    chatService.getSharedClips(selectedTeamId).then(setSharedClips);
  }, [selectedTeamId]);

  const selectedTeam = teams.find(t => t.id === selectedTeamId) ?? null;

  const handleSendMessage = async (content: string) => {
    if (!user || !selectedTeamId) return;

    const [firstName, ...rest] = (user.name ?? '').split(' ');
    const lastName = rest.join(' ');
    const role = user.role === ROLES.ADMIN ? 'admin' : user.role === ROLES.COACH ? 'coach' : 'player';

    const newMsg = await chatService.sendMessage(
      selectedTeamId,
      { content },
      user.id,
      firstName,
      lastName,
      role,
    );

    setMessages(prev => [...prev, newMsg]);
  };

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
        sharedClips={sharedClips}
      />
      <ChatMainPanel
        messages={messages}
        currentUserId={user?.id ?? ''}
        teamName={selectedTeam?.name}
        onSendMessage={handleSendMessage}
      />
    </Box>
  );
};
