import { Avatar, Box, FormControl, MenuItem, Select, Typography } from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import type { TeamResponse } from '../../../api/generated/model';
import { useTranslation } from 'react-i18next';

interface TeamSelectorDropdownProps {
  teams: TeamResponse[];
  selectedTeamId: string;
  onSelect: (teamId: string) => void;
}

const teamInitials = (team: TeamResponse): string => {
  if (team.shortName) return team.shortName.substring(0, 3).toUpperCase();
  return (team.name ?? '?').substring(0, 2).toUpperCase();
};

export const TeamSelectorDropdown = ({ teams, selectedTeamId, onSelect }: TeamSelectorDropdownProps) => {
  const { t } = useTranslation();

  const handleChange = (e: SelectChangeEvent<string>) => onSelect(e.target.value);

  return (
    <FormControl fullWidth size="small">
      <Select
        value={selectedTeamId}
        onChange={handleChange}
        displayEmpty
        renderValue={(value) => {
          const team = teams.find(t => t.id === value);
          if (!team) return <Typography variant="body2" color="text.secondary">{t('chat.selectTeam')}</Typography>;
          return (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Avatar sx={{ bgcolor: '#0f766e', width: 24, height: 24, fontSize: '0.65rem' }}>
                {teamInitials(team)}
              </Avatar>
              <Typography variant="body2" fontWeight={600} noWrap>
                {team.name}
              </Typography>
            </Box>
          );
        }}
        sx={{
          borderRadius: 2,
          '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' },
        }}
      >
        {teams.map((team) => (
          <MenuItem key={team.id} value={team.id ?? ''}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Avatar sx={{ bgcolor: '#0f766e', width: 28, height: 28, fontSize: '0.7rem' }}>
                {teamInitials(team)}
              </Avatar>
              <Typography variant="body2">{team.name}</Typography>
            </Box>
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );
};
