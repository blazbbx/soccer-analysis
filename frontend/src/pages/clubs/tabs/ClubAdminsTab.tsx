import { useState } from "react";
import {
  Alert,
  Box,
  CircularProgress,
  List,
  ListItem,
  ListItemText,
  Pagination,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import type { AxiosError } from "axios";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { listClubAdmins, removeClubAdmin } from "../../../api/clubAdmins";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { useAuth } from "../../../context/AuthContext";
import { formatPersonName } from "../../../utils/stringUtils";
import { matchesQuery, pageOf } from "../paging";
import { HoldToDeleteDialog } from "../dialogs/HoldToDeleteDialog";

const adminsKey = (clubId: string) => ["clubs", clubId, "admins"] as const;

export const ClubAdminsTab = ({
  clubId,
  canManage,
  query,
  page,
  onQueryChange,
  onPageChange,
  onInvite,
  onLeftClub,
}: {
  clubId: string;
  canManage: boolean;
  query: string;
  page: number;
  onQueryChange: (value: string) => void;
  onPageChange: (page: number) => void;
  onInvite: () => void;
  onLeftClub: () => void;
}) => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [removeTarget, setRemoveTarget] = useState<{ userId: string; name: string } | null>(null);
  const { data: admins = [], isLoading, isError } = useQuery({
    queryKey: adminsKey(clubId),
    queryFn: () => listClubAdmins(clubId),
    enabled: canManage && Boolean(clubId),
  });

  const filtered = admins.filter((admin) =>
    matchesQuery(query, [formatPersonName(admin.firstName, admin.lastName), admin.email]),
  );
  const paging = pageOf(filtered, page);
  const lastAdmin = admins.length <= 1;

  const handleRemove = async (userId: string) => {
    try {
      await removeClubAdmin(clubId, userId);
      setError(null);
      if (userId === user?.id) {
        onLeftClub();
        return;
      }
      await queryClient.invalidateQueries({ queryKey: adminsKey(clubId) });
    } catch (err) {
      const status = (err as AxiosError)?.response?.status;
      setError(status === 409 ? t("clubs.error.last-admin") : t("clubs.error.generic"));
    }
  };

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems={{ sm: "center" }}>
        <TextField
          fullWidth
          size="small"
          label={t("clubs.search")}
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
        />
        {canManage && (
          <PrimaryButton onClick={onInvite} sx={{ flexShrink: 0 }}>
            {t("clubs.invite-admin")}
          </PrimaryButton>
        )}
      </Stack>
      {error && <Alert severity="error">{error}</Alert>}
      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
          <CircularProgress size={28} />
        </Box>
      ) : isError ? (
        <Alert severity="error">{t("clubs.error.generic")}</Alert>
      ) : admins.length === 0 ? (
        <Typography variant="body2" sx={{ color: "text.secondary" }}>
          {t("clubs.no-admins")}
        </Typography>
      ) : filtered.length === 0 ? (
        <Typography variant="body2" sx={{ color: "text.secondary" }}>
          {t("clubs.search-empty-admins")}
        </Typography>
      ) : (
        <>
          <List disablePadding>
            {paging.visible.map((admin) => (
              <ListItem key={admin.userId} disableGutters sx={{ py: 1 }}>
                <Stack
                  direction={{ xs: "column", sm: "row" }}
                  spacing={1}
                  alignItems={{ xs: "stretch", sm: "center" }}
                  justifyContent="space-between"
                  sx={{ width: "100%" }}
                >
                  <ListItemText
                    primary={formatPersonName(admin.firstName, admin.lastName)}
                    secondary={admin.email}
                    sx={{ my: 0 }}
                  />
                  {canManage && (
                    <SecondaryButton
                      disabled={lastAdmin || !admin.userId}
                      onClick={() => admin.userId && setRemoveTarget({
                        userId: admin.userId,
                        name: formatPersonName(admin.firstName, admin.lastName),
                      })}
                    >
                      {t("clubs.remove-admin")}
                    </SecondaryButton>
                  )}
                </Stack>
              </ListItem>
            ))}
          </List>
          {paging.totalPages > 1 && (
            <Pagination
              count={paging.totalPages}
              page={paging.safePage}
              onChange={(_event, next) => onPageChange(next)}
              siblingCount={2}
              boundaryCount={0}
              sx={{ alignSelf: "center" }}
            />
          )}
        </>
      )}
      <HoldToDeleteDialog
        open={removeTarget != null}
        title={t("clubs.remove-admin")}
        itemName={removeTarget?.name ?? ""}
        description={t("clubs.remove-admin-confirm")}
        onClose={() => setRemoveTarget(null)}
        onConfirm={() => {
          const userId = removeTarget?.userId;
          setRemoveTarget(null);
          if (userId) void handleRemove(userId);
        }}
      />
    </Stack>
  );
};
