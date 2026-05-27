import { useState, useCallback, useEffect, useRef } from 'react';
import axios from 'axios';
import { useQueryClient } from '@tanstack/react-query';
import {
  useInitiateUpload,
  getGetAllMatchesQueryKey,
  getGetMatchQueryKey,
} from '../../../api/generated/match-controller/match-controller';
import type { MatchResponse } from '../../../api/generated/model/matchResponse';
import api from '../../../api/axiosInstance';
import { openMatchEventStream, type MatchEventStreamHandle } from '../../../services/matchEventStream';
import type { MatchUploadData } from '../Upload/UploadDialog';

export type UploadPhase =
  | 'idle'
  | 'initiating'
  | 'uploading'
  | 'preprocessing'
  | 'awaiting-corners'
  | 'confirming'
  | 'completed'
  | 'error';

export interface Corner {
  x: number;
  y: number;
}

export interface FieldDetectionPayload {
  defishedImageUrl: string;
  corners: Corner[];
}

export interface ResumableMatch {
  id?: string;
  defishedImageUrl?: string | null;
  fieldCorners?: Corner[] | null;
}

export interface UseMatchUploadFlowReturn {
  uploadProgress: number | null;
  uploadPhase: UploadPhase;
  error: string | null;
  matchId: string | null;
  fieldDetection: FieldDetectionPayload | null;
  startUpload: (data: MatchUploadData) => Promise<void>;
  confirmCorners: (corners: Corner[]) => Promise<void>;
  resumeFromAwaitingCorners: (match: ResumableMatch) => void;
  reset: () => void;
}

