import type { RecordData } from '../types/recordData';

export function saveRecord(recordData: RecordData[], blob: Blob): void {
  console.log('[RecordingService] RecordData:', recordData);

  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `recording-${Date.now()}.webm`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export function getRecordings(matchId: string): void {
  console.log('[RecordingService] getRecordings for matchId:', matchId);
  // TODO: fetch recording thumbnails from server and pass to ClipsSidebar
}
