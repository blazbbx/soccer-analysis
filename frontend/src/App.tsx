import { useEffect, useState } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { Alert } from "@mui/material";
import { useTranslation } from "react-i18next";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { useAuth as useKeycloakAuth } from "react-oidc-context";
import { ROLES } from "./types/roles";
import { useQueryClient } from "@tanstack/react-query";
import { ViewRoute } from "./components/auth/ViewRoute";
import { MY_VIEWS_QUERY_KEY } from "./api/me";
import { MainLayout } from "./components/layout/Mainlayout";
import { Teams } from "./pages/teams/Teams";
import { Clubs } from "./pages/clubs/Clubs";
import { Cups } from "./pages/cups/Cups";
import { UserPage } from "./pages/user/UserPage";
import { Chat } from "./pages/chat/Chat";
import { AdminPage } from "./pages/admin/AdminPage";
import { MatchAnalyzer } from "./pages/matches/MatchAnalyzer";
import { Matches } from "./pages/matches/Matches";
import { DashBoard } from "./pages/dashboard/Dashboard";
import { ChildProfilePage } from "./pages/children/ChildProfilePage";
import { InviteAcceptPage } from "./pages/registration/InviteAcceptPage";
import { getInviteDetails } from "./api/children";
import { MY_CHILDREN_QUERY_KEY } from "./context/ChildrenContext";
import { Registration } from "./pages/registration/Registration";
import { Login } from "./pages/login/Login";
import { ClipViewer } from "./pages/clips/ClipViewer";
import { useAcceptInvite } from "./api/generated/team-invitations/team-invitations";
import { getGetMyTeamsQueryKey } from "./api/generated/teams/teams";
import { ApiErrorListener } from "./api/ApiErrorListener";

const PendingInviteHandler = () => {
  const auth = useKeycloakAuth();
  const queryClient = useQueryClient();
  const { mutate: acceptInvite } = useAcceptInvite();
  const { t } = useTranslation();
  const [signInAgain, setSignInAgain] = useState(false);

  useEffect(() => {
    if (!auth.isAuthenticated) return;
    const token = localStorage.getItem('pendingInviteToken');
    if (!token) return;
    localStorage.removeItem('pendingInviteToken');
    getInviteDetails(token)
      .then((invite) => {
        if (invite.inviteKind === "CHILD_LOGIN") {
          window.location.assign(`/registration?invitetoken=${encodeURIComponent(token)}`);
          return;
        }
        const needsDetails =
          (invite.inviteKind === "PARENT" && !invite.childFirstName) ||
          invite.inviteKind === "PLAYER_SELF";
        if (needsDetails) {
          window.location.assign(`/invite-accept?token=${encodeURIComponent(token)}`);
          return;
        }
        acceptInvite({ token }, {
          onSuccess: async () => {
            try {
              await auth.signinSilent();
            } catch {
              setSignInAgain(true);
              return;
            }
            queryClient.invalidateQueries({ queryKey: getGetMyTeamsQueryKey() });
            queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY });
            queryClient.invalidateQueries({ queryKey: MY_VIEWS_QUERY_KEY });
          },
        });
      })
      .catch(() => undefined);
  }, [auth.isAuthenticated, acceptInvite, queryClient, auth]);

  if (!signInAgain) return null;
  return (
    <Alert severity="error" sx={{ m: 2 }}>
      {t("registration.signin-again")}
    </Alert>
  );
};


export default function App() {
  return (
    <ErrorBoundary>
    <BrowserRouter>
      <ApiErrorListener />
      <PendingInviteHandler />
      <Routes>
        <Route path="/registration" element={<Registration/>}/>
        <Route path="/invite-accept" element={<InviteAcceptPage/>}/>
        <Route path="/login" element={<Login/>}/>

        <Route element={<MainLayout />}>
          <Route path="/" element={<DashBoard />} />
          <Route element={<ViewRoute allowedViews={[ROLES.PARENT]} />}>
            <Route path="/children/:profileId" element={<ChildProfilePage />} />
          </Route>

          

          <Route
            element={
              <ViewRoute
                allowedViews={[
                  ROLES.ADMIN,
                  ROLES.CLUB_ADMIN,
                  ROLES.COACH,
                  ROLES.PLAYER, ROLES.PARENT,
                  ROLES.FAN,
                ]}
              />
            }
          >
            <Route path="/matches" element={<Matches />} />
          </Route>

          <Route
            element={<ViewRoute allowedViews={[ROLES.ADMIN, ROLES.CLUB_ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN]} />}
          >
            <Route path="/matches/:id" element={<MatchAnalyzer />} />
          </Route>

          <Route
            element={<ViewRoute allowedViews={[ROLES.ADMIN, ROLES.CLUB_ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN]} />}
          >
            <Route path="/clip/:clipId" element={<ClipViewer />} />
          </Route>

          <Route
            element={
              <ViewRoute
                allowedViews={[ROLES.ADMIN, ROLES.CLUB_ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN]}
              />
            }
          >
            <Route path="/user" element={<UserPage />} />
          </Route>

          <Route element={<ViewRoute allowedViews={[ROLES.ADMIN, ROLES.CLUB_ADMIN]} />}>
            <Route path="/clubs" element={<Clubs />} />
          </Route>

          <Route
            element={
              <ViewRoute
                allowedViews={[ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT]}
              />
            }
          >
            <Route path="/teams" element={<Teams />} />
          </Route>

          <Route
            element={
              <ViewRoute
                allowedViews={[ROLES.ADMIN, ROLES.CLUB_ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT]}
              />
            }
          >
            <Route path="/chat" element={<Chat />} />
          </Route>

          <Route
            element={
              <ViewRoute
                allowedViews={[ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.PARENT, ROLES.FAN]}
              />
            }
          >
            <Route path="/cups" element={<Cups />} />
          </Route>

          <Route element={<ViewRoute allowedViews={[ROLES.ADMIN]} />}>
            <Route path="/admin" element={<AdminPage />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
    </ErrorBoundary>
  );
}