export const useMatchUploadFlow = (): UseMatchUploadFlowReturn => {
  const queryClient = useQueryClient();
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [uploadPhase, setUploadPhase] = useState<UploadPhase>('idle');
  const [error, setError] = useState<string | null>(null);
  const [matchId, setMatchId] = useState<string | null>(null);
  const [fieldDetection, setFieldDetection] = useState<FieldDetectionPayload | null>(null);

  const streamRef = useRef<MatchEventStreamHandle | null>(null);

  const { mutate: initiateUploadMutation } = useInitiateUpload();

  const closeStream = useCallback(() => {
    streamRef.current?.close();
    streamRef.current = null;
  }, []);

  // Make sure we never leak the SSE connection if the component using this hook unmounts.
  useEffect(() => closeStream, [closeStream]);

  const subscribe = useCallback((id: string) => {
    closeStream();
    streamRef.current = openMatchEventStream(id, {
      onEvent: (eventName, rawData) => {
        if (eventName === 'FIELD_DETECTED') {
          try {
            const payload = JSON.parse(rawData) as FieldDetectionPayload;
            setFieldDetection(payload);
            setUploadPhase('awaiting-corners');
          } catch (e) {
            console.error('[Upload] Failed to parse FIELD_DETECTED payload:', e);
            setError('Invalid field-detection payload from server.');
            setUploadPhase('error');
          }
        } else if (eventName === 'COMPLETED') {
          setUploadPhase('completed');
          void queryClient.refetchQueries({ queryKey: getGetAllMatchesQueryKey() });
          closeStream();
        } else if (eventName === 'ERROR') {
          setError('Processing failed.');
          setUploadPhase('error');
          closeStream();
        }
      },
      onError: (err) => {
        console.error('[Upload] SSE error:', err);
      },
    });
  }, [closeStream, queryClient]);

  const startUpload = useCallback(async (data: MatchUploadData) => {
    setUploadProgress(0);
    setUploadPhase('initiating');
    setError(null);
    setMatchId(null);
    setFieldDetection(null);

    try {
      const initiated = await new Promise<{ uploadUrl: string; matchId: string }>((resolve, reject) => {
        initiateUploadMutation(
          {
            data: {
              originalFilename: data.file.name,
              homeTeamId: data.homeTeamId,
              matchDate: data.matchDate ? `${data.matchDate}T00:00:00.000Z` : undefined,
              homeTeamColor: data.homeTeamColor,
              awayTeamColor: data.awayTeamColor,
              refereeColor: data.refereeColor,
              awayTeamName: data.awayTeamName,
            },
          },
          {
            onSuccess: (response) => resolve(response as unknown as { uploadUrl: string; matchId: string }),
            onError: (err) => reject(err),
          },
        );
      });

      setMatchId(initiated.matchId);
      // Open the SSE channel before the backend can finish field detection so the
      // FIELD_DETECTED event is never missed.
      subscribe(initiated.matchId);

      setUploadPhase('uploading');

      await axios.put(initiated.uploadUrl, data.file, {
        headers: { 'Content-Type': data.file.type },
        onUploadProgress: (progressEvent) => {
          const percent = Math.round((progressEvent.loaded * 100) / (progressEvent.total ?? 1));
          setUploadProgress(percent);
        },
      });

      setUploadProgress(100);
      setUploadPhase('preprocessing');
      void queryClient.refetchQueries({ queryKey: getGetAllMatchesQueryKey() });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Upload failed';
      console.error('[Upload] Error:', message);
      setError(message);
      setUploadPhase('error');
      setUploadProgress(null);
      closeStream();
    }
  }, [initiateUploadMutation, queryClient, subscribe, closeStream]);

  const confirmCorners = useCallback(async (corners: Corner[]) => {
    if (!matchId) {
      throw new Error('No matchId available for corner confirmation.');
    }
    setUploadPhase('confirming');
    setError(null);
    try {
      // The endpoint returns the freshly-updated MatchResponse (overallStatus now
      // 'PROCESSING'). Shove it straight into the per-match query cache so MatchCard
      // sees the new state on the very next render — otherwise the cached
      // AWAITING_CORNERS keeps winning over the list refetch (MatchCard prefers the
      // per-match query over the prop), and the "Pályaválasztás" chip lingers until
      // a manual refresh.
      const response = await api.post<MatchResponse>(
        `/api/matches/${matchId}/confirm-corners`,
        { corners },
      );
      queryClient.setQueryData(getGetMatchQueryKey(matchId), response.data);
      // List query stays the authoritative source for the matches array on the
      // Matches page; refetch it so newly-added fields (e.g. trackingDataUrl after
      // completion) are picked up by anything else reading the list.
      void queryClient.refetchQueries({ queryKey: getGetAllMatchesQueryKey() });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to confirm corners.';
      console.error('[Upload] confirmCorners error:', message);
      setError(message);
      setUploadPhase('awaiting-corners');
      throw err;
    }
  }, [matchId, queryClient]);

  /**
   * Hydrate the flow from an existing match that's already in AWAITING_CORNERS state.
   * Used by MatchCard's "Field selection" chip when a coach left mid-flow (closed the
   * dialog, lost connection, or closed the browser) and is now resuming. The defisheyed
   * image URL and detected corners are pulled from the match record (persisted by the
   * backend the moment field detection finished, regardless of who was online).
   */
  const resumeFromAwaitingCorners = useCallback((match: ResumableMatch) => {
    if (!match.id) return;
    setError(null);
    setUploadProgress(null);
    setMatchId(match.id);
    setFieldDetection({
      defishedImageUrl: match.defishedImageUrl ?? '',
      corners: match.fieldCorners ?? [],
    });
    setUploadPhase('awaiting-corners');
    // Re-subscribe to SSE so a subsequent COMPLETED/ERROR for this match still flows
    // to the dialog and triggers a list refetch.
    subscribe(match.id);
  }, [subscribe]);

  const reset = useCallback(() => {
    closeStream();
    setUploadProgress(null);
    setUploadPhase('idle');
    setError(null);
    setMatchId(null);
    setFieldDetection(null);
  }, [closeStream]);

  return {
    uploadProgress,
    uploadPhase,
    error,
    matchId,
    fieldDetection,
    startUpload,
    confirmCorners,
    resumeFromAwaitingCorners,
    reset,
  };
};
