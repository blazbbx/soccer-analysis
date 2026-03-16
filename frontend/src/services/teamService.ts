import { TeamDto } from "../types/team";
import { MockTeamService} from '../services/MockTeamService'

export interface ITeamService{
    getMyTeams(): Promise<TeamDto[]> 
}

export const teamService = new MockTeamService();