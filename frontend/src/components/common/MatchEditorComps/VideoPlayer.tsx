import React from 'react';
import { Box } from '@mui/material';
import ReactPlayer from 'react-player'; 

interface VideoPlayerProps {
  videoUrl: string;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({ videoUrl }) => {  

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
        controls={true} 
        playing={true}  
      />
    </Box>
  );
};