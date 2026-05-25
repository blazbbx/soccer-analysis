import { Box, Typography, Avatar, Chip, Stack, Card, useTheme, CircularProgress } from "@mui/material";
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import EditNoteIcon from '@mui/icons-material/EditNote';
import VisibilityIcon from '@mui/icons-material/Visibility';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CropFreeIcon from '@mui/icons-material/CropFree';
import { useTranslation } from 'react-i18next';
import { type MatchResponse } from "../../api/generated/model/matchResponse";
import {APP_COLORS , STAT_COLORS} from  "../../constants/colors"
import {getInitials} from "../../utils/stringUtils"
import { useMatchWithPolling } from "./hooks/useMatchWithPolling";
import { uploadStatus } from "../../constants/uploadStatus";
import { useAuth } from "../../context/AuthContext";
import { ROLES } from "../../types/roles";

interface MatchCardProps {
  match: MatchResponse;
  onOpen: (matchId: string | undefined) => void;
  onResumeCornerSelection?: (match: MatchResponse) => void;
}

const getStatusInfo = (status: MatchResponse): { color: string; textKey: string } => {
  const encStatus = status.encodingStatus;
  const mlStatus = status.mlStatus;
  const overall = status.overallStatus;

  // Field-selection states take precedence: encodingStatus/mlStatus are still PENDING
  // at this point and would otherwise fall through to the "FELTÖLTÉS ALATT" branch.
  if (overall === 'PREPROCESSING') {
    return { color: '#a1a1aa', textKey: 'ELŐFELDOLGOZÁS' };
  }
  if (overall === 'AWAITING_CORNERS') {
    return { color: '#f59e0b', textKey: 'PÁLYAVÁLASZTÁS' };
  }
  if(encStatus == uploadStatus.enodingFailed){
    return {color: '#ef4444', textKey: 'matches.status.encoding-failed'}
  }
  if(encStatus == uploadStatus.encodingComplete && mlStatus == uploadStatus.mlPending){
    return { color: '#a1a1aa', textKey: 'matches.status.analyzing'}
  }
  if(encStatus == uploadStatus.encodingPending){
    return { color: '#a1a1aa', textKey: 'matches.status.uploading'}
  }
  if(mlStatus == uploadStatus.mlFailed){
    return { color: '#ef4444', textKey: 'matches.status.analysis-failed'}
  }
  if(mlStatus == uploadStatus.mlComplete){
    return { color: '#22c55e', textKey: 'matches.status.ready'}
  }
  return { color: '#a1a1aa', textKey: 'matches.status.unknown'}
};

