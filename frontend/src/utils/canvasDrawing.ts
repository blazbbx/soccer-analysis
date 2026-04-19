export interface Point {
  x: number;
  y: number;
}

const LINE_WIDTH = 3;

export function drawPenPath(
  ctx: CanvasRenderingContext2D,
  points: Point[],
  color: string
): void {
  if (points.length < 2) return;
  ctx.beginPath();
  ctx.strokeStyle = color;
  ctx.lineWidth = LINE_WIDTH;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.moveTo(points[0].x, points[0].y);
  for (let i = 1; i < points.length; i++) {
    ctx.lineTo(points[i].x, points[i].y);
  }
  ctx.stroke();
}

export function drawArrow(
  ctx: CanvasRenderingContext2D,
  start: Point,
  end: Point,
  color: string
): void {
  ctx.beginPath();
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineWidth = LINE_WIDTH;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';

  ctx.moveTo(start.x, start.y);
  ctx.lineTo(end.x, end.y);
  ctx.stroke();

  const angle = Math.atan2(end.y - start.y, end.x - start.x);
  const headLength = 15;
  ctx.beginPath();
  ctx.moveTo(end.x, end.y);
  ctx.lineTo(
    end.x - headLength * Math.cos(angle - Math.PI / 6),
    end.y - headLength * Math.sin(angle - Math.PI / 6)
  );
  ctx.lineTo(
    end.x - headLength * Math.cos(angle + Math.PI / 6),
    end.y - headLength * Math.sin(angle + Math.PI / 6)
  );
  ctx.lineTo(end.x, end.y);
  ctx.fill();
}

export function drawCircle(
  ctx: CanvasRenderingContext2D,
  center: Point,
  radiusPoint: Point,
  color: string
): void {
  const radius = Math.sqrt(
    Math.pow(radiusPoint.x - center.x, 2) + Math.pow(radiusPoint.y - center.y, 2)
  );
  ctx.beginPath();
  ctx.strokeStyle = color;
  ctx.lineWidth = LINE_WIDTH;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.arc(center.x, center.y, radius, 0, 2 * Math.PI);
  ctx.stroke();
}
