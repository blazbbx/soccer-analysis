import { Box, Typography, Avatar, Chip, Stack, useTheme, CircularProgress, Tooltip, IconButton } from "@mui/material";
import { Card } from "../../components/ui/Card";
import { getStatusToneColor, type StatusTone } from "../../components/ui/StatusChip";
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import EditNoteIcon from '@mui/icons-material/EditNote';
import VisibilityIcon from '@mui/icons-material/Visibility';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DeleteIcon from '@mui/icons-material/Delete';
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
  onDelete?: () => void;
}

type StatusInfo = { tone: StatusTone; textKey: string };

const getStatusInfo = (status: MatchResponse): StatusInfo => {
  const encStatus = status.encodingStatus;
  const mlStatus = status.mlStatus;
  const overall = status.overallStatus;

  // Field-selection states take precedence: encodingStatus/mlStatus are still PENDING
  // at this point and would otherwise fall through to the "FELTÖLTÉS ALATT" branch.
  if (overall === 'PREPROCESSING') {
    return { tone: 'neutral', textKey: 'matches.status.preprocessing' };
  }
  if (overall === 'AWAITING_CORNERS') {
    return { tone: 'warning', textKey: 'matches.status.awaiting-corners' };
  }
  if(encStatus == uploadStatus.enodingFailed){
    return { tone: 'error', textKey: 'matches.status.encoding-failed'}
  }
  if(encStatus == uploadStatus.encodingComplete && mlStatus == uploadStatus.mlPending){
    return { tone: 'neutral', textKey: 'matches.status.analyzing'}
  }
  if(encStatus == uploadStatus.encodingPending){
    return { tone: 'neutral', textKey: 'matches.status.uploading'}
  }
  if(mlStatus == uploadStatus.mlFailed){
    return { tone: 'error', textKey: 'matches.status.analysis-failed'}
  }
  if(mlStatus == uploadStatus.mlComplete){
    return { tone: 'success', textKey: 'matches.status.ready'}
  }
  return { tone: 'neutral', textKey: 'matches.status.unknown'}
};

export const MatchCard = ({match: initialMatch, onOpen, onResumeCornerSelection, onDelete }: MatchCardProps) => {
  const theme = useTheme();
  const { t } = useTranslation();
  const { user } = useAuth();
  const canEdit = user?.role === ROLES.ADMIN || user?.role === ROLES.COACH;

  const { data: polledMatch } = useMatchWithPolling(initialMatch.id);

  const currentMatch = (polledMatch as MatchResponse) ?? initialMatch;

  const statusInfo = getStatusInfo(currentMatch);
  const statusColor = getStatusToneColor(theme, statusInfo.tone);

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
      sx={{
        width: '100%',
        transition: 'border-color 0.2s ease',
        '&:hover': {
          borderColor: isDisabled ? theme.palette.divider : theme.palette.primary.main,
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
            <CircularProgress size={16} sx={{ color: statusColor }} />
          ) : currentMatch.encodingStatus === 'COMPLETED' ? (
            <CheckCircleIcon sx={{ color: statusColor, fontSize: '1.2rem' }} />
          ) : (
            <Box component="span" sx={{ color: statusColor, fontSize: '1.2rem', lineHeight: 1 }}>
              ●
            </Box>
          )}
          <Typography variant="body2" sx={{ color: statusColor, fontWeight: 500 }}>
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
        <Stack direction="row" spacing={1} alignItems="center">
          <Stack spacing={0.5} alignItems="flex-end">
             <Stack direction="row" spacing={1} alignItems="center">
                <CalendarTodayIcon sx={{ fontSize: '1rem', color: 'text.secondary' }} />
                <Typography variant="body2" color="text.secondary">
                   {currentMatch.matchDate?.split('T')[0]}
                </Typography>
             </Stack>

             <Tooltip title={isDisabled ? t('matches.not-ready-tooltip') : ''} placement="top">
               <span>
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
               </span>
             </Tooltip>
          </Stack>

          {onDelete && (
            <Tooltip title={t('common.delete')} placement="top">
              <IconButton size="small" color="error" onClick={onDelete}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      </Stack>
    </Card>
  );
};
