import { useState } from 'react';
import {
  Box,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  IconButton,
  Typography,
  CircularProgress,
  Tooltip,
} from '@mui/material';
import { Edit as EditIcon, Delete as DeleteIcon, GroupAdd as GroupAddIcon, Add as AddIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  useGetMyTeams,
  useCreateTeam,
  useUpdateTeam,
  useDeleteTeam,
  getGetMyTeamsQueryKey,
} from '../../../api/generated/teams/teams';
import type { TeamResponse, CreateTeamRequest, UpdateTeamRequest } from '../../../api/generated/model';
import { PrimaryButton } from '../../../components/ui/PrimaryButton';
import { CreateTeamDialog } from '../../teams/DialogComps/CreateTeamDialog';
import { EditTeamDialog } from '../dialogs/EditTeamDialog';
import { ManageMembersDialog } from '../dialogs/ManageMembersDialog';
import { ConfirmDeleteDialog } from '../dialogs/ConfirmDeleteDialog';

export const TeamsTab = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [editTeam, setEditTeam] = useState<TeamResponse | null>(null);
  const [manageTeamId, setManageTeamId] = useState<string | null>(null);
  const [deleteTeam, setDeleteTeam] = useState<TeamResponse | null>(null);

  const { data: teams = [], isLoading } = useGetMyTeams();

  const invalidate = () => queryClient.invalidateQueries({ queryKey: getGetMyTeamsQueryKey() });

  const { mutate: createTeam } = useCreateTeam({ mutation: { onSuccess: invalidate } });
  const { mutate: updateTeam } = useUpdateTeam({ mutation: { onSuccess: invalidate } });
  const { mutate: deleteTeamMutate } = useDeleteTeam({ mutation: { onSuccess: invalidate } });

  const handleCreate = (data: CreateTeamRequest) => createTeam({ data });
  const handleSave = (id: string, data: UpdateTeamRequest) => updateTeam({ id, data });
  const handleDelete = () => {
    if (deleteTeam?.id) deleteTeamMutate({ id: deleteTeam.id });
  };

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <PrimaryButton startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
          {t('teams.create-team')}
        </PrimaryButton>
      </Box>

      {teams.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
          {t('admin.no-teams')}
        </Typography>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('teams.name-label')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Short Name</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('teams.players')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('teams.coach')}</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {teams.map((team) => (
              <TableRow key={team.id} hover>
                <TableCell>{team.name}</TableCell>
                <TableCell>{team.shortName ?? '—'}</TableCell>
                <TableCell>{team.players?.length ?? 0}</TableCell>
                <TableCell>{team.coaches?.length ?? 0}</TableCell>
                <TableCell align="right">
                  <Tooltip title={t('admin.manage-members')}>
                    <IconButton size="small" onClick={() => setManageTeamId(team.id ?? null)}>
                      <GroupAddIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title={t('common.edit')}>
                    <IconButton size="small" onClick={() => setEditTeam(team)}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title={t('common.delete')}>
                    <IconButton size="small" color="error" onClick={() => setDeleteTeam(team)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <CreateTeamDialog open={createOpen} onClose={() => setCreateOpen(false)} onCreate={handleCreate} />
      <EditTeamDialog open={!!editTeam} team={editTeam} onClose={() => setEditTeam(null)} onSave={handleSave} />
      <ManageMembersDialog open={!!manageTeamId} teamId={manageTeamId} onClose={() => setManageTeamId(null)} />
      <ConfirmDeleteDialog
        open={!!deleteTeam}
        title={t('admin.delete-team')}
        description={t('admin.delete-team-confirm')}
        onConfirm={handleDelete}
        onClose={() => setDeleteTeam(null)}
      />
    </Box>
  );
};
