import { useEffect, useRef } from "react";
import { useVideoPlayer } from "../../context/VideoPlayerContext";


export const useVideoDrawing = (
  canvasRef: React.RefObject<HTMLCanvasElement  | null>,
  videoRef: React.RefObject<HTMLVideoElement | null>
) => {
  const { activeDrawTool, activeDrawColor, undoTrigger, clearTrigger } = useVideoPlayer();
  const isDrawing = useRef(false);
  const startPos = useRef({ x: 0, y: 0 });
  const savedSnapshot = useRef<ImageData | null>(null);

  const history = useRef<ImageData[]>([]);

  useEffect(() => {
    if (clearTrigger > 0) {
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext('2d');
      if (ctx && canvas) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        history.current = []; 
      }
    }
  }, [clearTrigger]);

  useEffect(() => {
    if (undoTrigger > 0) {
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext('2d');
      if (ctx && canvas && history.current.length > 0) {
        
        history.current.pop(); 
        
        if (history.current.length > 0) {
          
          const previousState = history.current[history.current.length - 1];
          ctx.putImageData(previousState, 0, 0);
        } else {
          
          ctx.clearRect(0, 0, canvas.width, canvas.height);
        }
      }
    }
  }, [undoTrigger]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const resizeCanvas = () => {
      canvas.width = video.clientWidth;
      canvas.height = video.clientHeight;
    };

    resizeCanvas();
    window.addEventListener("resize", resizeCanvas);
    return () => window.removeEventListener("resize", resizeCanvas);
  }, [canvasRef, videoRef]); 

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || activeDrawTool === 'none') return; 

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const getMousePos = (evt: MouseEvent) => {
      const rect = canvas.getBoundingClientRect();
      return {
        x: evt.clientX - rect.left,
        y: evt.clientY - rect.top,
      };
    };

    const handleMouseDown = (e: MouseEvent) => {
      isDrawing.current = true;
      startPos.current = getMousePos(e);
      savedSnapshot.current = ctx.getImageData(0, 0, canvas.width, canvas.height);

      ctx.beginPath();
      ctx.strokeStyle = activeDrawColor;
      ctx.fillStyle = activeDrawColor;
      ctx.lineWidth = 3;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
    };

    const handleMouseMove = (e: MouseEvent) => {
      if (!isDrawing.current) return;
      const currentPos = getMousePos(e);

      if (activeDrawTool === "pen") {
        ctx.lineTo(currentPos.x, currentPos.y);
        ctx.stroke();
      } else {
        if (savedSnapshot.current) {
          ctx.putImageData(savedSnapshot.current, 0, 0);
        }
        ctx.beginPath();
        
        if (activeDrawTool === "circle") {
          const radius = Math.sqrt(
            Math.pow(currentPos.x - startPos.current.x, 2) +
            Math.pow(currentPos.y - startPos.current.y, 2)
          );
          ctx.arc(startPos.current.x, startPos.current.y, radius, 0, 2 * Math.PI);
          ctx.stroke();
        } else if (activeDrawTool === "arrow") {
          
          ctx.moveTo(startPos.current.x, startPos.current.y);
          ctx.lineTo(currentPos.x, currentPos.y);
          ctx.stroke();
          
          const angle = Math.atan2(currentPos.y - startPos.current.y, currentPos.x - startPos.current.x);
          const headLength = 15;
          ctx.beginPath();
          ctx.moveTo(currentPos.x, currentPos.y);
          ctx.lineTo(currentPos.x - headLength * Math.cos(angle - Math.PI / 6), currentPos.y - headLength * Math.sin(angle - Math.PI / 6));
          ctx.lineTo(currentPos.x - headLength * Math.cos(angle + Math.PI / 6), currentPos.y - headLength * Math.sin(angle + Math.PI / 6));
          ctx.lineTo(currentPos.x, currentPos.y);
          ctx.fill();
        }
      }
    };

    const handleMouseUp = () => {
      if (isDrawing.current) {
        isDrawing.current = false;

        const snapshot = ctx.getImageData(0, 0, canvas.width, canvas.height);
        history.current.push(snapshot);

        if (history.current.length > 30) {
          history.current.shift();
        }
      }
    };

    canvas.addEventListener("mousedown", handleMouseDown);
    window.addEventListener("mousemove", handleMouseMove); 
    window.addEventListener("mouseup", handleMouseUp);

    return () => {
      canvas.removeEventListener("mousedown", handleMouseDown);
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseup", handleMouseUp);
    };
  }, [activeDrawTool, activeDrawColor, canvasRef]); 
};