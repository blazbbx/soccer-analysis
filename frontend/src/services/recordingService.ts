import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  getClipById,
  useCompleteCompositionUpload,
  useCreateClipWithUploadLinks,
  useDeleteClip,
} from '../api/generated/clip-controller/clip-controller';
import { getGetClipsQueryKey } from '../api/generated/match-controller/match-controller';
import type { ClipCompositionUploadResponse, ClipResponse, ClipSyncEvent } from '../api/generated/model';

interface UploadClipParams {
  overlayBlob: Blob;
  audioBlob: Blob | null;
  syncData: ClipSyncEvent[];
  name: string;
  matchId: string;
}

export function useClipUpload() {
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const { mutateAsync: createWithLinks } = useCreateClipWithUploadLinks();
  const { mutateAsync: completeUpload } = useCompleteCompositionUpload();

  const uploadClip = async ({ overlayBlob, audioBlob, syncData, name, matchId }: UploadClipParams) => {
    setIsUploading(true);
    setError(null);

    try {
      const response = await createWithLinks({
        data: {
          matchId,
          name,
          syncData,
          overlayFilename: 'overlay.webm',
          audioFilename: 'audio.webm',
          timelineFilename: 'timeline.json',
        },
      });

      // orval types this as Blob but the actual payload is ClipCompositionUploadResponse
      const { clipId, overlayUploadUrl, audioUploadUrl, timelineUploadUrl } =
        response as unknown as ClipCompositionUploadResponse;

      if (!clipId || !overlayUploadUrl || !timelineUploadUrl) {
        throw new Error('Incomplete upload URLs in server response');
      }

      const uploads: Promise<Response>[] = [
        fetch(overlayUploadUrl, {
          method: 'PUT',
          body: overlayBlob,
          headers: { 'Content-Type': 'video/webm' },
        }),
        fetch(timelineUploadUrl, {
          method: 'PUT',
          body: JSON.stringify(syncData),
          headers: { 'Content-Type': 'application/json' },
        }),
      ];

      if (audioBlob && audioUploadUrl) {
        uploads.push(
          fetch(audioUploadUrl, {
            method: 'PUT',
            body: audioBlob,
            headers: { 'Content-Type': 'audio/webm' },
          })
        );
      }

      await Promise.all(uploads);
      await completeUpload({ clipId });
      queryClient.invalidateQueries({ queryKey: getGetClipsQueryKey(matchId) });
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Upload failed';
      setError(message);
      throw e;
    } finally {
      setIsUploading(false);
    }
  };

  return { uploadClip, isUploading, error };
}

export async function getClip(id: string): Promise<ClipResponse> {
  const result = await getClipById(id);
  return result as unknown as ClipResponse;
}

export function useClipDelete(matchId: string) {
  const queryClient = useQueryClient();
  const { mutateAsync: deleteClipAsync, isPending: isDeleting } = useDeleteClip();

  const deleteClip = async (clipId: string) => {
    await deleteClipAsync({ clipId });
    queryClient.invalidateQueries({ queryKey: getGetClipsQueryKey(matchId) });
  };

  return { deleteClip, isDeleting };
}
