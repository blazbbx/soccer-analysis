export interface PlayerDto{
  id: string;
  name: string;
  position: string;
  number: number;
}

export interface TeamStatsDto {
  played: number;
  wins: number;
  draws: number;
  losses: number;
  points: number;
}

export interface TeamDto{
  id: string;
  name: string;
  shortName: string; 
  coachName: string;
  formation: string;
  stats: TeamStatsDto;
  squad: PlayerDto[];
}