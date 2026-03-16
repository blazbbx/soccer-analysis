import { CreateTeamRequest, TeamDto, UpdateTeamRequest } from "../types/team";
import { MockTeamService } from './MockTeamService';

export interface ITeamService {
    getMyTeams(): Promise<TeamDto[]>;
    createTeam(teamData: CreateTeamRequest): Promise<TeamDto>; 
    updateTeam(id: string, teamData: UpdateTeamRequest): Promise<TeamDto>;
    deleteTeam(id: string): Promise<void>;
}

export const teamService: ITeamService = new MockTeamService();