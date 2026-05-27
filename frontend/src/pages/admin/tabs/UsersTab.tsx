import { useState } from 'react';
import {
  Box,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  IconButton,
  Chip,
  Typography,
  Tooltip,
} from '@mui/material';
import { Edit as EditIcon, Delete as DeleteIcon, PersonAdd as PersonAddIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  useGetAllUsers,
  useCreateUser,
  useUpdateUser,
  useDeleteUser,
  getGetAllUsersQueryKey,
} from '../../../api/generated/user-controller/user-controller';
import type { UserResponse, CreateUserRequest, UpdateUserRequest } from '../../../api/generated/model';
import { PrimaryButton } from '../../../components/ui/PrimaryButton';
import { CreateUserDialog } from '../dialogs/CreateUserDialog';
import { EditUserDialog } from '../dialogs/EditUserDialog';
import { ConfirmDeleteDialog } from '../dialogs/ConfirmDeleteDialog';
import { LoadingPage } from '../../../components/LoadingPage';

const ROLE_COLORS: Record<string, string> = {
  ADMIN: '#ef4444',
  COACH: '#3b82f6',
  PLAYER: '#10b981',
  FAN: '#8b5cf6',
};

export const UsersTab = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserResponse | null>(null);
  const [deleteUser, setDeleteUser] = useState<UserResponse | null>(null);

  const { data: usersBlob, isLoading } = useGetAllUsers();
  const users = (usersBlob as unknown as UserResponse[]) ?? [];

  const invalidate = () => queryClient.invalidateQueries({ queryKey: getGetAllUsersQueryKey() });

  const { mutate: createUser } = useCreateUser({ mutation: { onSuccess: invalidate } });
  const { mutate: updateUser } = useUpdateUser({ mutation: { onSuccess: invalidate } });
  const { mutate: deleteUserMutate } = useDeleteUser({ mutation: { onSuccess: invalidate } });

  const handleCreate = (data: CreateUserRequest) => createUser({ data });
  const handleSave = (id: string, data: UpdateUserRequest) => updateUser({ id, data });
  const handleDelete = () => {
    if (deleteUser?.id) deleteUserMutate({ id: deleteUser.id });
  };

  if (isLoading) return <LoadingPage />;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <PrimaryButton startIcon={<PersonAddIcon />} onClick={() => setCreateOpen(true)}>
          {t('admin.create-user')}
        </PrimaryButton>
      </Box>

      {users.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
          {t('admin.no-users')}
        </Typography>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('profile.first-name')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('profile.last-name')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.email')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('profile.role')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.created')}</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id} hover>
                <TableCell>{user.firstName}</TableCell>
                <TableCell>{user.lastName}</TableCell>
                <TableCell>{user.email}</TableCell>
                <TableCell>
                  <Chip
                    label={user.role ?? '—'}
                    size="small"
                    sx={{
                      bgcolor: `${ROLE_COLORS[user.role ?? ''] ?? '#6b7280'}22`,
                      color: ROLE_COLORS[user.role ?? ''] ?? '#6b7280',
                      fontWeight: 'bold',
                    }}
                  />
                </TableCell>
                <TableCell>
                  {user.createdAt ? new Date(user.createdAt).toLocaleDateString() : '—'}
                </TableCell>
                <TableCell align="right">
                  <Tooltip title={t('common.edit')}>
                    <IconButton size="small" onClick={() => setEditUser(user)}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title={t('common.delete')}>
                    <IconButton size="small" color="error" onClick={() => setDeleteUser(user)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <CreateUserDialog open={createOpen} onClose={() => setCreateOpen(false)} onCreate={handleCreate} />
      <EditUserDialog open={!!editUser} user={editUser} onClose={() => setEditUser(null)} onSave={handleSave} />
      <ConfirmDeleteDialog
        open={!!deleteUser}
        title={t('admin.delete-user')}
        description={t('admin.delete-user-confirm')}
        onConfirm={handleDelete}
        onClose={() => setDeleteUser(null)}
      />
    </Box>
  );
};
