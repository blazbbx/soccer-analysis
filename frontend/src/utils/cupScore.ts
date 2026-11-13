import type { CupMatchResponse, MatchResponse } from "../api/generated/model";

export type ScoreSuggestion = { home: number; away: number };

/**
 * 2.2: the analysed match's result, turned to the cup match's sides.
 * If the cup match's home team is the analysed match's away team, the score is swapped.
 * No suggestion when the analysed match has no result or the sides cannot be matched unambiguously.
 */
export const scoreFromAnalysedMatch = (
  cupMatch: CupMatchResponse,
  analysed: MatchResponse,
): ScoreSuggestion | null => {
  if (analysed.homeScore == null || analysed.awayScore == null) return null;
  const cupHome = cupMatch.homeTeam?.realTeam?.id;
  const cupAway = cupMatch.awayTeam?.realTeam?.id;
  const same = (cupHome != null && cupHome === analysed.homeTeamId) || (cupAway != null && cupAway === analysed.awayTeamId);
  const swapped = (cupHome != null && cupHome === analysed.awayTeamId) || (cupAway != null && cupAway === analysed.homeTeamId);
  if (same && !swapped) return { home: analysed.homeScore, away: analysed.awayScore };
  if (swapped && !same) return { home: analysed.awayScore, away: analysed.homeScore };
  return null;
};
