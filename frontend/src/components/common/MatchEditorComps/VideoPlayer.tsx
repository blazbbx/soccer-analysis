import React from 'react';
import { Box } from '@mui/material';
import ReactPlayer from 'react-player'; // Ha ez hibát adna, próbáld így: import ReactPlayer from 'react-player/lazy';

interface VideoPlayerProps {
  videoUrl: string;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({ videoUrl }) => {
  // Figyeld meg: Nincs többé useRef, useState és togglePlay!
  // A react-player beépítve kezeli ezeket.

  return (
    <Box 
      sx={{ 
        position: 'relative', 
        width: '100%', 
        maxWidth: '800px', 
        borderRadius: 2, 
        overflow: 'hidden', 
        bgcolor: 'black',
        aspectRatio: '16/9',
      }}
    >
      <ReactPlayer
        src={videoUrl}
        width="100%"
        height="100%"
        controls={true} // Ez teszi ki a play/pause gombokat és az idősávot
        playing={true}  // Ezt true-ra hagyhatod, ha egyből el akarod indítani
      />
    </Box>
  );
};