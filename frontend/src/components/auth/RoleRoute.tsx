import { type Role } from '../../types/roles';
import { ViewRoute } from './ViewRoute';

interface RoleRouteProps {
  allowedRoles: Role[];
}

/** @deprecated Use ViewRoute. */
export const RoleRoute = ({ allowedRoles }: RoleRouteProps) => (
  <ViewRoute allowedViews={allowedRoles} />
);