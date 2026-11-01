import { useRef, useState, type ReactNode } from 'react';
import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  Paper,
  Snackbar,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import LockResetOutlinedIcon from '@mui/icons-material/LockResetOutlined';
import { useQuery } from '@tanstack/react-query';
import { useAuth as useKeycloakAuth } from 'react-oidc-context';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../context/AuthContext';
import { useView } from '../../context/ViewContext';
import { updateUserProfile } from '../../services/userService';
import { getMeViews, membershipCountForHat, MY_VIEWS_QUERY_KEY } from '../../api/me';
import { getMyMedicalStatus, myMedicalQueryKey } from '../../api/medical';
import { formatPersonName, getInitials } from '../../utils/stringUtils';
import { ROLES } from '../../types/roles';
import { MedicalSection } from '../../components/medical/MedicalSection';
import { PrimaryButton } from '../../components/ui/PrimaryButton';
import { eqCard, twoColumnGridSx, useEqualColumns } from '../../hooks/useEqualColumns';

interface ProfileFormState {
  firstName: string;
  lastName: string;
}

type SaveStatus = 'idle' | 'success' | 'error';

const GREEN = '#10b981';

const cardSx = {
  p: 2.5,
  borderRadius: 3,
  border: '1px solid',
  borderColor: 'divider',
  bgcolor: 'background.paper',
  boxSizing: 'border-box',
} as const;

const splitName = (fullName: string): ProfileFormState => {
  const spaceIndex = fullName.indexOf(' ');
  if (spaceIndex === -1) return { firstName: fullName, lastName: '' };
  return {
    firstName: fullName.slice(0, spaceIndex),
    lastName: fullName.slice(spaceIndex + 1),
  };
};

