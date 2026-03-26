import { useState } from "react";
import { 
  Dialog, 
  DialogTitle, 
  DialogContent, 
  DialogActions, 
  Button, 
  TextField, 
  InputAdornment, 
  IconButton, 
  Typography, 
  Box,
  CircularProgress
} from "@mui/material";
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';

// Ha használod a saját színeidet, itt importálhatod:
// import { APP_COLORS } from "../../../theme/colors";

interface InvitePlayerDialogProps {
  open: boolean;
  onClose: () => void;
  teamId: string | number;
}

export const InvitePlayerDialog = ({ open, onClose, teamId }: InvitePlayerDialogProps) => {
  const [isLoading, setIsLoading] = useState(false);
  const [inviteLink, setInviteLink] = useState<string | null>(null);
  const [isCopied, setIsCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Ide jön majd az OpenAPI által generált hookod, pl:
  // const { mutate: generateInvite } = useGenerateTeamInvite();

  const handleGenerateLink = async () => {
    setIsLoading(true);
    setError(null);
    setInviteLink(null);
    setIsCopied(false);

    try {
      // IDEIGLENES SZIMULÁCIÓ: Itt hívd meg a generált API hookodat!
      // Példa a valós hívásra:
      // generateInvite({ teamId, role: 'PLAYER' }, { onSuccess: (res) => setInviteLink(res.inviteLink) })
      
      await new Promise(resolve => setTimeout(resolve, 1000)); // Szimulált hálózati késés
      const mockResponseLink = `https://te-appod.hu/register?inviteToken=uuid-1234-5678-team-${teamId}`;
      
      setInviteLink(mockResponseLink);
    } catch (err) {
      setError("Hiba történt a link generálása során. Kérlek, próbáld újra.");
      console.error(err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCopy = async () => {
    if (inviteLink) {
      try {
        await navigator.clipboard.writeText(inviteLink);
        setIsCopied(true);
        // 3 másodperc múlva visszaállítjuk az ikont
        setTimeout(() => setIsCopied(false), 3000);
      } catch (err) {
        console.error("Nem sikerült a vágólapra másolni!", err);
      }
    }
  };

  const handleClose = () => {
    // Alaphelyzetbe állítjuk a state-eket bezáráskor
    setTimeout(() => {
      setInviteLink(null);
      setIsCopied(false);
      setError(null);
    }, 200); // Késleltetve, hogy a bezáródó animáció alatt ne villanjon át
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle fontWeight="bold">Játékos Meghívása</DialogTitle>
      
      <DialogContent dividers>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
          Generálj egy egyszer használatos, biztonságos meghívó linket. A linket küldd el a játékosnak, amivel regisztrálhat és automatikusan csatlakozhat a csapathoz.
        </Typography>

        {error && (
          <Typography color="error" variant="body2" sx={{ mb: 2 }}>
            {error}
          </Typography>
        )}

        {/* Ha még nincs link, a Generálás gombot mutatjuk */}
        {!inviteLink ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
            <Button 
              variant="contained" 
              onClick={handleGenerateLink} 
              disabled={isLoading}
              // A te témád gomb színét használva (opcionális):
              // sx={{ bgcolor: APP_COLORS.filledAction.main, '&:hover': { bgcolor: APP_COLORS.filledAction.hover } }}
            >
              {isLoading ? <CircularProgress size={24} color="inherit" /> : "Meghívó link generálása"}
            </Button>
          </Box>
        ) : (
          /* Ha megvan a link, megjelenítjük a másolható mezőt */
          <Box sx={{ mt: 1 }}>
            <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 'bold' }}>
              Elkészült a meghívó link (1 óráig érvényes):
            </Typography>
            <TextField
              fullWidth
              variant="outlined"
              value={inviteLink}
              InputProps={{
                readOnly: true,
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton onClick={handleCopy} color={isCopied ? "success" : "default"}>
                      {isCopied ? <CheckCircleIcon /> : <ContentCopyIcon />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
              // Kisebb vizuális trükk, hogy kiemeljük, ha sikeres a másolás
              sx={{
                '& .MuiOutlinedInput-root': {
                  borderColor: isCopied ? 'success.main' : 'default',
                }
              }}
            />
          </Box>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} color="inherit">
          Bezárás
        </Button>
      </DialogActions>
    </Dialog>
  );
};