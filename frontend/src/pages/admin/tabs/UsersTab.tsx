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
  useCreateUser,
  useUpdateUser,
  useDeleteUser,
  getGetAllUsersQueryKey,
} from '../../../api/generated/user-controller/user-controller';
import { LIST_PAGE_SIZE } from '../../../api/paging';
import { useListPage, usePagedUsers, useRetreatEmptyPage } from '../../../api/pagedLists';
import { ListPager } from '../../../components/ListPager';
import type { UserResponse, CreateUserRequest, UpdateUserRequest } from '../../../api/generated/model';
import { PrimaryButton } from '../../../components/ui/PrimaryButton';
import { CreateUserDialog } from '../dialogs/CreateUserDialog';
import { EditUserDialog } from '../dialogs/EditUserDialog';
import { ConfirmDeleteDialog } from '../dialogs/ConfirmDeleteDialog';
import { LoadingPage } from '../../../components/LoadingPage';
import { useSnackbar } from '../../../context/SnackbarContext';
import { useAuth } from '../../../context/AuthContext';
import type { AxiosError } from 'axios';

const ROLE_COLORS: Record<string, string> = {
  ADMIN: '#ef4444',
  COACH: '#3b82f6',
  PLAYER: '#10b981',
  FAN: '#8b5cf6',
};

export const UsersTab = () => {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const { showError } = useSnackbar();
  const { user: currentUser } = useAuth();

  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserResponse | null>(null);
  const [deleteUser, setDeleteUser] = useState<UserResponse | null>(null);

  const [page, setPage] = useListPage("users");
  const { data: usersPage, isLoading } = usePagedUsers(page, LIST_PAGE_SIZE);
  useRetreatEmptyPage(page, usersPage?.items.length, usersPage?.totalPages, setPage);
  const users = usersPage?.items ?? [];
  const adminCount = usersPage?.adminCount ?? 0;

  const invalidate = () => queryClient.invalidateQueries({ queryKey: getGetAllUsersQueryKey() });

  const { mutate: createUser } = useCreateUser({ mutation: { onSuccess: invalidate } });
  const { mutate: updateUser } = useUpdateUser({ mutation: { onSuccess: invalidate } });
  const { mutate: deleteUserMutate } = useDeleteUser({
    mutation: {
      onSuccess: invalidate,
      onError: (error) => {
        const data = (error as AxiosError<{ detail?: string }>).response?.data;
        showError(data?.detail || t('admin.delete-self'));
      },
    },
  });

  const isLastAdmin = (user: UserResponse) => user.role === 'ADMIN' && adminCount <= 1;
  const isSelf = (user: UserResponse) => !!currentUser?.id && user.id === currentUser.id;
  const canDelete = (user: UserResponse) => !isLastAdmin(user) && !isSelf(user);
  const deleteTooltip = (user: UserResponse) => {
    if (isSelf(user)) return t('admin.delete-self');
    if (isLastAdmin(user)) return t('admin.delete-last-admin');
    return t('common.delete');
  };

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
              <TableCell sx={{ fontWeight: 'bold' }}>{t(i18n.language.toLowerCase().startsWith('hu') ? 'profile.last-name' : 'profile.first-name')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t(i18n.language.toLowerCase().startsWith('hu') ? 'profile.first-name' : 'profile.last-name')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.email')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('profile.role')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('admin.created')}</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id} hover>
                <TableCell>{i18n.language.toLowerCase().startsWith('hu') ? user.lastName : user.firstName}</TableCell>
                <TableCell>{i18n.language.toLowerCase().startsWith('hu') ? user.firstName : user.lastName}</TableCell>
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
                  <Tooltip title={deleteTooltip(user)}>
                    <span>
                      <IconButton
                        size="small"
                        color="error"
                        disabled={!canDelete(user)}
                        onClick={() => setDeleteUser(user)}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}>
        <ListPager page={page} totalPages={usersPage?.totalPages ?? 0} onChange={setPage} />
      </Box>

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
