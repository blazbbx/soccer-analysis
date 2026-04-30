import { useEffect } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { useAuth as useKeycloakAuth } from "react-oidc-context";
import { useQueryClient } from "@tanstack/react-query";
import { RoleRoute } from "./components/auth/RoleRoute";
import { MainLayout } from "./components/layout/Mainlayout";
import { ROLES } from "./types/roles";
import { Teams } from "./pages/teams/Teams";
import { UserPage } from "./pages/user/UserPage";
import { Chat } from "./pages/chat/Chat";
import { MatchAnalyzer } from "./pages/matches/MatchAnalyzer";
import { Matches } from "./pages/matches/Matches";
import { DashBoard } from "./pages/dashboard/Dashboard";
import { Registration } from "./pages/registration/Registration";
import { Login } from "./pages/login/Login";
import { useAcceptInvite } from "./api/generated/team-invitations/team-invitations";
import { getGetAllTeamsQueryKey } from "./api/generated/teams/teams";

const PendingInviteHandler = () => {
  const auth = useKeycloakAuth();
  const queryClient = useQueryClient();
  const { mutate: acceptInvite } = useAcceptInvite();

  useEffect(() => {
    if (!auth.isAuthenticated) return;
    const token = localStorage.getItem('pendingInviteToken');
    if (!token) return;
    localStorage.removeItem('pendingInviteToken');
    acceptInvite({ token }, {
      onSuccess: () => queryClient.invalidateQueries({ queryKey: getGetAllTeamsQueryKey() }),
    });
  }, [auth.isAuthenticated, acceptInvite, queryClient]);

  return null;
};


export default function App() {
  return (
    <BrowserRouter>
      <PendingInviteHandler />
      <Routes>
        <Route path="/registration" element={<Registration/>}/>
        <Route path="/login" element={<Login/>}/>

        <Route element={<MainLayout />}>
          <Route path="/" element={<DashBoard />} />

          

          <Route
            element={
              <RoleRoute
                allowedRoles={[
                  ROLES.ADMIN,
                  ROLES.COACH,
                  ROLES.PLAYER,
                  ROLES.FAN,
                ]}
              />
            }
          >
            <Route path="/matches" element={<Matches />} />
          </Route>

          <Route
            element={<RoleRoute allowedRoles={[ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.FAN]} />}
          >
            <Route path="/matches/:id" element={<MatchAnalyzer />} />
          </Route>

          <Route
            element={
              <RoleRoute
                allowedRoles={[ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.FAN]}
              />
            }
          >
            <Route path="/user" element={<UserPage />} />
          </Route>

          <Route
            element={
              <RoleRoute
                allowedRoles={[ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER]}
              />
            }
          >
            <Route path="/teams" element={<Teams />} />
          </Route>

          <Route
            element={
              <RoleRoute
                allowedRoles={[ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER]}
              />
            }
          >
            <Route path="/chat" element={<Chat />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
