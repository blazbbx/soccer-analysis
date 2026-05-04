import { useGetClipById } from '../../../api/generated/clip-controller/clip-controller';
import type { ClipResponse } from '../../../api/generated/model';

const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED']);

export function useClipPolling(clip: ClipResponse): ClipResponse {
  const { data: polledData } = useGetClipById(clip.id!, {
    query: {
      enabled: !!clip.id,
      refetchInterval: (query) => {
        const status = (query.state.data as unknown as ClipResponse)?.renderStatus
          ?? clip.renderStatus;
        return TERMINAL_STATUSES.has(status ?? '') ? false : 3000;
      },
    },
  });

  return polledData ? (polledData as unknown as ClipResponse) : clip;
}
