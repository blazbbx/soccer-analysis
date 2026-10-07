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
  Menu,
  MenuItem,
} from "@mui/material";
import {
  Logout as LogoutIcon,
  ChevronLeft as ChevronLeftIcon,
  Menu as MenuIcon,
  UnfoldMore as UnfoldMoreIcon,
} from "@mui/icons-material";
import { useLocation, useNavigate } from "react-router-dom";
import { useState, type MouseEvent, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../../context/AuthContext";
import { useChildren } from "../../context/ChildrenContext";
import { useView } from "../../context/ViewContext";
import { formatPersonName, getInitials } from "../../utils/stringUtils";
import { ThemeSwitcher } from "../ui/ThemeSwitcher";
import { LanguageSwitcher } from "../ui/LanguageSwitcher";
import { useTranslation } from "react-i18next";
import { MENU_ITEMS, pathAllowedForView } from "../../constants/menuItems";
import { Logo } from "../ui/Logo";
import { APP_COLORS } from "../../constants/colors";
import { childClubAndTeamLabel, type ChildResponse } from "../../api/children";
import { getMeViews, membershipCountForHat, MY_VIEWS_QUERY_KEY } from "../../api/me";
import { HAT_RANK, ROLES, type Role } from "../../types/roles";

const DRAWER_WIDTH = 260;
const COLLAPSED_WIDTH = 120;

const identityCardSx = (collapsed: boolean, active: boolean) => ({
  p: collapsed ? 1 : 2,
  borderTop: 1,
  borderColor: "divider",
  display: "flex",
  alignItems: "center",
  justifyContent: collapsed ? "center" : "flex-start",
  gap: 1.5,
  cursor: "pointer",
  bgcolor: active ? APP_COLORS.sideBarButton.activeBackGround : "transparent",
  "&:hover": { bgcolor: active ? APP_COLORS.sideBarButton.activeHoverBackGround : "action.hover" },
  transition: "background-color 0.2s ease",
});

const IdentityCard = ({
  collapsed,
  name,
  caption,
  active,
  onClick,
  trailing,
}: {
  collapsed: boolean;
  name: string;
  caption: string;
  active: boolean;
  onClick: (event: MouseEvent<HTMLElement>) => void;
  trailing?: ReactNode;
}) => (
  <Box onClick={onClick} sx={identityCardSx(collapsed, active)}>
    <Tooltip title={collapsed ? name : ""} placement="right">
      <Avatar sx={{ bgcolor: "#0f766e", width: 40, height: 40, fontSize: "1rem", flexShrink: 0 }}>
        {getInitials(name)}
      </Avatar>
    </Tooltip>
    {!collapsed && (
      <>
        <Box sx={{ flexGrow: 1, overflow: "hidden" }}>
          <Typography variant="body2" fontWeight="bold" noWrap>
            {name}
          </Typography>
          <Typography
            variant="caption"
            sx={{ color: "#10b981", textTransform: "capitalize" }}
            noWrap
          >
            {caption}
          </Typography>
        </Box>
        {trailing}
      </>
    )}
  </Box>
);

export const Sidebar = () => {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const { hats, activeView, setActiveView } = useView();
  const { childrenList, selected: selectedChild, select } = useChildren();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [childMenuAnchor, setChildMenuAnchor] = useState<HTMLElement | null>(null);
  const [hatMenuAnchor, setHatMenuAnchor] = useState<HTMLElement | null>(null);
  const { data: views } = useQuery({
    queryKey: MY_VIEWS_QUERY_KEY,
    queryFn: getMeViews,
    enabled: Boolean(user),
  });

  const orderedHats = HAT_RANK.filter((hat) => hats.includes(hat));
  const canSwitchHats = orderedHats.length > 1;
  const showChildCard = activeView === ROLES.PARENT && Boolean(selectedChild);

  const visibleMenuItems = MENU_ITEMS.filter((item) =>
    activeView != null && item.allowedRoles.includes(activeView),
  );

  const chooseHat = (hat: Role) => {
    setActiveView(hat);
    setHatMenuAnchor(null);
    if (!pathAllowedForView(location.pathname, hat)) {
      navigate("/");
    }
  };

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
              <Typography
                sx={{
                  fontWeight: 900,
                  fontSize: "1.5rem",
                  letterSpacing: "0.08em",
                  textTransform: "uppercase",
                  color: "text.primary",
                  whiteSpace: "nowrap",
                  fontStyle: "italic",
                  lineHeight: 1,
                }}
              >
                Kixify
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

        {showChildCard && selectedChild && (
          <IdentityCard
            collapsed={collapsed}
            name={formatPersonName(selectedChild.firstName, selectedChild.lastName)}
            caption={t("children.role")}
            active={location.pathname === `/children/${selectedChild.profileId}`}
            onClick={(event) => {
              if (collapsed && childrenList.length > 1) {
                setChildMenuAnchor(event.currentTarget);
                return;
              }
              navigate(`/children/${selectedChild.profileId}`);
            }}
            trailing={
              childrenList.length > 1 ? (
                <Tooltip title={t("children.switch")} placement="right">
                  <Box
                    onClick={(event: MouseEvent<HTMLDivElement>) => {
                      event.stopPropagation();
                      setChildMenuAnchor(event.currentTarget);
                    }}
                    role="button"
                    aria-label={t("children.switch")}
                    sx={{
                      width: 40,
                      height: 40,
                      flexShrink: 0,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      borderRadius: 1.5,
                      color: APP_COLORS.sideBarButton.active,
                      bgcolor: APP_COLORS.sideBarButton.activeBackGround,
                      border: "1px solid",
                      borderColor: "rgba(16, 185, 129, 0.28)",
                      transition: "background-color 0.2s ease",
                      "&:hover": {
                        bgcolor: APP_COLORS.sideBarButton.activeHoverBackGround,
                      },
                    }}
                  >
                    <UnfoldMoreIcon sx={{ fontSize: 22 }} />
                  </Box>
                </Tooltip>
              ) : undefined
            }
          />
        )}

        <IdentityCard
          collapsed={collapsed}
          name={user?.firstName || user?.lastName ? formatPersonName(user?.firstName, user?.lastName) : (user?.name ?? "")}
          caption={activeView ? t(`roles.${activeView}`) : ""}
          active={location.pathname === "/user"}
          onClick={(event) => {
            if (collapsed && canSwitchHats) {
              setHatMenuAnchor(event.currentTarget);
              return;
            }
            navigate("/user");
          }}
          trailing={
            <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, flexShrink: 0 }}>
              {canSwitchHats ? (
                <Tooltip title={t("sidebar.switch-view")} placement="right">
                  <Box
                    onClick={(event: MouseEvent<HTMLDivElement>) => {
                      event.stopPropagation();
                      setHatMenuAnchor(event.currentTarget);
                    }}
                    role="button"
                    aria-label={t("sidebar.switch-view")}
                    sx={{
                      width: 40,
                      height: 40,
                      flexShrink: 0,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      borderRadius: 1.5,
                      color: APP_COLORS.sideBarButton.active,
                      bgcolor: APP_COLORS.sideBarButton.activeBackGround,
                      border: "1px solid",
                      borderColor: "rgba(16, 185, 129, 0.28)",
                      transition: "background-color 0.2s ease",
                      "&:hover": {
                        bgcolor: APP_COLORS.sideBarButton.activeHoverBackGround,
                      },
                    }}
                  >
                    <UnfoldMoreIcon sx={{ fontSize: 22 }} />
                  </Box>
                </Tooltip>
              ) : null}
              <Tooltip title={t("sidebar.logout")} placement="right">
                <IconButton
                  onClick={(e) => {
                    e.stopPropagation();
                    logout();
                  }}
                  size="small"
                  sx={{ color: "text.secondary", flexShrink: 0 }}
                >
                  <LogoutIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Box>
          }
        />
      </Box>
      <Menu
        anchorEl={childMenuAnchor}
        open={Boolean(childMenuAnchor)}
        onClose={() => setChildMenuAnchor(null)}
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
        transformOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        {childrenList.map((child: ChildResponse) => {
          const name = formatPersonName(child.firstName, child.lastName);
          return (
            <MenuItem
              key={child.profileId}
              selected={child.profileId === selectedChild?.profileId}
              onClick={() => {
                select(child.profileId);
                setChildMenuAnchor(null);
                if (location.pathname.startsWith("/children/")) {
                  navigate(`/children/${child.profileId}`);
                }
              }}
            >
              <ListItemText
                primary={name}
                secondary={
                  childClubAndTeamLabel(child) || t("children.no-team")
                }
              />
            </MenuItem>
          );
        })}
      </Menu>
      <Menu
        anchorEl={hatMenuAnchor}
        open={Boolean(hatMenuAnchor)}
        onClose={() => setHatMenuAnchor(null)}
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
        transformOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        {orderedHats.map((hat) => {
          const count = membershipCountForHat(hat, views);
          return (
            <MenuItem
              key={hat}
              selected={hat === activeView}
              onClick={() => chooseHat(hat)}
            >
              <ListItemText
                primary={t(`roles.${hat}`)}
                secondary={
                  count === undefined
                    ? undefined
                    : t(`views.membership.${hat}`, { count })
                }
              />
            </MenuItem>
          );
        })}
      </Menu>
    </Drawer>
  );
};
