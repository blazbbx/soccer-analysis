import { AuthResponse, LoginCredentials, RegisterData } from "../types/auth";
import { MockAuthService } from "./MockAuthService";

export interface IAuthService {
    login(credentials: LoginCredentials): Promise<AuthResponse>;
    register(userData: RegisterData): Promise<AuthResponse>; 
}

export const authService: IAuthService = new MockAuthService();