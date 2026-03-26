import type { AuthResponse, LoginCredentials, RegisterData, User } from "../types/auth";
import { ROLES } from "../types/roles";
import type { IAuthService } from "./authService";

const USERS_STORAGE_KEY = "football_analysis_users";

export class MockAuthService implements IAuthService {
  // Felhasználók lekérése a localStorage-ból
  private getUsersFromStorage(): any[] {
    const data = localStorage.getItem(USERS_STORAGE_KEY);
    if (data) {
      return JSON.parse(data);
    }
    
    // Alapértelmezett tesztfelhasználók, ha még üres a tároló
    const defaultUsers = [
      { id: '1', email: 'coach@test.com', password: 'password123', name: 'Test Edző', role: ROLES.COACH },
      { id: '2', email: 'player@test.com', password: 'password123', name: 'Test Játékos', role: ROLES.PLAYER }
    ];
    this.saveUsersToStorage(defaultUsers);
    return defaultUsers;
  }

  // Felhasználók mentése a localStorage-ba
  private saveUsersToStorage(users: any[]): void {
    localStorage.setItem(USERS_STORAGE_KEY, JSON.stringify(users));
  }

  // --- Bejelentkezés ---
  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        const users = this.getUsersFromStorage();
        // Megkeressük az egyező email/jelszó párost
        const user = users.find(u => u.email === credentials.email && u.password === credentials.password);

        if (user) {
          // A jelszót nem küldjük vissza a frontendnek
          const { password, ...userWithoutPassword } = user;
          resolve({
            token: `mock-jwt-token-${user.id}`,
            user: userWithoutPassword as User
          });
        } else {
          reject(new Error("Hibás e-mail cím vagy jelszó!"));
        }
      }, 500);
    });
  }

  // --- Regisztráció ---
  async register(userData: RegisterData): Promise<AuthResponse> {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        const users = this.getUsersFromStorage();

        // Ellenőrizzük, hogy foglalt-e az e-mail
        if (users.some(u => u.email === userData.email)) {
          reject(new Error("Ez az e-mail cím már regisztrálva van!"));
          return;
        }

        // Új felhasználó létrehozása
        const newUser = {
          id: crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 15),
          email: userData.email,
          password: userData.password, 
          name: userData.name,
          role: userData.role || ROLES.FAN 
        };

        // Mentés
        users.push(newUser);
        this.saveUsersToStorage(users);

        // Automatikus bejelentkeztetés regisztráció után
        const { password, ...userWithoutPassword } = newUser;
        resolve({
          token: `mock-jwt-token-${newUser.id}`,
          user: userWithoutPassword as User
        });
      }, 500);
    });
  }
}