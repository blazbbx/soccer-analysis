import React, { useState } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { UploadDialog } from './UploadDialog';
import { VideoPlayer } from './VideoPlayer';
import { useMockVideoUploader } from '../../../hooks/useVideoUploader';

export const MatchAnalyzer = () => {
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const { isUploading, videoUrl, uploadVideo } = useMockVideoUploader();
  

 const handleVideoUpload = async (file: File) => {
    const isSuccess = await uploadVideo(file);
    
    if (isSuccess) {
      setIsUploadOpen(false);
    }   
  };

  return (
    <Box sx={{ p: 4 }}>
      <Typography variant="h4" gutterBottom>
        Mérkőzés Elemző
      </Typography>
      
      {/* Feltöltés gomb */}
      <Button variant="contained" onClick={() => setIsUploadOpen(true)} sx={{ mb: 4 }}>
        Mérkőzés Feltöltése
      </Button>

      {/* Dialógusablak */}
      <UploadDialog 
        open={isUploadOpen} 
        onClose={() => setIsUploadOpen(false)} 
        onUpload={handleVideoUpload} 
        isUploading = {isUploading}
      />

      {/* Ha van feltöltött videó, megjelenítjük a lejátszót */}
      {videoUrl && (
        <Box sx={{ mt: 4 }}>
          <Typography variant="h6" gutterBottom>
            Feltöltött videó előnézete:
          </Typography>
          <VideoPlayer videoUrl={videoUrl} />
        </Box>
      )}
    </Box>
  );
};