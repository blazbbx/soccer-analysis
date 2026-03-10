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
import {
  Dashboard as DashboardIcon,
  OndemandVideo as MatchesIcon,
  ChatBubbleOutline as ChatIcon,
  PeopleOutline as TeamsIcon,
  Logout as LogoutIcon,
  LightMode as LightModeIcon,
  DarkMode as DarkmodeIcon,
} from "@mui/icons-material";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { getInitials } from "../../utils/stringUtils";
import { useColorMode } from "../../context/ThemeContext";

const drawerWidth = 260;

export const Sidebar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { mode, toggleColorMode } = useColorMode();

  // A menüpontok konfigurációja
  const menuItems = [
    { text: "Dashboard", icon: <DashboardIcon />, path: "/" },
    { text: "Matches", icon: <MatchesIcon />, path: "/matches" },
    { text: "Team Chat", icon: <ChatIcon />, path: "/chat" },
    { text: "Teams", icon: <TeamsIcon />, path: "/teams" },
  ];

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
          <Box
            sx={{
              bgcolor: "#10b981",
              color: "#fff",
              p: 0.25,              
              display: "flex",
            }}
          >            
            <Box
              component="img"
              sx={{
                height: 42, 
                width: 42,
                cursor: "pointer", 
              }}              
              src="/logo.png"
            />
          </Box>
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
          {menuItems.map((item) => {
            const isActive = location.pathname === item.path;

            return (
              <ListItem key={item.text} disablePadding sx={{ mb: 0.5 }}>
                <ListItemButton
                  onClick={() => navigate(item.path)}
                  sx={{
                    borderRadius: 2,
                    bgcolor: isActive
                      ? "rgba(16, 185, 129, 0.1)"
                      : "transparent",
                    color: isActive ? "#10b981" : "text.secondary",
                    "&:hover": {
                      bgcolor: isActive
                        ? "rgba(16, 185, 129, 0.15)"
                        : "action.hover",
                    },
                  }}
                >
                  <ListItemIcon
                    sx={{
                      color: "inherit",
                      minWidth: 40,
                    }}
                  >
                    {item.icon}
                  </ListItemIcon>
                  <ListItemText
                    primary={item.text}
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
        {/* Témaváltó kis doboza */}
        <Box
          sx={{
            px: 2,
            py: 1,
            display: "flex",
            justifyContent: "flex-start",
          }}
        >
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1,
              p: 0.5,
              borderRadius: 2,
              bgcolor: "action.hover",
              border: 1,
              borderColor: "divider",
            }}
          >
            <Typography
              variant="caption"
              sx={{ pl: 1, color: "text.secondary", fontWeight: 500 }}
            >
              {mode === "dark" ? "Light Mode" : "Dark Mode"}
            </Typography>
            <IconButton
              onClick={toggleColorMode}
              size="small"
              sx={{ color: "text.secondary" }}
            >
              {mode === "dark" ? (
                <LightModeIcon fontSize="small" />
              ) : (
                <DarkmodeIcon fontSize="small" />
              )}
            </IconButton>
          </Box>
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
              {user?.role}
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
