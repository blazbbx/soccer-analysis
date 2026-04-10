import { useQuery } from '@tanstack/react-query';

import { getGetMatchQueryKey, getMatch } from '../../api/generated/match-controller/match-controller';

export const useMatchWithPolling = (matchId: string | undefined | null) => {
  return useQuery({
    enabled: !!matchId,
    queryKey: getGetMatchQueryKey(matchId!),

    queryFn: () => getMatch(matchId!),
    
    refetchInterval: (query) => {
      const status = query.state.data?.overallStatus;
      
      const isProcessing = status === 'UPLOADED' || status === 'PROCESSING';
      
      return isProcessing ? 3000 : false;
    },
  });
};