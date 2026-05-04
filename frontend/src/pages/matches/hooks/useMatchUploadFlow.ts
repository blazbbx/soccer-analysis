import { useState, useCallback } from 'react';
import axios from 'axios';
import { useQueryClient } from '@tanstack/react-query';
import { useInitiateUpload, getGetAllMatchesQueryKey } from '../../../api/generated/match-controller/match-controller';
import type { MatchUploadData } from '../Upload/UploadDialog';

type UploadPhase = 'idle' | 'initiating' | 'uploading' | 'completing' | 'completed' | 'error';

export interface UseMatchUploadFlowReturn {
  uploadProgress: number | null;
  uploadPhase: UploadPhase;
  error: string | null;
  startUpload: (data: MatchUploadData) => Promise<void>;
  reset: () => void;
}

export const useMatchUploadFlow = (): UseMatchUploadFlowReturn => {
  const queryClient = useQueryClient();
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [uploadPhase, setUploadPhase] = useState<UploadPhase>('idle');
  const [error, setError] = useState<string | null>(null);

  const { mutate: initiateUploadMutation } = useInitiateUpload();

  const startUpload = useCallback(async (data: MatchUploadData) => {
    
    setUploadProgress(0);
    setUploadPhase('initiating');
    setError(null);

    try {
      
      const initiatePromise = new Promise<{ uploadUrl: string }>((resolve, reject) => {
        initiateUploadMutation(
          {
            data: {
              originalFilename: data.file.name,
              homeTeamId: data.homeTeamId,
              matchDate: data.matchDate ? `${data.matchDate}T00:00:00.000Z` : undefined,
              homeTeamColor: data.homeTeamColor,
              awayTeamColor: data.awayTeamColor,
              refereeColor: data.refereeColor,
              awayTeamName: data.awayTeamName
            },
          },
          {
            onSuccess: (response) => {
              resolve(response as any);
            },
            onError: (err) => {
              reject(err);
            },
          }
        );
      });

      const { uploadUrl } = await initiatePromise;

      setUploadPhase('uploading');

      await axios.put(uploadUrl, data.file, {
        headers: { 'Content-Type': data.file.type },
        onUploadProgress: (progressEvent) => {
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total!
          );
          setUploadProgress(percentCompleted);
        },
      });

      setUploadProgress(100);
      setUploadPhase('completing');

      await new Promise((resolve) => setTimeout(resolve, 500));

      await queryClient.refetchQueries({
        queryKey: getGetAllMatchesQueryKey(),
      });

      setUploadPhase('completed');

      await new Promise((resolve) => setTimeout(resolve, 300));
      setUploadProgress(null);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Upload failed';
      console.error('[Upload] Error:', errorMessage);
      setError(errorMessage);
      setUploadPhase('error');
      setUploadProgress(null);
    }
  }, [initiateUploadMutation, queryClient]);

  const reset = useCallback(() => {
    setUploadProgress(null);
    setUploadPhase('idle');
    setError(null);
  }, []);

  return {
    uploadProgress,
    uploadPhase,
    error,
    startUpload,
    reset,
  };
};
