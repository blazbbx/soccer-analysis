import { useAuth } from "../context/AuthContext";
import {
  CreateTeamRequest,
  TeamDto,
  TeamStatsDto,
  UpdateTeamRequest,
} from "../types/team";
import { ITeamService } from "./teamService";
import {BaseService} from "./baseService"


const STORAGE_KEY = "football_analysis_teams";

const decodeToken = (token: string) => {
  try {
    const base64Url = token.split(".")[1]; // A középső rész a Payload
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      window
        .atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join(""),
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
};

export class MockTeamService extends BaseService implements ITeamService {
  private getTeamsFromStorage(): TeamDto[] {
    const data = localStorage.getItem(STORAGE_KEY);
    return data ? JSON.parse(data) : [];
  }

  private saveTeamsToStorage(teams: TeamDto[]): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(teams));
  }

  // --- LEKÉRÉS ---
  async getMyTeams(token: string): Promise<TeamDto[]> {
    const payload = this.decodeMockToken(token); // A BaseService-ből jön

    return new Promise((resolve) => {
      setTimeout(() => {
        const allTeams = this.getTeamsFromStorage();
        // Csak a saját csapatait látja
        const myTeams = allTeams.filter(t => t.coachId === payload.sub);
        resolve(myTeams);
      }, 500);
    });
  }

  // --- LÉTREHOZÁS ---
  async createTeam(teamData: CreateTeamRequest, token: string): Promise<TeamDto> {
    const payload = this.decodeMockToken(token);

    return new Promise((resolve) => {
      setTimeout(() => {
        const teams = this.getTeamsFromStorage();
        const newTeam: TeamDto = {
          id: crypto.randomUUID(),
          name: teamData.name,
          shortName: teamData.name.substring(0, 3).toUpperCase(),
          stats: { played: 0, wins: 0, draws: 0, losses: 0, points: 0 },
          squad: [],
          coachName: payload.name || "Edző",
          coachId: payload.sub,
        };
        teams.push(newTeam);
        this.saveTeamsToStorage(teams);
        resolve(newTeam);
      }, 500);
    });
  }

  // --- MÓDOSÍTÁS ---
  async updateTeam(id: string, teamData: UpdateTeamRequest, token: string): Promise<TeamDto> {
    const payload = this.decodeMockToken(token);

    return new Promise((resolve, reject) => {
      setTimeout(() => {
        const teams = this.getTeamsFromStorage();
        const teamIndex = teams.findIndex((t) => t.id === id);

        if (teamIndex === -1) return reject(new Error("Nincs ilyen csapat!"));
        
        // BIZTONSÁGI ELLENŐRZÉS: Csak a tulajdonos módosíthatja!
        if (teams[teamIndex].coachId !== payload.sub) {
          return reject(new Error("Nincs jogosultságod a csapat módosításához!"));
        }

        const team = { ...teams[teamIndex], ...teamData };
        // Statisztika újraszámolás... (marad a korábbi logikád szerint)
        team.stats.played = team.stats.wins + team.stats.draws + team.stats.losses;
        team.stats.points = team.stats.wins * 3 + team.stats.draws;

        teams[teamIndex] = team;
        this.saveTeamsToStorage(teams);
        resolve(team);
      }, 500);
    });
  }

  // --- TÖRLÉS ---
  async deleteTeam(id: string, token: string): Promise<void> {
    const payload = this.decodeMockToken(token);

    return new Promise((resolve, reject) => {
      setTimeout(() => {
        const teams = this.getTeamsFromStorage();
        const teamToDelete = teams.find(t => t.id === id);

        if (!teamToDelete) return reject(new Error("A csapat nem található!"));

        // BIZTONSÁGI ELLENŐRZÉS
        if (teamToDelete.coachId !== payload.sub) {
          return reject(new Error("Nincs jogosultságod a törléshez!"));
        }

        const filteredTeams = teams.filter((t) => t.id !== id);
        this.saveTeamsToStorage(filteredTeams);
        resolve();
      }, 500);
    });
  }
}
