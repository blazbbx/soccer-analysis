import { useState } from 'react';
import { Box, Collapse, Divider, IconButton, Stack, Typography } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import type { ClipResponse, TeamResponse } from '../../api/generated/model';
import { useGetTeam } from '../../api/generated/teams/teams';
import { TeamAutocomplete, type TeamPick } from '../../components/TeamAutocomplete';
import { TeamSelectorDropdown } from './TeamSelectorDropdown';
import { MemberCard } from './MemberCard';
import { SharedClipCard } from './SharedClipCard';
import { useTranslation } from 'react-i18next';

interface ChatLeftPanelProps {
  teams: TeamResponse[];
  selectedTeamId: string;
  onSelectTeam: (teamId: string, team?: TeamPick | null) => void;
  selectedTeam: TeamPick | null;
  sharedClips: ClipResponse[];
  searchTeams?: boolean;
}

interface CollapsibleSectionProps {
  title: string;
  count?: number;
  children: React.ReactNode;
}

const CollapsibleSection = ({ title, count, children }: CollapsibleSectionProps) => {
  const [open, setOpen] = useState(true);

  return (
    <Box>
      <Box
        onClick={() => setOpen(prev => !prev)}
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          px: 2,
          py: 1,
          cursor: 'pointer',
          userSelect: 'none',
          '&:hover': { bgcolor: 'action.hover' },
          borderRadius: 1,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
            {title}
          </Typography>
          {count !== undefined && (
            <Typography variant="caption" color="text.secondary">
              ({count})
            </Typography>
          )}
        </Box>
        <IconButton size="small" sx={{ color: 'text.secondary', p: 0 }}>
          <ExpandMoreIcon
            fontSize="small"
            sx={{
              transform: open ? 'rotate(180deg)' : 'rotate(0deg)',
              transition: 'transform 0.2s ease',
            }}
          />
        </IconButton>
      </Box>
      <Collapse in={open}>
        {children}
      </Collapse>
    </Box>
  );
};

export const ChatLeftPanel = ({
  teams,
  selectedTeamId,
  onSelectTeam,
  selectedTeam,
  sharedClips,
  searchTeams = false,
}: ChatLeftPanelProps) => {
  const { t } = useTranslation();
  const rosterId = selectedTeamId || selectedTeam?.id || '';
  const { data: roster } = useGetTeam(rosterId, {
    query: { enabled: rosterId.length > 0 },
  });
  const coaches = roster?.coaches ?? [];
  const players = roster?.players ?? [];
  const totalMembers = coaches.length + players.length;

  return (
    <Box
      sx={{
        width: 300,
        flexShrink: 0,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        borderRight: 1,
        borderColor: 'divider',
        bgcolor: 'background.paper',
        overflow: 'hidden',
      }}
    >
      <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
        {teams.length === 1 ? (
          <TeamSelectorDropdown
            teams={teams}
            selectedTeamId={selectedTeamId}
            onSelect={onSelectTeam}
          />
        ) : searchTeams ? (
          <TeamAutocomplete
            teams={teams}
            valueId={selectedTeamId || null}
            onChange={(teamId, team) => onSelectTeam(teamId ?? '', team)}
            selectedLabel={selectedTeam?.name ?? undefined}
            label={t('chat.selectTeam')}
            size="small"
          />
        ) : (
          <TeamSelectorDropdown
            teams={teams}
            selectedTeamId={selectedTeamId}
            onSelect={onSelectTeam}
          />
        )}
      </Box>

      <Box sx={{ flexGrow: 1, overflowY: 'auto', py: 1 }}>
        <Stack spacing={0}>
          <CollapsibleSection title={t('chat.members')} count={totalMembers}>
            {coaches.map(coach => (
              <MemberCard key={coach.id} member={coach} role="coach" />
            ))}
            {players.length > 0 && coaches.length > 0 && (
              <Divider sx={{ mx: 2, my: 0.5 }} />
            )}
            {players.map(player => (
              <MemberCard key={player.id} member={player} role="player" />
            ))}
            {totalMembers === 0 && (
              <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 1, display: 'block' }}>
                —
              </Typography>
            )}
          </CollapsibleSection>

          <Divider sx={{ my: 1 }} />

          <CollapsibleSection title={t('chat.sharedClips')} count={sharedClips.length}>
            {sharedClips.map(clip => (
              <SharedClipCard key={clip.id} clip={clip} />
            ))}
            {sharedClips.length === 0 && (
              <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 1, display: 'block' }}>
                —
              </Typography>
            )}
          </CollapsibleSection>
        </Stack>
      </Box>
    </Box>
  );
};
