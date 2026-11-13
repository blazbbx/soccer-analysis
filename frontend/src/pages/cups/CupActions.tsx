import { useState } from "react";
import { IconButton, Stack, Tooltip } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import SettingsIcon from "@mui/icons-material/Settings";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useAddTeamToCup } from "../../api/generated/cups/cups";
import { invalidateCups, useDeleteCup, useRenameCup } from "../../api/cups";
import { type TeamResponse } from "../../api/generated/model";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { AddTeamDialog } from "./dialogs/AddTeamDialog";
import { CreateCupDialog } from "./dialogs/CreateCupDialog";
import { CupConfirmDialog } from "./dialogs/CupConfirmDialog";
import { ManageCupDialog } from "../admin/dialogs/ManageCupDialog";

interface CupActionsProps {
  cup: { id: string; name?: string | null };
  myTeams: TeamResponse[];
  /** The detail page also offers rename and delete (2.6). */
  withRenameAndDelete?: boolean;
  onDeleted?: () => void;
}

/** Manager buttons of a cup. Render it only when the backend says `canManage`. */
export const CupActions = ({ cup, myTeams, withRenameAndDelete = false, onDeleted }: CupActionsProps) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [addTeamOpen, setAddTeamOpen] = useState(false);
  const [manageOpen, setManageOpen] = useState(false);
  const [renameOpen, setRenameOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const addTeamMutation = useAddTeamToCup();
  const renameMutation = useRenameCup();
  const deleteMutation = useDeleteCup();

  const handleAddTeam = async (name: string, realTeamId?: string) => {
    await addTeamMutation.mutateAsync({ cupId: cup.id, data: { name, realTeamId } });
    await invalidateCups(queryClient);
    setAddTeamOpen(false);
  };

  const handleRename = async (name: string) => {
    await renameMutation.mutateAsync({ cupId: cup.id, name });
    setRenameOpen(false);
  };

  const handleDelete = async () => {
    await deleteMutation.mutateAsync(cup.id);
    onDeleted?.();
  };

  return (
    <>
      <Stack direction="row" spacing={1} alignItems="center">
        <SecondaryButton startIcon={<AddIcon />} size="small" onClick={() => setAddTeamOpen(true)}>
          {t("cups.add-team")}
        </SecondaryButton>
        <Tooltip title={t("admin.manage-cup")}>
          <IconButton size="small" onClick={() => setManageOpen(true)}>
            <SettingsIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        {withRenameAndDelete && (
          <>
            <Tooltip title={t("cups.rename-cup")}>
              <IconButton size="small" onClick={() => setRenameOpen(true)}>
                <EditIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title={t("cups.delete-cup")}>
              <IconButton size="small" color="error" onClick={() => setDeleteOpen(true)}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </>
        )}
      </Stack>

      <AddTeamDialog open={addTeamOpen} onClose={() => setAddTeamOpen(false)} onAdd={handleAddTeam} myTeams={myTeams} />
      <ManageCupDialog open={manageOpen} onClose={() => setManageOpen(false)} cup={cup} myTeams={myTeams} />
      <CreateCupDialog
        open={renameOpen}
        onClose={() => setRenameOpen(false)}
        onCreate={handleRename}
        initialName={cup.name ?? ""}
        title={t("cups.rename-cup")}
        submitLabel={t("common.save")}
      />
      <CupConfirmDialog
        open={deleteOpen}
        title={t("cups.delete-cup")}
        description={t("cups.delete-cup-confirm", { name: cup.name ?? "" })}
        confirmLabel={t("common.delete")}
        onConfirm={handleDelete}
        onClose={() => setDeleteOpen(false)}
      />
    </>
  );
};