const Card = ({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) => (
  <Paper elevation={0} {...eqCard} sx={cardSx}>
    <Typography variant="body1" fontWeight={700}>{title}</Typography>
    {subtitle && (
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>{subtitle}</Typography>
    )}
    <Box sx={{ mt: 2 }}>{children}</Box>
  </Paper>
);

export const UserPage = () => {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();
  const oidc = useKeycloakAuth();
  const { hats, activeView } = useView();
  const leftRef = useRef<HTMLDivElement>(null);
  const rightRef = useRef<HTMLDivElement>(null);

  const initial: ProfileFormState = user && (user.firstName || user.lastName)
    ? { firstName: user.firstName ?? '', lastName: user.lastName ?? '' }
    : splitName(user?.name ?? '');
  const [saved, setSaved] = useState<ProfileFormState>(initial);
  const [formState, setFormState] = useState<ProfileFormState>(initial);
  const [isSaving, setIsSaving] = useState(false);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle');

  const isPlayerView = activeView === ROLES.PLAYER;
  // Same query (and cache) as the medical section; it only tells whether the section shows at all.
  const { data: medical } = useQuery({
    queryKey: myMedicalQueryKey,
    queryFn: getMyMedicalStatus,
    enabled: isPlayerView,
    retry: false,
  });
  // The child always sees its own state (read only without the upload right); an adult player manages it fully.
  // In the 30-day grace the adult player still reaches the file left over.
  const showMedical = isPlayerView && Boolean(medical?.required || medical?.certificateId);
  const { data: views } = useQuery({
    queryKey: MY_VIEWS_QUERY_KEY,
    queryFn: getMeViews,
    enabled: Boolean(user),
  });

  useEqualColumns(leftRef, rightRef);

  const trimmed = { firstName: formState.firstName.trim(), lastName: formState.lastName.trim() };
  const dirty = trimmed.firstName !== saved.firstName.trim() || trimmed.lastName !== saved.lastName.trim();
  const valid = trimmed.firstName.length > 0 && trimmed.lastName.length > 0;

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await updateUserProfile(trimmed);
      setSaved(trimmed);
      setFormState(trimmed);
      setSaveStatus('success');
    } catch {
      setSaveStatus('error');
    } finally {
      setIsSaving(false);
    }
  };

  // TODO(backend): there is no password endpoint (passwords live in Keycloak). Until one exists the
  // card sends the user to Keycloak's own "update password" page and back here, instead of the inline
  // current / new / confirm form of the design.
  const changePassword = () => {
    localStorage.setItem('pendingRedirectPath', '/user');
    void oidc.signinRedirect({ extraQueryParams: { kc_action: 'UPDATE_PASSWORD' } });
  };

  const displayName = formatPersonName(saved.firstName, saved.lastName) || user?.name || '';

  const personal = (
    <Card key="personal" title={t('profile.personal.title')}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1.75} alignItems="center">
          <Avatar sx={{ width: 52, height: 52, bgcolor: '#0f766e', fontSize: '1.2rem', fontWeight: 700 }}>
            {getInitials(displayName)}
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body1" fontWeight={700} noWrap>{displayName}</Typography>
            <Typography variant="body2" color="text.secondary" noWrap>{user?.email}</Typography>
          </Box>
        </Stack>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.5 }}>
          <TextField
            size="small"
            label={t('profile.last-name')}
            value={formState.lastName}
            onChange={(e) => setFormState((prev) => ({ ...prev, lastName: e.target.value }))}
            autoComplete="family-name"
            required
          />
          <TextField
            size="small"
            label={t('profile.first-name')}
            value={formState.firstName}
            onChange={(e) => setFormState((prev) => ({ ...prev, firstName: e.target.value }))}
            autoComplete="given-name"
            required
          />
        </Box>
        <TextField
          size="small"
          label={t('profile.email')}
          value={user?.email ?? ''}
          helperText={t('profile.email-hint')}
          slotProps={{ input: { readOnly: true } }}
        />
        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
          <Typography variant="caption" color="text.secondary">
            {!dirty ? t('profile.no-changes') : !valid ? t('profile.name-required') : ''}
          </Typography>
          <PrimaryButton
            onClick={() => void handleSave()}
            disabled={isSaving || !dirty || !valid}
            startIcon={isSaving ? <CircularProgress size={14} color="inherit" /> : undefined}
            sx={{ py: 0.75 }}
          >
            {t('profile.save')}
          </PrimaryButton>
        </Stack>
      </Stack>
    </Card>
  );

  const password = (
    <Card key="password" title={t('profile.password.title')} subtitle={t('profile.password.subtitle')}>
      <Stack direction="row" spacing={1.5} alignItems="center" justifyContent="space-between" flexWrap="wrap" useFlexGap>
        <Typography variant="body2" color="text.secondary" sx={{ flex: '1 1 220px' }}>
          {t('profile.password.hint')}
        </Typography>
        <Button
          variant="outlined"
          color="inherit"
          startIcon={<LockResetOutlinedIcon />}
          onClick={changePassword}
          sx={{ textTransform: 'none', fontWeight: 700, borderRadius: 2, borderColor: 'divider', flexShrink: 0 }}
        >
          {t('profile.password.action')}
        </Button>
      </Stack>
    </Card>
  );

  const language = (
    <Card key="language" title={t('profile.language.title')} subtitle={showMedical ? undefined : t('profile.language.subtitle')}>
      <ToggleButtonGroup
        exclusive
        size="small"
        value={i18n.language.startsWith('en') ? 'en' : 'hu'}
        onChange={(_, value: string | null) => { if (value) void i18n.changeLanguage(value); }}
        aria-label={t('profile.language.title')}
        sx={{ '& .MuiToggleButton-root': { textTransform: 'none', fontWeight: 700, px: 2.5 } }}
      >
        <ToggleButton value="hu">Magyar</ToggleButton>
        <ToggleButton value="en">English</ToggleButton>
      </ToggleButtonGroup>
    </Card>
  );

  const roles = (
    <Card key="roles" title={t('profile.roles.title')} subtitle={t('profile.roles.hint')}>
      <Stack spacing={1}>
        {hats.map((hat) => {
          const count = membershipCountForHat(hat, views);
          const active = hat === activeView;
          return (
            <Stack key={hat} direction="row" alignItems="center" spacing={1.5}
              sx={{ px: 1.5, py: 1, borderRadius: 2, border: '1px solid', borderColor: active ? `${GREEN}66` : 'divider', bgcolor: active ? `${GREEN}0d` : undefined }}>
              <Typography variant="body2" fontWeight={700} sx={{ flex: 1 }}>{t(`roles.${hat}`)}</Typography>
              {count !== undefined && (
                <Typography variant="caption" color="text.secondary">{t(`views.membership.${hat}`, { count })}</Typography>
              )}
              {active && <Chip size="small" label={t('profile.roles.active')} sx={{ height: 20, fontWeight: 700, color: GREEN, bgcolor: `${GREEN}1a` }} />}
            </Stack>
          );
        })}
      </Stack>
    </Card>
  );

  return (
    <Box sx={{ px: { xs: 2, md: 4 }, py: { xs: 3, md: 3 }, maxWidth: 1360, mx: 'auto' }}>
      <Box sx={{ mb: 2.5 }}>
        <Typography variant="h5" fontWeight="bold">{t('profile.title')}</Typography>
        <Typography variant="body2" color="text.secondary">
          {showMedical ? t('profile.subtitle-medical') : t('profile.subtitle')}
        </Typography>
      </Box>

      <Box sx={twoColumnGridSx}>
        <Stack ref={leftRef} spacing={2.5} sx={{ minWidth: 0 }}>
          {showMedical ? [personal, language, password] : [personal, language]}
        </Stack>
        <Stack ref={rightRef} spacing={2.5} sx={{ minWidth: 0 }}>
          {showMedical
            ? <MedicalSection viewer="player" eqCard />
            : [password, roles]}
        </Stack>
      </Box>

      <Snackbar
        open={saveStatus !== 'idle'}
        autoHideDuration={4000}
        onClose={() => setSaveStatus('idle')}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={saveStatus === 'success' ? 'success' : 'error'}
          onClose={() => setSaveStatus('idle')}
        >
          {saveStatus === 'success'
            ? t('profile.save-success')
            : t('profile.save-error')}
        </Alert>
      </Snackbar>
    </Box>
  );
};
