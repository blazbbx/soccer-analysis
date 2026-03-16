import React from "react";
import {
  Dashboard as DashboardIcon,
  OndemandVideo as MatchesIcon,
  ChatBubbleOutline as ChatIcon,
  PeopleOutline as TeamsIcon,
  MovieEdit as MatchEditIcon,
} from "@mui/icons-material";
import { ROLES } from "../types/roles";

// Típus definíció a menüponthoz
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
    allowedRoles: [ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.FAN],
  },
  {
    translationKey: "sidebar.matches",
    icon: <MatchesIcon />,
    path: "/matches",
    allowedRoles: [ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.FAN],
  },
  {
    translationKey: "sidebar.teamchat",
    icon: <ChatIcon />,
    path: "/chat",
    allowedRoles: [ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.FAN],
  },
  {
    translationKey: "sidebar.teams",
    icon: <TeamsIcon />,
    path: "/teams",
    allowedRoles: [ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER],
  },
  {
    translationKey: "sidebar.matcheditor",
    icon: <MatchEditIcon />,
    path: "/matcheditor",
    allowedRoles: [ROLES.ADMIN, ROLES.COACH],
  },
];