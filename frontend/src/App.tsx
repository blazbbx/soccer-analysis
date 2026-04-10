import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { RoleRoute } from "./components/auth/RoleRoute";
import { MainLayout } from "./components/layout/Mainlayout";
import { ROLES } from "./types/roles";
import { Teams } from "./pages/Teams";
import { MatchAnalyzer } from "./components/common/MatchesPageComps/MatchAnalyzer";
import { Matches } from "./pages/Matches";
import { DashBoard } from "./pages/DashBoard";
import { Registration } from "./pages/Registration";
import { Login } from "./pages/Login";


export default function App() {
  return (
    <BrowserRouter>
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
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
