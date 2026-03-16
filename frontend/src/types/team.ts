export interface PlayerDto{
  id: string;
  name: string;
  position: string;
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
  coachId: string;
}

export interface CreateTeamRequest {
  name: string;
  formation: string;
  wins: number;
  draws: number;
  losses: number;
  points: number;
  coachName: string;
}

export interface UpdateTeamRequest {
  name?: string;
  formation?: string;
  wins?: number;
  draws?: number;
  losses?: number;
}