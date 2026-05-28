import { useQuery } from '@tanstack/react-query';

import { getGetMatchQueryKey, getMatch } from '../../../api/generated/match-controller/match-controller';

export const useMatchWithPolling = (matchId: string | undefined | null) => {
  return useQuery({
    enabled: !!matchId,
    queryKey: getGetMatchQueryKey(matchId!),

    queryFn: () => getMatch(matchId!),
    
    refetchInterval: (query) => {
      const status = query.state.data?.overallStatus;

      // PREPROCESSING is in the polling set so the chip can flip to "Field selection
      // (clickable)" the moment the Python worker finishes — needed when the user is
      // viewing the list with no live SSE connection (closed dialog, browser refresh,
      // or just opened the page on another tab).
      const isInFlight =
        status === 'PROCESSING' ||
        status === 'UPLOADING' ||
        status === 'PREPROCESSING';

      return isInFlight ? 3000 : false;
    },
  });
};