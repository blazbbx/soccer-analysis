import { CreateTeamRequest, TeamDto, UpdateTeamRequest } from "../types/team";
import { MockTeamService } from './MockTeamService';

export interface ITeamService {
    getMyTeams(token: string|null): Promise<TeamDto[]>;
    createTeam(teamData: CreateTeamRequest, token: string): Promise<TeamDto>; 
    updateTeam(id: string, teamData: UpdateTeamRequest, token: string): Promise<TeamDto>;
    deleteTeam(id: string, token: string): Promise<void>;
}

export const teamService: ITeamService = new MockTeamService();