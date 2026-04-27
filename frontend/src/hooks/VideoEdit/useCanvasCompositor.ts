import { useEffect } from 'react';

export function useCanvasCompositor(
  videoRef: React.RefObject<HTMLVideoElement | null>,
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
): void {
  // Match canvas internal resolution to video's native dimensions
  useEffect(() => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas) return;

    const syncSize = () => {
      if (video.videoWidth > 0) {
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
      }
    };

    video.addEventListener('loadedmetadata', syncSize);
    syncSize();
    return () => video.removeEventListener('loadedmetadata', syncSize);
  }, [videoRef, canvasRef]);

  // Draw video frames onto canvas
  useEffect(() => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const drawFrame = () => {
      if (video.readyState >= 2) {
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      }
    };

    // requestVideoFrameCallback fires exactly when the decoder has a new frame ready —
    // no frame skips, no duplicate draws, perfectly aligned with PTS timestamps.
    if ('requestVideoFrameCallback' in video) {
      let id: number;
      const onFrame = () => {
        drawFrame();
        id = video.requestVideoFrameCallback(onFrame);
      };
      id = video.requestVideoFrameCallback(onFrame);
      return () => video.cancelVideoFrameCallback(id);
    }

    // Fallback: plain rAF for browsers without requestVideoFrameCallback
    let rafId: number;
    const loop = () => {
      drawFrame();
      rafId = requestAnimationFrame(loop);
    };
    rafId = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(rafId);
  }, [videoRef, canvasRef]);
}
