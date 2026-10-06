import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Box,
  CircularProgress,
  Container,
  FormControlLabel,
  MenuItem,
  Paper,
  Radio,
  RadioGroup,
  TextField,
  Typography,
} from "@mui/material";
import type { AxiosError } from "axios";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { acceptInviteWithDetails, childClubAndTeamLabel, getInviteDetails, listMyChildren } from "../../api/children";
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { useAuth as useKeycloakAuth } from "react-oidc-context";
import { useAuth } from "../../context/AuthContext";
import { MY_CHILDREN_QUERY_KEY } from "../../context/ChildrenContext";
import { MY_VIEWS_QUERY_KEY } from "../../api/me";
import { formatPersonName } from "../../utils/stringUtils";

export const InviteAcceptPage = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const oidc = useKeycloakAuth();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const [kind, setKind] = useState<string | null>(null);
  const [childName, setChildName] = useState<string | null>(null);
  const [inviteLoaded, setInviteLoaded] = useState(false);
  const [childForm, setChildForm] = useState({ firstName: "", lastName: "", dateOfBirth: "" });
  const [playerDateOfBirth, setPlayerDateOfBirth] = useState("");
  const [childChoice, setChildChoice] = useState<"new" | "existing">("new");
  const [existingProfileId, setExistingProfileId] = useState("");
  const [error, setError] = useState("");
  const [inviteError, setInviteError] = useState("");
  const [needsSignIn, setNeedsSignIn] = useState(false);

  const { data: childrenList = [] } = useQuery({
    queryKey: MY_CHILDREN_QUERY_KEY,
    queryFn: listMyChildren,
    enabled: Boolean(user),
  });

  useEffect(() => {
    if (!token) {
      setInviteLoaded(true);
      return;
    }
    getInviteDetails(token)
      .then((invite) => {
        setKind(invite.inviteKind ?? null);
        setChildName(invite.childFirstName ?? null);
      })
      .catch(() => setInviteError(t("registration.invalid-link")))
      .finally(() => setInviteLoaded(true));
  }, [token, t]);

  const openParent = kind === "PARENT" && !childName;
  const playerSelf = kind === "PLAYER_SELF";
  const canPickExisting = openParent && childrenList.length > 0;
  const useExisting = canPickExisting && childChoice === "existing";

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError("");
    try {
      await acceptInviteWithDetails(token, {
        childFirstName: openParent && !useExisting ? childForm.firstName.trim() : undefined,
        childLastName: openParent && !useExisting ? childForm.lastName.trim() : undefined,
        childDateOfBirth: openParent && !useExisting ? childForm.dateOfBirth || undefined : undefined,
        dateOfBirth: playerSelf ? playerDateOfBirth || undefined : undefined,
        existingProfileId: useExisting ? existingProfileId : undefined,
      });
      try {
        await oidc.signinSilent();
      } catch {
        setNeedsSignIn(true);
        setError(t("registration.signin-again"));
        return;
      }
      queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY });
      queryClient.invalidateQueries({ queryKey: MY_VIEWS_QUERY_KEY });
      navigate("/");
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
      setError(detail || t("registration.failed"));
    }
  };

  return (
    <Container maxWidth="sm" sx={{ display: "flex", alignItems: "center", minHeight: "100vh" }}>
      <Paper elevation={3} sx={{ p: 4, width: "100%", borderRadius: 2 }}>
        {inviteLoaded && !inviteError && !needsSignIn && (
        <Typography variant="h4" sx={{ fontWeight: "bold", textAlign: "center", mb: 3 }}>
          {openParent ? t("registration.open-parent-title") : t("registration.title")}
        </Typography>
        )}
        {token && !inviteLoaded && (
          <Box sx={{ display: "flex", justifyContent: "center", py: 2 }}>
            <CircularProgress />
          </Box>
        )}
        {(inviteError || error) && <Alert severity="error" sx={{ mb: 2 }}>{inviteError || error}</Alert>}
        {token && inviteLoaded && !inviteError && !needsSignIn && (
        <Box component="form" onSubmit={submit}>
          {openParent && (
            <>
              {canPickExisting && (
                <RadioGroup
                  value={childChoice}
                  onChange={(event) => setChildChoice(event.target.value as "new" | "existing")}
                >
                  <FormControlLabel value="existing" control={<Radio />} label={t("registration.existing-child")} />
                  <FormControlLabel value="new" control={<Radio />} label={t("registration.new-child")} />
                </RadioGroup>
              )}
              {useExisting ? (
                <TextField
                  select
                  fullWidth
                  required
                  label={t("registration.choose-child")}
                  value={existingProfileId}
                  onChange={(event) => setExistingProfileId(event.target.value)}
                  margin="normal"
                >
                  {childrenList.map((child) => (
                    <MenuItem key={child.profileId} value={child.profileId}>
                      {formatPersonName(child.firstName, child.lastName)}
                      {childClubAndTeamLabel(child) ? ` · ${childClubAndTeamLabel(child)}` : ""}
                    </MenuItem>
                  ))}
                </TextField>
              ) : (
                <>
                  <Typography variant="overline" color="text.secondary" fontWeight={600} letterSpacing={1.2} sx={{ display: "block" }}>
                    {t("registration.child-section")}
                  </Typography>
                  <TextField fullWidth label={t("registration.child-first-name")} value={childForm.firstName} onChange={(event) => setChildForm((prev) => ({ ...prev, firstName: event.target.value }))} margin="normal" />
                  <TextField fullWidth label={t("registration.child-last-name")} value={childForm.lastName} onChange={(event) => setChildForm((prev) => ({ ...prev, lastName: event.target.value }))} margin="normal" />
                  <TextField fullWidth label={t("registration.child-dob")} type="date" value={childForm.dateOfBirth} onChange={(event) => setChildForm((prev) => ({ ...prev, dateOfBirth: event.target.value }))} margin="normal" InputLabelProps={{ shrink: true }} />
                </>
              )}
            </>
          )}
          {playerSelf && (
            <TextField fullWidth label={t("registration.player-dob")} type="date" value={playerDateOfBirth} onChange={(event) => setPlayerDateOfBirth(event.target.value)} margin="normal" helperText={t("registration.player-dob-help")} InputLabelProps={{ shrink: true }} />
          )}
          <PrimaryButton type="submit" fullWidth sx={{ mt: 2 }} disabled={useExisting && !existingProfileId}>
            {t("registration.submit")}
          </PrimaryButton>
        </Box>
        )}
      </Paper>
    </Container>
  );
};
