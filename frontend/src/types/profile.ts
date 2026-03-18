interface KeycloakProfile {
  sub?: string;
  email?: string;
  name?: string;
  preferred_username?: string;
  given_name?: string;   
  family_name?: string;
  realm_access?: {
    roles: string[];
  };
}