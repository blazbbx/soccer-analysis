import { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stack,
  Typography,
  Chip,
  MenuItem,
  TextField,
  Divider,
  Box,
  CircularProgress,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { PrimaryButton } from '../../../components/ui/PrimaryButton';
import { SecondaryButton } from '../../../components/ui/SecondaryButton';
import { useGetTeam, useAddPlayer, useRemovePlayer, useAddCoach, useRemoveCoach, useAddFan, getGetTeamQueryKey } from '../../../api/generated/teams/teams';
import { useGetAllUsers } from '../../../api/generated/user-controller/user-controller';
import type { UserResponse } from '../../../api/generated/model';
import { CreatePlayerProfileDialog } from '../../teams/DialogComps/CreatePlayerProfileDialog';
import { createPlayerProfile, type CreatePlayerProfileRequest } from '../../../api/playerProfiles';
import { formatPersonName } from '../../../utils/stringUtils';
import { HoldToDeleteDialog } from '../../clubs/dialogs/HoldToDeleteDialog';

const TEAM_ROLES = ['PLAYER', 'COACH', 'FAN'] as const;
type TeamRole = (typeof TEAM_ROLES)[number];

interface ManageMembersDialogProps {
  open: boolean;
  teamId: string | null;
  onClose: () => void;
}

export const ManageMembersDialog = ({ open, teamId, onClose }: ManageMembersDialogProps) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [selectedUserId, setSelectedUserId] = useState('');
  const [selectedRole, setSelectedRole] = useState<TeamRole>('PLAYER');
  const [profileDialogOpen, setProfileDialogOpen] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [removeTarget, setRemoveTarget] = useState<{ kind: 'player' | 'coach'; id: string; name: string } | null>(null);

  const { data: team, isLoading: teamLoading } = useGetTeam(teamId ?? '', {
    query: { enabled: open && !!teamId },
  });

  const { data: usersBlob } = useGetAllUsers();
  const allUsers = (usersBlob as unknown as UserResponse[]) ?? [];

  const invalidate = () => queryClient.invalidateQueries({ queryKey: getGetTeamQueryKey(teamId ?? '') });

  const { mutate: addPlayer } = useAddPlayer({ mutation: { onSuccess: invalidate } });
  const { mutate: removePlayer } = useRemovePlayer({ mutation: { onSuccess: invalidate } });
  const { mutate: addCoach } = useAddCoach({ mutation: { onSuccess: invalidate } });
  const { mutate: removeCoach } = useRemoveCoach({ mutation: { onSuccess: invalidate } });
  const { mutate: addFan } = useAddFan({ mutation: { onSuccess: invalidate } });

  const handleCreateProfile = async (data: CreatePlayerProfileRequest) => {
    if (!teamId) return;
    try {
      await createPlayerProfile(teamId, data);
      invalidate();
      setProfileDialogOpen(false);
      setProfileError(null);
    } catch {
      setProfileError(t('teams.add-player-error'));
    }
  };

  const handleAdd = () => {
    if (!teamId || !selectedUserId) return;
    if (selectedRole === 'PLAYER') addPlayer({ teamId, playerId: selectedUserId });
    else if (selectedRole === 'COACH') addCoach({ teamId, coachId: selectedUserId });
    else addFan({ teamId, fanId: selectedUserId });
    setSelectedUserId('');
  };

  const handleRemovePlayer = (playerId: string) => {
    if (!teamId) return;
    removePlayer({ teamId, playerId });
  };

  const handleRemoveCoach = (coachId: string) => {
    if (!teamId) return;
    removeCoach({ teamId, coachId });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" disableEnforceFocus={removeTarget != null}>
      <DialogTitle sx={{ fontWeight: 'bold' }}>{t('admin.manage-members')}</DialogTitle>
      <DialogContent dividers>
        {teamLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <Stack spacing={3} sx={{ mt: 1 }}>
            <Box>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                {t('teams.players')}
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {(team?.players ?? []).length === 0 && (
                  <Typography variant="body2" color="text.secondary">—</Typography>
                )}
                {(team?.players ?? []).map((p) => (
                  <Chip
                    key={p.id}
                    label={formatPersonName(p.firstName, p.lastName)}
                    onDelete={() => p.id && setRemoveTarget({ kind: 'player', id: p.id, name: formatPersonName(p.firstName, p.lastName) })}
                    size="small"
                  />
                ))}
              </Box>
            </Box>

            <Box>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                {t('teams.coach')}
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {(team?.coaches ?? []).length === 0 && (
                  <Typography variant="body2" color="text.secondary">—</Typography>
                )}
                {(team?.coaches ?? []).map((c) => (
                  <Chip
                    key={c.id}
                    label={formatPersonName(c.firstName, c.lastName)}
                    onDelete={() => c.id && setRemoveTarget({ kind: 'coach', id: c.id, name: formatPersonName(c.firstName, c.lastName) })}
                    size="small"
                  />
                ))}
              </Box>
            </Box>

            <Divider />

            <SecondaryButton
              onClick={() => {
                setProfileError(null);
                setProfileDialogOpen(true);
              }}
            >
              {t('teams.add-player')}
            </SecondaryButton>

            <Typography variant="subtitle1" fontWeight="bold">
              {t('admin.add-member')}
            </Typography>

            <TextField
              select
              label={t('admin.select-user')}
              value={selectedUserId}
              onChange={(e) => setSelectedUserId(e.target.value)}
              fullWidth
            >
              {allUsers.map((u) => (
                <MenuItem key={u.id} value={u.id ?? ''}>
                  {formatPersonName(u.firstName, u.lastName)} ({u.email})
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              label={t('admin.member-role')}
              value={selectedRole}
              onChange={(e) => setSelectedRole(e.target.value as TeamRole)}
              fullWidth
            >
              {TEAM_ROLES.map((r) => (
                <MenuItem key={r} value={r}>
                  {t(`roles.${r.toLowerCase()}`)}
                </MenuItem>
              ))}
            </TextField>

            <PrimaryButton onClick={handleAdd} disabled={!selectedUserId}>
              {t('common.add')}
            </PrimaryButton>
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose}>{t('common.cancel')}</SecondaryButton>
      </DialogActions>
      <CreatePlayerProfileDialog
        open={profileDialogOpen}
        onClose={() => {
          setProfileDialogOpen(false);
          setProfileError(null);
        }}
        onCreate={handleCreateProfile}
        serverError={profileError}
        onClearError={() => setProfileError(null)}
      />
      <HoldToDeleteDialog
        open={removeTarget != null}
        title={removeTarget?.kind === 'coach' ? t('teams.coach') : t('teams.remove-player')}
        itemName={removeTarget?.name ?? ''}
        description={t('teams.remove-player-confirm', { name: removeTarget?.name ?? '' })}
        onClose={() => setRemoveTarget(null)}
        onConfirm={() => {
          if (removeTarget?.kind === 'player') handleRemovePlayer(removeTarget.id);
          if (removeTarget?.kind === 'coach') handleRemoveCoach(removeTarget.id);
          setRemoveTarget(null);
        }}
      />
    </Dialog>
  );
};
