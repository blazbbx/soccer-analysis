import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { RoleRoute } from "./components/auth/RoleRoute";
import { MainLayout } from "./components/layout/Mainlayout";
import { ROLES } from "./types/roles";
import { Teams } from "./pages/Teams";
import { AdminPanel } from "./pages/AdminPanel";
import { MatchAnalyzer } from "./components/common/MatchesPageComps/MatchAnalyzer";
import { Matches } from "./pages/Matches";
import { DashBoard } from "./pages/DashBoard";
import { Registration } from "./pages/Registration";

//Az App, ami összefogja a routert
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<MainLayout />}>
          <Route path="/" element={<DashBoard />} />

          <Route path="/registration" element={<Registration/>}/>

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
            element={<RoleRoute allowedRoles={[ROLES.COACH, ROLES.ADMIN]} />}
          >
            <Route path="/matches/:id" element={<MatchAnalyzer />} />
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

          <Route element={<RoleRoute allowedRoles={[ROLES.ADMIN]} />}>
            <Route path="/adminpanel" element={<AdminPanel />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
