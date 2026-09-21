/**
 * Resolves the Spring backend base URL.
 *
 * Priority:
 *   1. VITE_API_BASE_URL if set non-empty at build time (used for explicit
 *      domain-style deploys e.g. https://api.kixify.hu).
 *   2. Otherwise derived at runtime from window.location.hostname:
 *      `http://${hostname}:8080`. This lets a single frontend image work
 *      on any host/IP (e.g. a uni-LAN box at http://10.0.0.50) without
 *      a rebuild.
 *
 * The runtime fallback assumes the Spring service is reachable on port
 * 8080 of the same host that serves the SPA — which is what
 * docker-compose-deploy.yml publishes.
 */
export function getApiBaseUrl(): string {
  const fromEnv = import.meta.env.VITE_API_BASE_URL;
  if (fromEnv && fromEnv.length > 0) return fromEnv;
  return `http://${window.location.hostname}:8080`;
}

/**
 * Resolves the Keycloak realm authority URL. Same priority rule.
 * Default realm name comes from `VITE_KEYCLOAK_REALM` or `football-realm`.
 */
export function getKeycloakAuthority(): string {
  const fromEnv = import.meta.env.VITE_KEYCLOAK_AUTHORITY;
  if (fromEnv && fromEnv.length > 0) return fromEnv;
  const realm = import.meta.env.VITE_KEYCLOAK_REALM || 'football-realm';
  return `http://${window.location.hostname}:9080/realms/${realm}`;
}
