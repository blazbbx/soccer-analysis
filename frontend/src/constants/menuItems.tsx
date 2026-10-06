import React from "react";
import {
  Dashboard as DashboardIcon,
  OndemandVideo as MatchesIcon,
  ChatBubbleOutline as ChatIcon,
  PeopleOutline as TeamsIcon,
  AdminPanelSettings as AdminIcon,
  EmojiEvents as CupsIcon,
  Business as ClubsIcon,
} from "@mui/icons-material";
import { ROLES, type Role } from "../types/roles";


export interface MenuItemConfig {
  translationKey: string;
  icon: React.ReactNode;
  path: string;
  allowedRoles: string[];
}

export const MENU_ITEMS: MenuItemConfig[] = [
  {
    translationKey: "sidebar.dashboard",
    icon: <DashboardIcon />,
    path: "/",
    allowedRoles: [ROLES.ADMIN, ROLES.CLUB_ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN],
  },
  {
    translationKey: "sidebar.matches",
    icon: <MatchesIcon />,
    path: "/matches",
    allowedRoles: [ROLES.ADMIN, ROLES.CLUB_ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN],
  },
  {
    translationKey: "sidebar.teamchat",
    icon: <ChatIcon />,
    path: "/chat",
    allowedRoles: [ROLES.ADMIN, ROLES.CLUB_ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT],
  },
  {
    translationKey: "sidebar.clubs",
    icon: <ClubsIcon />,
    path: "/clubs",
    allowedRoles: [ROLES.ADMIN, ROLES.CLUB_ADMIN],
  },
  {
    translationKey: "sidebar.teams",
    icon: <TeamsIcon />,
    path: "/teams",
    allowedRoles: [ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT],
  },
  {
    translationKey: "sidebar.cups",
    icon: <CupsIcon />,
    path: "/cups",
    allowedRoles: [ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN],
  },
  {
    translationKey: "sidebar.admin",
    icon: <AdminIcon />,
    path: "/admin",
    allowedRoles: [ROLES.ADMIN],
  },
];

export const pathAllowedForView = (pathname: string, view: Role): boolean => {
  if (pathname === "/" || pathname === "/user") return true;
  if (pathname.startsWith("/children/")) return view === ROLES.PARENT;
  if (pathname.startsWith("/clip/")) {
    const clipViews: Role[] = [ROLES.ADMIN, ROLES.CLUB_ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN];
    return clipViews.includes(view);
  }
  const item = MENU_ITEMS.find(
    (entry) => pathname === entry.path || pathname.startsWith(`${entry.path}/`),
  );
  if (!item) return true;
  return item.allowedRoles.includes(view);
};