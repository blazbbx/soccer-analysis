import { CreateTeamRequest, TeamDto } from "../types/team";
import { MockTeamService } from './MockTeamService';

export interface ITeamService {
    getMyTeams(): Promise<TeamDto[]>;
    createTeam(teamData: CreateTeamRequest): Promise<TeamDto>; 
}

export const teamService: ITeamService = new MockTeamService();