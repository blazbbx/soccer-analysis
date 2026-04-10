import { useState } from "react";
import {
  Box,
  Typography,
  Stack
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useNavigate } from "react-router-dom";


import {
  UploadDialog,
  type MatchUploadData,
} from "../components/common/MatchesPageComps/Upload/UploadDialog";
import { MatchCard } from "../components/common/MatchesPageComps/MatchCard";

import {
  useGetAllMatches,
} from "../api/generated/match-controller/match-controller";
import { useGetAllTeams } from "../api/generated/teams/teams";
import { type MatchResponse } from "../api/generated/model/matchResponse";
import { type TeamResponse } from "../api/generated/model";
import { FilledActionButton } from "../components/common/ui/FilledActionButton";
import { useMatchUploadFlow } from "../hooks/MatchUpload/useMatchUploadFlow";

export const Matches = () => {
  const navigate = useNavigate();
  const [isUploadOpen, setIsUploadOpen] = useState(false);

  
  const { uploadProgress, uploadPhase, startUpload } = useMatchUploadFlow();

  
  const { data: teamsData } = useGetAllTeams();
  const { data: matchesData } = useGetAllMatches();

  const teams = (teamsData as unknown as TeamResponse[]) || [];
  const matches = (matchesData as unknown as MatchResponse[]) || [];

  const isUploading = uploadPhase === 'uploading' || uploadPhase === 'initiating';
  const isUploadingToMinio = uploadProgress !== null && uploadProgress < 100;

  
  const handleVideoUpload = async (data: MatchUploadData) => {
    try {
      await startUpload(data);
      
      setIsUploadOpen(false);
    } catch (err) {
      
      console.error('Upload failed:', err);
    }
  };

  return (    
    <Box sx={{ p: 4 }}>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 4,
        }}
      >
        <Typography variant="h4" fontWeight="bold">
          Mérkőzések
        </Typography>
        <FilledActionButton
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setIsUploadOpen(true)}
          disabled={isUploading}
        >
          Mérkőzés feltöltése
        </FilledActionButton>
      </Box>

      {/* Progress kijelzése, ha épp töltünk fel valamit */}
      {isUploadingToMinio && (
        <Box sx={{ mb: 4, p: 2, bgcolor: "primary.light", borderRadius: 1 }}>
          <Typography
            variant="body1"
            color="primary.contrastText"
            fontWeight="bold"
          >
            Videó feltöltése folyamatban... {uploadProgress}%
          </Typography>
        </Box>
      )}

      
      <Stack spacing={2} sx={{ mt: 2 }}>        
        {matches.map((match: MatchResponse) => (
          <MatchCard
            key={match.id}
            match={match}
            onOpen={(matchId) => navigate(`/matches/${matchId}`)}
          />          
        ))}
      </Stack>

      <UploadDialog
        open={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onUpload={handleVideoUpload}
        isUploading={isUploading}
        uploadProgress={uploadProgress}
        teams={teams}
      />
    </Box>
  );
};
