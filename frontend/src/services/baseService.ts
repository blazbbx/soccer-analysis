import { useAuth } from "../context/AuthContext";

export abstract class BaseService {
  // Minden hívásnál ezt fogjuk használni a token kinyerésére
  protected getAuthHeader(token: string | null): string {
    if (!token) throw new Error("Nincs érvényes munkamenet!");
    return `Bearer ${token}`;
  }

  // Segédfüggvény a token dekódolásához (a mock backend szimuláláshoz)
  protected decodeMockToken(authHeader: string) {
    const token = authHeader.replace('Bearer ', '');
    const base64Url = token.split('.')[1];
    return JSON.parse(window.atob(base64Url));
  }
}