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
  Chip,
  Tooltip,
} from '@mui/material';
import { Edit as EditIcon, Delete as DeleteIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  useGetAllMatches,
  useUpdateMatch,
  useDeleteMatch,
  getGetAllMatchesQueryKey,
} from '../../../api/generated/match-controller/match-controller';
import type { MatchResponse, UpdateMatchRequest } from '../../../api/generated/model';
import { EditMatchDialog } from '../dialogs/EditMatchDialog';
import { ConfirmDeleteDialog } from '../dialogs/ConfirmDeleteDialog';

export const MatchesTab = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [editMatch, setEditMatch] = useState<MatchResponse | null>(null);
  const [deleteMatch, setDeleteMatch] = useState<MatchResponse | null>(null);

  const { data: matches = [], isLoading } = useGetAllMatches();

  const invalidate = () => queryClient.invalidateQueries({ queryKey: getGetAllMatchesQueryKey() });

  const { mutate: updateMatch } = useUpdateMatch({ mutation: { onSuccess: invalidate } });
  const { mutate: deleteMatchMutate } = useDeleteMatch({ mutation: { onSuccess: invalidate } });

  const handleSave = (id: string, data: UpdateMatchRequest) => updateMatch({ id, data });
  const handleDelete = () => {
    if (deleteMatch?.id) deleteMatchMutate({ id: deleteMatch.id });
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
      {matches.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
          {t('admin.no-matches')}
        </Typography>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.home-team')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.vs')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.away-team')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.score')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.match-date')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.status')}</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {matches.map((match) => (
              <TableRow key={match.id} hover>
                <TableCell>{match.homeTeamName ?? match.homeTeamId ?? '—'}</TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.secondary">
                    {t('admin.vs')}
                  </Typography>
                </TableCell>
                <TableCell>{match.awayTeamName ?? match.awayTeamId ?? '—'}</TableCell>
                <TableCell>
                  {match.homeScore ?? 0} – {match.awayScore ?? 0}
                </TableCell>
                <TableCell>
                  {match.matchDate ? new Date(match.matchDate).toLocaleDateString() : '—'}
                </TableCell>
                <TableCell>
                  <Chip
                    label={match.overallStatus ?? '—'}
                    size="small"
                    variant="outlined"
                  />
                </TableCell>
                <TableCell align="right">
                  <Tooltip title={t('common.edit')}>
                    <IconButton size="small" onClick={() => setEditMatch(match)}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title={t('common.delete')}>
                    <IconButton size="small" color="error" onClick={() => setDeleteMatch(match)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <EditMatchDialog open={!!editMatch} match={editMatch} onClose={() => setEditMatch(null)} onSave={handleSave} />
      <ConfirmDeleteDialog
        open={!!deleteMatch}
        title={t('admin.delete-match')}
        description={t('admin.delete-match-confirm')}
        onConfirm={handleDelete}
        onClose={() => setDeleteMatch(null)}
      />
    </Box>
  );
};
