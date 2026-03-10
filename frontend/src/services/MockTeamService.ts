import { TeamDto } from '../types/team';
import {ITeamService} from '../services/teamService'

export class MockTeamService implements ITeamService {
  // Ez a függvény szimulálja a backend /api/teams végpontját
  async getMyTeams(): Promise<TeamDto[]> {
    // Szimulálunk egy kis hálózati késleltetést, 
    // hogy lássuk majd a töltőképernyőt (Spinner) a felületen
    await new Promise(resolve => setTimeout(resolve, 800));

    // Összerakjuk a mock adatokat
    const mockTeams: TeamDto[] = [
      {
        id: 't1',
        name: 'FC United',
        shortName: 'FCU',
        coachName: 'Carlos Coach',
        formation: '4-3-3',
        stats: { played: 19, wins: 12, draws: 4, losses: 3, points: 40 },
        squad: [
          { id: 'p1', name: 'Pablo Player', position: 'Midfielder', number: 8 },
          { id: 'p2', name: 'Maria Martinez', position: 'Forward', number: 9 },
          { id: 'p3', name: 'James Wilson', position: 'Defender', number: 5 },
          { id: 'p4', name: 'Leo Santos', position: 'Winger', number: 11 },
          { id: 'p5', name: 'Kai Müller', position: 'Goalkeeper', number: 1 },
        ]
      },
      {
        id: 't2',
        name: 'City Eagles',
        shortName: 'CEA',
        coachName: 'Sam Roberts',
        formation: '4-4-2',
        stats: { played: 19, wins: 9, draws: 5, losses: 5, points: 32 },
        squad: [
          { id: 'p6', name: 'Alex Johnson', position: 'Striker', number: 10 }
        ]
      }
    ];

    return mockTeams;
  }
}
