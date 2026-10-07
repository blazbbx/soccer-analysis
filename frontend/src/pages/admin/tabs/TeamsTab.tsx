import { useEffect, useState } from 'react';
import {
  Box,
  Stack,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  IconButton,
  TextField,
  Typography,
  Tooltip,
} from '@mui/material';
import { Edit as EditIcon, Delete as DeleteIcon, GroupAdd as GroupAddIcon, Add as AddIcon, MailOutline as MailOutlineIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  useCreateTeam,
  useUpdateTeam,
  useDeleteTeam,
} from '../../../api/generated/teams/teams';
import { TEAM_CATALOG_KEY, useTeamCatalog, type TeamCatalogItem } from '../../../api/roleTeams';
import { useListPage, useRetreatEmptyPage } from '../../../api/pagedLists';
import { LIST_PAGE_SIZE } from '../../../api/paging';
import type { CreateTeamRequest, UpdateTeamRequest } from '../../../api/generated/model';
import { PrimaryButton } from '../../../components/ui/PrimaryButton';
import { LoadingPage } from '../../../components/LoadingPage';
import { ListPager } from '../../../components/ListPager';
import { CreateTeamDialog } from '../../teams/DialogComps/CreateTeamDialog';
import { EditTeamDialog } from '../dialogs/EditTeamDialog';
import { ManageMembersDialog } from '../dialogs/ManageMembersDialog';
import { ConfirmDeleteDialog } from '../dialogs/ConfirmDeleteDialog';
import { useCreateInvite } from '../../../api/generated/team-invitations/team-invitations';
import { InviteCreatedDialog } from '../../teams/DialogComps/InviteCreatedDialog';
import { useSnackbar } from '../../../context/SnackbarContext';
import { useView } from '../../../context/ViewContext';

export const TeamsTab = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { showError } = useSnackbar();
  const { activeView } = useView();

  const [createOpen, setCreateOpen] = useState(false);
  const [editTeam, setEditTeam] = useState<TeamCatalogItem | null>(null);
  const [manageTeamId, setManageTeamId] = useState<string | null>(null);
  const [deleteTeam, setDeleteTeam] = useState<TeamCatalogItem | null>(null);
  const [inviteUrl, setInviteUrl] = useState('');
  const [inviteOpen, setInviteOpen] = useState(false);
  const [teamQuery, setTeamQuery] = useState('');
  const [debouncedTeamQuery, setDebouncedTeamQuery] = useState('');

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedTeamQuery(teamQuery), 300);
    return () => window.clearTimeout(timer);
  }, [teamQuery]);

  const [page, setPage] = useListPage(debouncedTeamQuery.trim());
  const { data: catalog, isLoading } = useTeamCatalog(
    debouncedTeamQuery,
    true,
    true,
    page,
    LIST_PAGE_SIZE,
  );
  useRetreatEmptyPage(page, catalog?.items.length, catalog?.totalPages, setPage);
  const teams = catalog?.items ?? [];
  const queryActive = debouncedTeamQuery.trim().length > 0;
  const soleTeamVisible = (catalog?.total ?? 0) === 1 && teams.length === 1 && !queryActive;

  const invalidate = () => queryClient.invalidateQueries({ queryKey: TEAM_CATALOG_KEY });

  const { mutate: createTeam } = useCreateTeam({ mutation: { onSuccess: invalidate } });
  const { mutate: updateTeam } = useUpdateTeam({ mutation: { onSuccess: invalidate } });
  const { mutate: deleteTeamMutate } = useDeleteTeam({ mutation: { onSuccess: invalidate } });
  const inviteMutation = useCreateInvite();

  const handleCreate = (data: CreateTeamRequest) => createTeam({ data });
  const handleSave = (id: string, data: UpdateTeamRequest) => updateTeam({ id, data });
  const handleDelete = () => {
    if (deleteTeam?.id) deleteTeamMutate({ id: deleteTeam.id });
  };

  const handleCoachInvite = async (teamId: string) => {
    if (!activeView) return;
    try {
      const response = await inviteMutation.mutateAsync({
        teamId,
        params: { role: 'COACH', view: activeView.toUpperCase() },
      });
      if (!response.token) {
        showError(t('teams.invite-token-missing'));
        return;
      }
      setInviteUrl(`${window.location.origin}/registration?invitetoken=${response.token}`);
      setInviteOpen(true);
    } catch {
      showError(t('teams.error.generic'));
    }
  };

  if (isLoading) return <LoadingPage />;

  return (
    <Box>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        {!soleTeamVisible && (
          <TextField
            value={teamQuery}
            onChange={(event) => setTeamQuery(event.target.value)}
            label={t('teams.search')}
            placeholder={t('teams.catalog-search')}
            size="small"
            fullWidth
          />
        )}
        <PrimaryButton startIcon={<AddIcon />} onClick={() => setCreateOpen(true)} sx={{ flexShrink: 0 }}>
          {t('teams.create-team')}
        </PrimaryButton>
      </Stack>

      {teams.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
          {queryActive ? t('teams.search-empty') : t('admin.no-teams')}
        </Typography>
      ) : (
        <Stack spacing={2}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('teams.name-label')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('teams.short-name')}</TableCell>
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
                <TableCell>{team.playerCount ?? 0}</TableCell>
                <TableCell>{team.coachCount ?? 0}</TableCell>
                <TableCell align="right">
                  <Tooltip title={t('invite.coach')}>
                    <IconButton
                      size="small"
                      disabled={!team.id || inviteMutation.isPending}
                      onClick={() => team.id && handleCoachInvite(team.id)}
                    >
                      <MailOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
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
        <ListPager page={page} totalPages={catalog?.totalPages ?? 0} onChange={setPage} />
        </Stack>
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
      <InviteCreatedDialog
        open={inviteOpen}
        inviteUrl={inviteUrl}
        onClose={() => setInviteOpen(false)}
      />
    </Box>
  );
};
