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
} from "@mui/material";
import { Logout as LogoutIcon } from "@mui/icons-material";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { getInitials } from "../../utils/stringUtils";
import { ThemeSwitcher } from "../common/ui/ThemeSwitcher";
import { LanguageSwitcher } from "../common/ui/LanguageSwitcher";
import { useTranslation } from "react-i18next";
import { MENU_ITEMS } from "../../constants/menuItems";
import { Logo } from "../common/ui/Logo";
import {APP_COLORS}  from "../../constants/colors";

const drawerWidth = 260;

export const Sidebar = () => {
  const {t} = useTranslation();

  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();  

  const visibleMenuItems = MENU_ITEMS.filter(item => 
    user?.role && item.allowedRoles.includes(user.role)
  );

  return (
    <Drawer
      variant="permanent"
      sx={{
        width: drawerWidth,
        flexShrink: 0,
        "& .MuiDrawer-paper": {
          width: drawerWidth,
          boxSizing: "border-box",
          bgcolor: "background.paper",
          borderRight: 1,
          borderColor: "divider",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
        },
      }}
    >
      <Box>
        {/* Felső rész: Logó */}
        <Box sx={{ p: 3, display: "flex", alignItems: "center", gap: 1.5 }}>
          {/* Alkalmazás logója*/}
          <Logo/>
          <Typography
            variant="h6"
            fontWeight="bold"
            sx={{ color: "text.primary" }}
          >
            Soccer Analyser
          </Typography>
        </Box>

        {/* Középső rész: Menüpontok */}
        <List sx={{ px: 2 }}>
          {visibleMenuItems.map((item) => {
            const isActive = location.pathname === item.path;

            return (
              <ListItem key={t(item.translationKey)} disablePadding sx={{ mb: 0.5 }}>
                <ListItemButton
                  onClick={() => navigate(item.path)}
                  sx={{
                    borderRadius: 2,
                    bgcolor: isActive ? APP_COLORS.sideBarButton.activeBackGround : "transparent",
                    color: isActive ? APP_COLORS.sideBarButton.active : "text.secondary",
                    "&:hover": {
                      bgcolor: isActive ? APP_COLORS.sideBarButton.activeHoverBackGround : "action.hover",
                    },
                  }}
                >
                  <ListItemIcon sx={{ color: "inherit", minWidth: 40 }}>
                    {item.icon}
                  </ListItemIcon>
                  <ListItemText
                    primary={t(item.translationKey)}
                    slotProps={{
                      primary: { fontWeight: isActive ? 600 : 400 },
                    }}
                  />
                </ListItemButton>
              </ListItem>
            );
          })}
        </List>
      </Box>

      <Box sx={{ display: "flex", flexDirection: "column" }}>
        {/* Témaváltónak doboz*/}
        <Box
          sx={{
            px: 2,
            py: 1,
            display: "flex",
            justifyContent: "flex-start",
          }}
        >
          {/*Témaváltó komponens*/}
          <ThemeSwitcher/>

          {/*Nyelvváltó komponens*/}
          <LanguageSwitcher/>
        </Box>

        {/* Profil és kijelentkezés */}
        <Box
          sx={{
            p: 2,
            borderTop: 1,
            borderColor: "divider",
            display: "flex",
            alignItems: "center",
            gap: 2,
          }}
        >
          <Avatar
            sx={{ bgcolor: "#0f766e", width: 40, height: 40, fontSize: "1rem" }}
          >
            {user && getInitials(user.name)}
          </Avatar>
          <Box sx={{ flexGrow: 1, overflow: "hidden" }}>
            <Typography variant="body2" fontWeight="bold" noWrap>
              {user?.name}
            </Typography>
            <Typography
              variant="caption"
              sx={{ color: "#10b981", textTransform: "capitalize" }}
              noWrap
            >
              {user?.role ? t(`roles.${user.role}`) : ''}
            </Typography>
          </Box>
          <IconButton
            onClick={logout}
            size="small"
            sx={{ color: "text.secondary" }}
          >
            <LogoutIcon fontSize="small" />
          </IconButton>
        </Box>
      </Box>
    </Drawer>
  );
};
