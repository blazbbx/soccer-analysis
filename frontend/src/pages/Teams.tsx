import { useEffect, useState } from "react";
import {
  Box,
  Container,
  Typography,
  CircularProgress,
  Stack,
} from "@mui/material";

import { TeamDto } from "../types/team";
import { teamService } from "../services/teamService"; 

import { TeamCard } from "../components/common/TeamPageComps/TeamCard"; 
import { PrimaryButton } from "../components/common/ui/PrimaryButton";
import { FilledActionButton } from "../components/common/ui/FilledActionButton";

import PersonAddAlt1Icon from "@mui/icons-material/PersonAddAlt1";
import AddIcon from "@mui/icons-material/Add";
import { useAuth } from "../context/AuthContext";
import { ROLES } from "../types/roles";
import { useTranslation } from "react-i18next";

export const Teams = () => {
  const [teams, setTeams] = useState<TeamDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const { user } = useAuth();

  const {t} = useTranslation();

  useEffect(() => {
    const fetchTeams = async () => {
      try {
        setIsLoading(true); 
        const data = await teamService.getMyTeams();
        setTeams(data);
      } catch (error) {
        console.error("Hiba történt a csapatok lekérésekor:", error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchTeams();
  }, []); 

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      {/* Oldal címe */}
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 4 }}
      >
        {/* Bal oldal: Cím és Számláló */}
        <Box>
          <Typography
            variant="h1"
            sx={{
              fontSize: "2rem",
              fontWeight: "bold",
              color: "text.primary",
              mb: 0.5,
            }}
          >
            {t("sidebar.teams")}
          </Typography>
          <Typography variant="body1" sx={{ color: "text.secondary" }}>
            {/* Dinamikusan kiírjuk a tömb hosszát */}
            {isLoading ? "Loading..." : `${teams.length} ${t("teams.teams-available")}`}
          </Typography>
        </Box>

        {user?.role === ROLES.COACH && (
          /* Jobb oldal: Két kapszula alakú gomb */
          <Stack direction="row" spacing={2}>
            {/* Invite Player gomb (Outlined, sötétes háttérrel) */}
            <PrimaryButton startIcon={<PersonAddAlt1Icon />}>
              {t("teams.invite-player")}
            </PrimaryButton>

            {/* Create Team gomb (Contained, élénk zöld) */}
            <FilledActionButton startIcon={<AddIcon />}>
              {t("teams.create-team")}
            </FilledActionButton>
          </Stack>
        )}
        {user?.role === ROLES.PLAYER && (
          <FilledActionButton startIcon={<AddIcon/>}>
            {t("teams.join-team")}
          </FilledActionButton> 
        )}


      </Stack>

      {/* Feltételes renderelés: Ha töltünk, Spinner, ha nem, kártyák */}
      {isLoading ? (
        <Box
          sx={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            minHeight: "40vh",
          }}
        >
          <CircularProgress sx={{ color: "#10b981" }} /> {/* Zöld pörgő */}
        </Box>
      ) : (
        // A Stack egymás alá rendezi a kártyákat, 4-es távolsággal
        <Stack spacing={4}>
          {teams.length === 0 ? (
            <Typography variant="body1" sx={{ color: "text.secondary" }}>
              {t("teams.noteamsmessage")}
            </Typography>
          ) : (
            // Végigmegyünk a lekérdezett csapatokon, és mindegyiknek kirajzolunk egy TeamCard-ot
            teams.map((team) => <TeamCard key={team.id} team={team} showInviteAction={user?.role===ROLES.COACH} />)
          )}
        </Stack>
      )}
    </Container>
  );
};
