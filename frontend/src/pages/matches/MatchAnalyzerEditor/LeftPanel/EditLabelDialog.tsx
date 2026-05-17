import React, { useEffect, useState } from 'react';
import {
  Box,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
  useTheme,
  type SvgIconProps,
} from '@mui/material';
import { LABEL_ITEMS, type LabelItemConfig } from '../../../../constants/labels';
import type { PlacedLabel } from '../../../../context/VideoPlayerContext';
import { PrimaryButton } from '../../../../components/ui/PrimaryButton';
import { SecondaryButton } from '../../../../components/ui/SecondaryButton';

interface EditLabelDialogProps {
  open: boolean;
  label: PlacedLabel | null;
  onSave: (config: LabelItemConfig) => void;
  onClose: () => void;
}

export const EditLabelDialog: React.FC<EditLabelDialogProps> = ({ open, label, onSave, onClose }) => {
  const theme = useTheme();
  const [selectedConfig, setSelectedConfig] = useState<LabelItemConfig | null>(null);

  useEffect(() => {
    if (label) setSelectedConfig(label.config);
  }, [label]);

  const handleSave = () => {
    if (selectedConfig) onSave(selectedConfig);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Esemény szerkesztése</DialogTitle>
      <DialogContent>
        <Stack spacing={1} sx={{ mt: 1 }}>
          {LABEL_ITEMS.map((item) => {
            const isActive = selectedConfig?.hotkey === item.hotkey;
            return (
              <Box
                key={item.hotkey}
                onClick={() => setSelectedConfig(item)}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  px: 2,
                  py: 1.25,
                  borderRadius: '6px',
                  cursor: 'pointer',
                  borderLeft: isActive ? `3px solid ${item.color}` : '3px solid transparent',
                  bgcolor: isActive
                    ? theme.palette.action.selected
                    : 'transparent',
                  transition: 'background 0.15s',
                  '&:hover': {
                    bgcolor: isActive
                      ? theme.palette.action.selected
                      : theme.palette.action.hover,
                  },
                }}
              >
                {React.cloneElement(
                  item.icon as React.ReactElement<SvgIconProps>,
                  { sx: { color: item.color, fontSize: '22px' } },
                )}
                <Typography
                  sx={{
                    color: theme.palette.text.primary,
                    fontSize: '14px',
                    fontWeight: isActive ? 600 : 400,
                  }}
                >
                  {item.event}
                </Typography>
              </Box>
            );
          })}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <SecondaryButton onClick={onClose}>Mégse</SecondaryButton>
        <PrimaryButton onClick={handleSave} disabled={selectedConfig === null}>
          Mentés
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
