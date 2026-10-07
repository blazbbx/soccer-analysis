import { useState } from "react";
import {
  Box,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  IconButton,
  Typography,
  Tooltip,
} from "@mui/material";
import { Edit as EditIcon, Delete as DeleteIcon, Add as AddIcon } from "@mui/icons-material";
import type { AxiosError } from "axios";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import {
  useCreateClub,
  useUpdateClub,
  useDeleteClub,
  getListClubsQueryKey,
} from "../../../api/generated/clubs/clubs";
import { LIST_PAGE_SIZE } from "../../../api/paging";
import { useListPage, usePagedClubs, useRetreatEmptyPage } from "../../../api/pagedLists";
import { ListPager } from "../../../components/ListPager";
import type { ClubResponse, CreateClubRequest, UpdateClubRequest } from "../../../api/generated/model";
import type { ClubUpdateBody } from "../../clubs/dialogs/EditClubDialog";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { LoadingPage } from "../../../components/LoadingPage";
import { CreateClubDialog } from "../../clubs/dialogs/CreateClubDialog";
import { EditClubDialog } from "../../clubs/dialogs/EditClubDialog";
import { ConfirmDeleteDialog } from "../dialogs/ConfirmDeleteDialog";
import { useSnackbar } from "../../../context/SnackbarContext";

export const ClubsTab = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { showError } = useSnackbar();

  const [createOpen, setCreateOpen] = useState(false);
  const [editClub, setEditClub] = useState<ClubResponse | null>(null);
  const [deleteClub, setDeleteClub] = useState<ClubResponse | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);

  const [page, setPage] = useListPage("clubs");
  const { data: clubsPage, isLoading } = usePagedClubs(page, LIST_PAGE_SIZE);
  useRetreatEmptyPage(page, clubsPage?.items.length, clubsPage?.totalPages, setPage);
  const clubs = clubsPage?.items ?? [];

  const invalidate = () => queryClient.invalidateQueries({ queryKey: getListClubsQueryKey() });

  const { mutateAsync: createClub } = useCreateClub();
  const { mutate: updateClub } = useUpdateClub({ onSuccess: invalidate });
  const { mutate: deleteClubMutate } = useDeleteClub();

  const handleCreate = async (data: CreateClubRequest) => {
    try {
      await createClub({ data });
      invalidate();
      setCreateOpen(false);
      setCreateError(null);
    } catch {
      setCreateError(t("clubs.error.generic"));
    }
  };

  const handleSave = (id: string, data: ClubUpdateBody) => {
    updateClub({
      id,
      data: {
        name: data.name,
        shortName: data.shortName,
        logoUrl: data.logoUrl,
      } as UpdateClubRequest,
    });
    setEditClub(null);
  };

  const handleDelete = () => {
    if (!deleteClub?.id) return;
    deleteClubMutate(
      { id: deleteClub.id },
      {
        onSuccess: invalidate,
        onError: (error) => {
          const status = (error as AxiosError)?.response?.status;
          showError(status === 409 ? t("clubs.error.has-teams") : t("clubs.error.generic"));
        },
      },
    );
  };

  if (isLoading) return <LoadingPage />;

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 2 }}>
        <PrimaryButton startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
          {t("clubs.create-club")}
        </PrimaryButton>
      </Box>

      {clubs.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: "center", py: 4 }}>
          {t("admin.no-clubs")}
        </Typography>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: "bold" }}>{t("clubs.name-label")}</TableCell>
              <TableCell sx={{ fontWeight: "bold" }}>{t("clubs.short-name")}</TableCell>
              <TableCell sx={{ fontWeight: "bold" }}>{t("admin.created")}</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {clubs.map((club) => (
              <TableRow key={club.id} hover>
                <TableCell>{club.name}</TableCell>
                <TableCell>{club.shortName ?? "—"}</TableCell>
                <TableCell>
                  {club.createdAt ? new Date(club.createdAt).toLocaleDateString() : "—"}
                </TableCell>
                <TableCell align="right">
                  <Tooltip title={t("common.edit")}>
                    <IconButton size="small" onClick={() => setEditClub(club)}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title={t("common.delete")}>
                    <IconButton size="small" color="error" onClick={() => setDeleteClub(club)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}>
        <ListPager page={page} totalPages={clubsPage?.totalPages ?? 0} onChange={setPage} />
      </Box>

      <CreateClubDialog
        open={createOpen}
        onClose={() => {
          setCreateOpen(false);
          setCreateError(null);
        }}
        onCreate={handleCreate}
        serverError={createError}
        onClearError={() => setCreateError(null)}
      />
      {editClub && (
        <EditClubDialog
          open={Boolean(editClub)}
          club={editClub}
          onClose={() => setEditClub(null)}
          onEdit={handleSave}
          onDelete={(id) => {
            setEditClub(null);
            deleteClubMutate(
              { id },
              {
                onSuccess: invalidate,
                onError: (error) => {
                  const status = (error as AxiosError)?.response?.status;
                  showError(status === 409 ? t("clubs.error.has-teams") : t("clubs.error.generic"));
                },
              },
            );
          }}
        />
      )}
      <ConfirmDeleteDialog
        open={Boolean(deleteClub)}
        title={t("clubs.delete-club")}
        description={t("clubs.delete-club-confirm")}
        onConfirm={handleDelete}
        onClose={() => setDeleteClub(null)}
      />
    </Box>
  );
};
