import React, { useState, useRef, useEffect } from 'react';
import { Box, Slider, IconButton, Stack, Typography, Button, List, ListItem, ListItemText, Paper } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import CreateIcon from '@mui/icons-material/Create';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import Hls from 'hls.js';

interface VideoPlayerProps {
  videoUrl: string;
}

interface Snippet {
  id: number;
  name: string;
  startTime: number;
  endTime: number;
}

const formatTime = (seconds: number) => {
  const m = Math.floor(seconds / 60).toString().padStart(2, '0');
  const s = Math.floor(seconds % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
};

export const VideoPlayer: React.FC<VideoPlayerProps> = ({ videoUrl }) => {  
  // --- VIDEÓ ÁLLAPOTOK ---
  const [playing, setPlaying] = useState<boolean>(false);
  const [played, setPlayed] = useState<number>(0);
  const [duration, setDuration] = useState<number>(0);
  const [seeking, setSeeking] = useState<boolean>(false);
  
  // --- SNIPPET ÁLLAPOTOK ---
  const [snippets, setSnippets] = useState<Snippet[]>([]);
  const [clipStart, setClipStart] = useState<number | null>(null);
  
  // --- RAJZOLÓ ÁLLAPOTOK ---
  const [isDrawMode, setIsDrawMode] = useState<boolean>(false);
  const [isDrawing, setIsDrawing] = useState<boolean>(false);

  // --- REFERENCIÁK ---
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  // --- HLS LOGIKA BEVEZETÉSE ---
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !videoUrl) return;

    let hls: Hls;

    // Ha a böngésző támogatja a hls.js-t (pl. Chrome, Firefox, Edge)
    if (Hls.isSupported()) {
      hls = new Hls({
        // Ide lehet tenni HLS specifikus beállításokat, de alapból jó így is
      });
      
      hls.loadSource(videoUrl);
      hls.attachMedia(video);
      
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        // Amikor a manifest betöltött, a videó készen áll
        console.log("HLS stream betöltve!");
      });
    } 
  
    else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = videoUrl;
    }

    // Cleanup: ha eltűnik a komponens vagy változik az URL, takarítunk
    return () => {
      if (hls) {
        hls.destroy();
      }
    };
  }, [videoUrl]);

  // --- RAJZOLÓ LOGIKA --- 
  useEffect(() => {
    const canvas = canvasRef.current;
    if (canvas && isDrawMode) {
      const { width, height } = canvas.getBoundingClientRect();
      canvas.width = width;
      canvas.height = height;
      
      const context = canvas.getContext('2d');
      if (context) {
        context.lineCap = 'round';
        context.lineJoin = 'round';
        context.strokeStyle = '#ff0000'; 
        context.lineWidth = 4;          
      }
    }
  }, [isDrawMode]);

  const startDrawing = ({ nativeEvent }: React.MouseEvent) => {
    if (!isDrawMode) return;
    const { offsetX, offsetY } = nativeEvent;
    const context = canvasRef.current?.getContext('2d');
    if (context) {
      context.beginPath();
      context.moveTo(offsetX, offsetY);
      setIsDrawing(true);
      if (playing && videoRef.current) {
        videoRef.current.pause();
        setPlaying(false);
      }
    }
  };

  const draw = ({ nativeEvent }: React.MouseEvent) => {
    if (!isDrawing || !isDrawMode) return;
    const { offsetX, offsetY } = nativeEvent;
    const context = canvasRef.current?.getContext('2d');
    if (context) {
      context.lineTo(offsetX, offsetY);
      context.stroke();
    }
  };

  const stopDrawing = () => {
    if (!isDrawing) return;
    const context = canvasRef.current?.getContext('2d');
    if (context) {
      context.closePath();
      setIsDrawing(false);
    }
  };

  const clearCanvas = () => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d');
    if (canvas && context) {
      context.clearRect(0, 0, canvas.width, canvas.height);
    }
  };

  // --- VIDEÓ ÉS SNIPPET LOGIKA ---

  const handlePlayPause = () => {
    if (videoRef.current) {
      if (playing) videoRef.current.pause();
      else videoRef.current.play();
      setPlaying(!playing);
    }
  };

  const handleLoadedMetadata = () => {
    if (videoRef.current) setDuration(videoRef.current.duration);
  };

  const handleTimeUpdate = () => {
    if (!seeking && videoRef.current && duration > 0) {
      setPlayed(videoRef.current.currentTime / duration);
    }
  };

  const handleSeekChange = (_event: Event, newValue: number | number[]) => {
    setPlayed(newValue as number);
  };

  const handleSeekMouseUp = (_event: React.SyntheticEvent | Event, newValue: number | number[]) => {
    setSeeking(false);
    if (videoRef.current && duration > 0) {
      videoRef.current.currentTime = (newValue as number) * duration;
    }
  };

  const handleMarkStart = () => {
    if (videoRef.current) setClipStart(videoRef.current.currentTime);
  };

  const handleMarkEnd = () => {
    if (videoRef.current && clipStart !== null) {
      const endTime = videoRef.current.currentTime;
      if (endTime > clipStart) {
        setSnippets([...snippets, { id: Date.now(), name: `Jelenet ${snippets.length + 1}`, startTime: clipStart, endTime }]);
        setClipStart(null);
      }
    }
  };

  const handlePlaySnippet = (snippet: Snippet) => {
    if (videoRef.current) {
      videoRef.current.currentTime = snippet.startTime;
      videoRef.current.play();
      setPlaying(true);
    }
  };

  return (
    <Box sx={{ width: '100%', maxWidth: '800px', display: 'flex', flexDirection: 'column', gap: 2 }}>
      
      <Box sx={{ borderRadius: 2, overflow: 'hidden', bgcolor: '#121212' }}>
        
        {/* VIDEÓ ÉS VÁSZON KONTÉNER */}
        <Box sx={{ position: 'relative', width: '100%', aspectRatio: '16/9', bgcolor: 'black' }}>
          
          <video
            ref={videoRef}
            style={{ width: '100%', height: '100%', cursor: isDrawMode ? 'crosshair' : 'pointer' }}
            onClick={!isDrawMode ? handlePlayPause : undefined} // Ha rajzolunk, a kattintás ne állítsa meg a videót!
            onTimeUpdate={handleTimeUpdate}
            onLoadedMetadata={handleLoadedMetadata}
            onPlay={() => setPlaying(true)}
            onPause={() => setPlaying(false)}
          />

          {/* ÁTLÁTSZÓ RAJZOLÓ RÉTEG */}
          <canvas
            ref={canvasRef}
            onMouseDown={startDrawing}
            onMouseMove={draw}
            onMouseUp={stopDrawing}
            onMouseLeave={stopDrawing} // Ha kihúzzuk az egeret a videóról, hagyja abba a rajzolást
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              height: '100%',
              pointerEvents: isDrawMode ? 'auto' : 'none', // Ha ki van kapcsolva, a kattintás "átmegy" a videóra
              cursor: 'crosshair',
            }}
          />
        </Box>

        {/* VEZÉRLŐK */}
        <Box sx={{ p: 2 }}>
          <Slider
            min={0} max={1} step={0.001} value={played}
            onMouseDown={() => setSeeking(true)}
            onChange={handleSeekChange}
            onChangeCommitted={handleSeekMouseUp}
            sx={{ color: '#1976d2', padding: '0 !important', '& .MuiSlider-thumb': { width: 12, height: 12 } }}
          />

          <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 1 }}>
            <Stack direction="row" alignItems="center" spacing={2}>
              <IconButton onClick={handlePlayPause} sx={{ color: 'white' }}>
                {playing ? <PauseIcon /> : <PlayArrowIcon />}
              </IconButton>
              <Typography variant="body2" sx={{ color: 'white', minWidth: '90px' }}>
                {videoRef.current ? formatTime(videoRef.current.currentTime) : "00:00"} / {formatTime(duration)}
              </Typography>

              {/* Rajzoló gombok */}
              <Box sx={{ borderLeft: '1px solid #444', pl: 2, display: 'flex', gap: 1 }}>
                <Button 
                  variant={isDrawMode ? "contained" : "outlined"} 
                  color="error" 
                  size="small" 
                  startIcon={<CreateIcon />}
                  onClick={() => setIsDrawMode(!isDrawMode)}
                >
                  Rajzolás
                </Button>
                <IconButton size="small" color="error" onClick={clearCanvas} title="Rajz törlése">
                  <DeleteSweepIcon />
                </IconButton>
              </Box>

            </Stack>
            
            <Stack direction="row" spacing={1}>
              <Button variant="contained" color="secondary" size="small" onClick={handleMarkStart}>
                IN [ {clipStart !== null ? formatTime(clipStart) : '-'} ]
              </Button>
              <Button variant="contained" color="success" size="small" onClick={handleMarkEnd} disabled={clipStart === null}>
                OUT
              </Button>
            </Stack>
          </Stack>
        </Box>
      </Box>

      {/* SNIPPETEK */}
      {snippets.length > 0 && (
        <Paper sx={{ p: 2, bgcolor: '#1e1e1e', color: 'white' }}>
          <Typography variant="h6" gutterBottom>Kivágott jelenetek</Typography>
          <List>
            {snippets.map((snippet) => (
              <ListItem 
                key={snippet.id} 
                sx={{ bgcolor: '#2c2c2c', mb: 1, borderRadius: 1, cursor: 'pointer', '&:hover': { bgcolor: '#3c3c3c' } }}
                onClick={() => handlePlaySnippet(snippet)}
              >
                <ListItemText primary={snippet.name} secondary={<Typography variant="body2" color="gray">{formatTime(snippet.startTime)} - {formatTime(snippet.endTime)}</Typography>} />
              </ListItem>
            ))}
          </List>
        </Paper>
      )}
    </Box>
  );
};