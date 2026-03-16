import { CreateTeamRequest, TeamDto, TeamStatsDto, UpdateTeamRequest } from "../types/team";
import { ITeamService } from "./teamService";

const STORAGE_KEY = "football_analysis_teams";

export class MockTeamService implements ITeamService{
  // Belső segédfüggvény az adatok lekérésére
  private getTeamsFromStorage(): TeamDto[] {
    const data = localStorage.getItem(STORAGE_KEY);
    if (data) {
      return JSON.parse(data);
    }
    return [];
  }

  // Belső segédfüggvény az adatok mentésére
  private saveTeamsToStorage(teams: TeamDto[]): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(teams));
  }

  private getCurrentUser(): any {
    const userData = localStorage.getItem('user');
    return userData ? JSON.parse(userData) : null;
  }

  // Csapatok lekérdezése
  async getMyTeams(): Promise<TeamDto[]> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const currentUser = this.getCurrentUser();
        if (!currentUser) {
          resolve([]); 
          return;
        }

        const allTeams = this.getTeamsFromStorage();
        
        // Leszűrjük a csapatokat a kapott userId alapján
        const myTeams = allTeams.filter((team: TeamDto) => team.coachId === currentUser.id);

        resolve(myTeams);
      }, 500); // 500ms hálózati késleltetés szimulálása
    });
  }

  // Új csapat létrehozása
  async createTeam(teamData: CreateTeamRequest): Promise<TeamDto> {
    return new Promise((resolve,reject) => {
      setTimeout(() => {
        const currentUser = this.getCurrentUser();
        if (!currentUser) {
          reject(new Error("Nem vagy bejelentkezve!"));
          return;
        }

        const teams = this.getTeamsFromStorage();
        
        // Kiszámoljuk a lejátszott meccseket
        const played = (teamData.wins || 0) + (teamData.draws || 0) + (teamData.losses || 0);
        
        // Összeállítjuk a statisztikát
        const stats: TeamStatsDto = {
          played: played,
          wins: teamData.wins || 0,
          draws: teamData.draws || 0,
          losses: teamData.losses || 0,
          points: teamData.wins*3+teamData.draws,
        };

        // Létrehozzuk az új csapat objektumot a TeamDto alapján
        const newTeam: TeamDto = {
          id: crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 15),
          name: teamData.name,
          shortName: teamData.name ? teamData.name.substring(0, 3).toUpperCase() : "UNK",
          formation: teamData.formation,
          stats: stats,
          squad: [], // Üres játékoskerettel indul
          coachName: currentUser.name,
          coachId: currentUser.id
        };

        // Hozzáadjuk a listához és mentjük a localStorage-ba
        teams.push(newTeam);
        this.saveTeamsToStorage(teams);
        
        resolve(newTeam);
      }, 500);
    });
  }

  async updateTeam(id: string, teamData: UpdateTeamRequest): Promise<TeamDto> {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        const teams = this.getTeamsFromStorage();
        const teamIndex = teams.findIndex((t) => t.id === id);

        if (teamIndex === -1) {
          reject(new Error("A csapat nem található!"));
          return;
        }

        const team = teams[teamIndex];

        // Értékek frissítése, ha kaptunk újat
        if (teamData.name !== undefined) {
          team.name = teamData.name;
          team.shortName = teamData.name.substring(0, 3).toUpperCase();
        }
        if (teamData.formation !== undefined) {
          team.formation = teamData.formation;
        }

        // Statisztikák frissítése
        if (teamData.wins !== undefined) team.stats.wins = teamData.wins;
        if (teamData.draws !== undefined) team.stats.draws = teamData.draws;
        if (teamData.losses !== undefined) team.stats.losses = teamData.losses;

        // Számított mezők (played, points) újraszámolása
        team.stats.played = team.stats.wins + team.stats.draws + team.stats.losses;
        team.stats.points = team.stats.wins * 3 + team.stats.draws;

        teams[teamIndex] = team;
        this.saveTeamsToStorage(teams);

        resolve(team);
      }, 500);
    });
  }

  async deleteTeam(id: string): Promise<void> {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        const teams = this.getTeamsFromStorage();
        const initialLength = teams.length;
        
        // Kiszűrjük a törlendő csapatot
        const filteredTeams = teams.filter(t => t.id !== id);
        
        if (initialLength === filteredTeams.length) {
          reject(new Error("A csapat nem található!"));
          return;
        }

        this.saveTeamsToStorage(filteredTeams);
        resolve();
      }, 500);
    });
  }
}

