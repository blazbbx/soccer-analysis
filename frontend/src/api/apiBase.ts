/**
 * Resolves the Spring backend base URL.
 *
 * Priority:
 *   1. VITE_API_BASE_URL if set non-empty at build time (used for explicit
 *      domain-style deploys e.g. https://api.kixify.hu).
 *   2. Otherwise derived at runtime from window.location.hostname and the
 *      port from VITE_API_PORT (default 8080). This lets a single frontend
 *      image work on any host/IP (e.g. a uni-LAN box at http://10.0.0.50)
 *      without a rebuild.
 *
 * The runtime fallback assumes the Spring service is reachable on the same
 * host that serves the SPA. docker-compose.yml publishes it on 8080;
 * docker-compose-deploy.yml publishes 8086 and passes that as a build arg.
 */
export function getApiBaseUrl(): string {
  const fromEnv = import.meta.env.VITE_API_BASE_URL;
  if (fromEnv && fromEnv.length > 0) return fromEnv;
  const port = import.meta.env.VITE_API_PORT || '8080';
  // Same scheme as the page: https behind the deploy proxy, http in local development.
  return `${window.location.protocol}//${window.location.hostname}:${port}`;
}

/**
 * Resolves the Keycloak realm authority URL. Same priority rule, with the
 * port from VITE_KEYCLOAK_PORT (default 9080; the deploy compose passes 9085).
 * Default realm name comes from `VITE_KEYCLOAK_REALM` or `football-realm`.
 */
export function getKeycloakAuthority(): string {
  const fromEnv = import.meta.env.VITE_KEYCLOAK_AUTHORITY;
  if (fromEnv && fromEnv.length > 0) return fromEnv;
  const realm = import.meta.env.VITE_KEYCLOAK_REALM || 'football-realm';
  const port = import.meta.env.VITE_KEYCLOAK_PORT || '9080';
  return `${window.location.protocol}//${window.location.hostname}:${port}/realms/${realm}`;
}
