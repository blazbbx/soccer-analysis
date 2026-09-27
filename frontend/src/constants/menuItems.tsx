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
import { ROLES } from "../types/roles";


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
    allowedRoles: [ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN],
  },
  {
    translationKey: "sidebar.matches",
    icon: <MatchesIcon />,
    path: "/matches",
    allowedRoles: [ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN],
  },
  {
    translationKey: "sidebar.teamchat",
    icon: <ChatIcon />,
    path: "/chat",
    allowedRoles: [ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT],
  },
  {
    translationKey: "sidebar.clubs",
    icon: <ClubsIcon />,
    path: "/clubs",
    allowedRoles: [ROLES.ADMIN],
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