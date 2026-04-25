import {
  Box,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
  Avatar,
  IconButton,
  Tooltip,
} from "@mui/material";
import {
  Logout as LogoutIcon,
  ChevronLeft as ChevronLeftIcon,
  Menu as MenuIcon,
} from "@mui/icons-material";
import { useLocation, useNavigate } from "react-router-dom";
import { useState } from "react";
import { useAuth } from "../../context/AuthContext";
import { getInitials } from "../../utils/stringUtils";
import { ThemeSwitcher } from "../common/ui/ThemeSwitcher";
import { LanguageSwitcher } from "../common/ui/LanguageSwitcher";
import { useTranslation } from "react-i18next";
import { MENU_ITEMS } from "../../constants/menuItems";
import { Logo } from "../common/ui/Logo";
import { APP_COLORS } from "../../constants/colors";

const DRAWER_WIDTH = 260;
const COLLAPSED_WIDTH = 120;

export const Sidebar = () => {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);

  const visibleMenuItems = MENU_ITEMS.filter(
    (item) => user?.role && item.allowedRoles.includes(user.role)
  );

  const currentWidth = collapsed ? COLLAPSED_WIDTH : DRAWER_WIDTH;

  return (
    <Drawer
      variant="permanent"
      sx={{
        width: currentWidth,
        flexShrink: 0,
        transition: "width 0.25s ease",
        "& .MuiDrawer-paper": {
          width: currentWidth,
          boxSizing: "border-box",
          bgcolor: "background.paper",
          borderRight: 1,
          borderColor: "divider",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          overflowX: "hidden",
          transition: "width 0.25s ease",
        },
      }}
    >
      <Box>
        {/* Logo + toggle */}
        <Box
          sx={{
            px: collapsed ? 1 : 2,
            py: 1.5,
            display: "flex",
            alignItems: "center",
            justifyContent: collapsed ? "center" : "space-between",
            gap: 1,
          }}
        >
          {!collapsed && (
            <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
              <Logo />
              <Typography variant="h6" fontWeight="bold" sx={{ color: "text.primary", whiteSpace: "nowrap" }}>
                Soccer Analyser
              </Typography>
            </Box>
          )}

          {collapsed && <Logo />}

          <IconButton
            onClick={() => setCollapsed((prev) => !prev)}
            size="small"
            sx={{ color: "text.secondary", flexShrink: 0 }}
          >
            {collapsed ? <MenuIcon fontSize="small" /> : <ChevronLeftIcon fontSize="small" />}
          </IconButton>
        </Box>

        {/* Menu items */}
        <List sx={{ px: collapsed ? 0.5 : 2 }}>
          {visibleMenuItems.map((item) => {
            const isActive = location.pathname === item.path;

            return (
              <ListItem key={t(item.translationKey)} disablePadding sx={{ mb: 0.5 }}>
                <Tooltip title={collapsed ? t(item.translationKey) : ""} placement="right">
                  <ListItemButton
                    onClick={() => navigate(item.path)}
                    sx={{
                      borderRadius: 2,
                      justifyContent: collapsed ? "center" : "flex-start",
                      px: collapsed ? 1 : 2,
                      bgcolor: isActive ? APP_COLORS.sideBarButton.activeBackGround : "transparent",
                      color: isActive ? APP_COLORS.sideBarButton.active : "text.secondary",
                      "&:hover": {
                        bgcolor: isActive
                          ? APP_COLORS.sideBarButton.activeHoverBackGround
                          : "action.hover",
                      },
                    }}
                  >
                    <ListItemIcon
                      sx={{
                        color: "inherit",
                        minWidth: collapsed ? 0 : 40,
                        justifyContent: "center",
                      }}
                    >
                      {item.icon}
                    </ListItemIcon>
                    {!collapsed && (
                      <ListItemText
                        primary={t(item.translationKey)}
                        slotProps={{
                          primary: { fontWeight: isActive ? 600 : 400 },
                        }}
                      />
                    )}
                  </ListItemButton>
                </Tooltip>
              </ListItem>
            );
          })}
        </List>
      </Box>

      <Box sx={{ display: "flex", flexDirection: "column" }}>
        {/* Theme / language switchers */}
        {!collapsed && (
          <Box sx={{ px: 2, py: 1, display: "flex", justifyContent: "flex-start" }}>
            <ThemeSwitcher />
            <LanguageSwitcher />
          </Box>
        )}

        {/* Profile */}
        <Box
          sx={{
            p: collapsed ? 1 : 2,
            borderTop: 1,
            borderColor: "divider",
            display: "flex",
            alignItems: "center",
            justifyContent: collapsed ? "center" : "flex-start",
            gap: 2,
          }}
        >
          <Tooltip title={collapsed ? (user?.name ?? "") : ""} placement="right">
            <Avatar sx={{ bgcolor: "#0f766e", width: 40, height: 40, fontSize: "1rem", flexShrink: 0 }}>
              {user && getInitials(user.name)}
            </Avatar>
          </Tooltip>

          {!collapsed && (
            <>
              <Box sx={{ flexGrow: 1, overflow: "hidden" }}>
                <Typography variant="body2" fontWeight="bold" noWrap>
                  {user?.name}
                </Typography>
                <Typography
                  variant="caption"
                  sx={{ color: "#10b981", textTransform: "capitalize" }}
                  noWrap
                >
                  {user?.role ? t(`roles.${user.role}`) : ""}
                </Typography>
              </Box>
              <IconButton onClick={logout} size="small" sx={{ color: "text.secondary" }}>
                <LogoutIcon fontSize="small" />
              </IconButton>
            </>
          )}
        </Box>
      </Box>
    </Drawer>
  );
};
