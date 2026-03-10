import { type User } from '../types/auth';
import { type IAuthService } from './authService';
import { ROLES, type Role } from '../types/roles'; 

export class MockAuthService implements IAuthService {
  async login(email: string, password?: string): Promise<{ user: User; token: string }> {
    // Szimulálunk egy kis töltést
    await new Promise(resolve => setTimeout(resolve, 800));

    // Dinamikus Role meghatározás az email alapján
    let assignedRole: Role = ROLES.COACH; // Alapértelmezett

    const lowerEmail = email.toLowerCase();
    
    if (lowerEmail.includes('player')) {
      assignedRole = ROLES.PLAYER;
    } else if (lowerEmail.includes('admin')) {
      assignedRole = ROLES.ADMIN;
    } else if (lowerEmail.includes('coach')) {
      assignedRole = ROLES.COACH;
    }

    // Visszaadunk egy dinamikus usert a választott role-al
    return {
      user: { 
        id: Math.floor(Math.random() * 1000).toString(), 
        name: assignedRole.charAt(0).toUpperCase() + assignedRole.slice(1) + " User", 
        email: email, 
        role: assignedRole 
      },
      token: `mock-jwt-token-for-${assignedRole}`
    };
  }

  async logout(): Promise<void> {
    await new Promise(resolve => setTimeout(resolve, 300));
    console.log('Mock kijelentkezés sikeres.');
  }
}