export const MatchCard = ({match: initialMatch, onOpen, onResumeCornerSelection }: MatchCardProps) => {
  const theme = useTheme();
  const { t } = useTranslation();
  const { user } = useAuth();
  const canEdit = user?.role === ROLES.ADMIN || user?.role === ROLES.COACH;

  const { data: polledMatch } = useMatchWithPolling(initialMatch.id);

  const currentMatch = (polledMatch as MatchResponse) ?? initialMatch;

  const statusInfo = getStatusInfo(currentMatch);

  // The chip has three modes:
  //   1. "Field selection" + clickable when AWAITING_CORNERS (resume the flow)
  //   2. "Field selection" + disabled while PREPROCESSING (nothing to act on yet,
  //      but labeled so coaches know what's coming)
  //   3. The normal "Editor"/"Megtekintés" once ML has completed
  const overall = currentMatch.overallStatus;
  const isFieldSelectionState = overall === 'AWAITING_CORNERS' || overall === 'PREPROCESSING';
  const isFieldSelectionReady = overall === 'AWAITING_CORNERS';

  const isDisabled = isFieldSelectionState
    ? !isFieldSelectionReady || !onResumeCornerSelection
    : currentMatch.mlStatus !== uploadStatus.mlComplete;
 
  const handleChipClick = () => {
    if (isDisabled) return;
    if (isFieldSelectionReady && onResumeCornerSelection) {
      onResumeCornerSelection(currentMatch);
      return;
    }
    onOpen(initialMatch.id);
  };

  const homeTeamName = currentMatch.homeTeamName;
  const awayTeamName = currentMatch.awayTeamName;

  return (
    <Card
      elevation={0}
      sx={{
        bgcolor: 'background.paper',
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: theme.shape.borderRadius,
        p: 2,
        width: '100%',
        transition: 'border-color 0.2s ease',
        '&:hover': {
          borderColor: isDisabled ? theme.palette.divider : APP_COLORS.sideBarButton.active,
        }
      }}
    >
      <Stack
        direction="row"
        spacing={2}
        alignItems="center"
        justifyContent="space-between"
        width="100%"
      >
        {/* Status */}
        <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: '120px' }}>
          {currentMatch.encodingStatus === 'ENCODING' ? (
            <CircularProgress size={16} sx={{ color: statusInfo.color }} />
          ) : currentMatch.encodingStatus === 'COMPLETED' ? (
            <CheckCircleIcon sx={{ color: statusInfo.color, fontSize: '1.2rem' }} />
          ) : (
            <Box component="span" sx={{ color: statusInfo.color, fontSize: '1.2rem', lineHeight: 1 }}>
              ●
            </Box>
          )}
          <Typography variant="body2" sx={{ color: statusInfo.color, fontWeight: 500 }}>
            {t(statusInfo.textKey)}
          </Typography>
        </Stack>

        {/* Teams */}
        <Stack direction="row" spacing={2} alignItems="center" flexGrow={1} justifyContent="center">
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Typography variant="h6" color="text.primary">
              {homeTeamName}
            </Typography>
            <Avatar
              sx={{
                bgcolor: STAT_COLORS.blueAccent,
                color: '#fff',
                width: 32,
                height: 32,
                fontSize: '1rem',
                fontWeight: 500
              }}
            >
              {getInitials(homeTeamName)}
            </Avatar>
          </Stack>

          <Typography variant="body2" color="text.secondary" fontWeight={500}>
            {t('common.vs')}
          </Typography>

          <Stack direction="row" spacing={1.5} alignItems="center">
            <Avatar
              sx={{
                bgcolor: STAT_COLORS.losses,
                color: '#fff',
                width: 32,
                height: 32,
                fontSize: '1rem',
                fontWeight: 500               
              }}
            >
              {getInitials(awayTeamName)}
            </Avatar>
            <Typography variant="h6" color="text.primary">
              {awayTeamName}
            </Typography>
          </Stack>
        </Stack>

        {/* Date and button */}
        <Stack direction="row" spacing={3} alignItems="center">
          <Stack spacing={0.5} alignItems="flex-end">
             <Stack direction="row" spacing={1} alignItems="center">
                <CalendarTodayIcon sx={{ fontSize: '1rem', color: 'text.secondary' }} />
                <Typography variant="body2" color="text.secondary">
                   {currentMatch.matchDate?.split('T')[0]}
                </Typography>
             </Stack>

             <Chip
                label={canEdit ? t('matches.editor') : t('matches.viewer')}
                icon={canEdit
                  ? <EditNoteIcon sx={{ color: 'inherit !important' }} />
                  : <VisibilityIcon sx={{ color: 'inherit !important' }} />
                }
                size="small"
                onClick={isDisabled ? undefined : handleChipClick}
                sx={{
                  bgcolor: isDisabled ? theme.palette.secondary.main : APP_COLORS.sideBarButton.activeBackGround,
                  color: isDisabled ? theme.palette.text.secondary : APP_COLORS.sideBarButton.active,
                  fontWeight: 500,
                  cursor: isDisabled ? 'default' : 'pointer',
                  border: 'none',
                  transition: 'background-color 0.2s ease',
                  '&:hover': {
                    bgcolor: isDisabled
                      ? theme.palette.secondary.main
                      : APP_COLORS.sideBarButton.activeHoverBackGround,
                  },
                  '& .MuiChip-icon': {
                    color: isDisabled ? theme.palette.text.secondary : APP_COLORS.sideBarButton.active,
                  },
                }}
             />
          </Stack>

        </Stack>
      </Stack>
    </Card>
  );
};
