import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import {Login} from "./pages/Login";
import { RoleRoute } from "./components/auth/RoleRoute";
import { MainLayout } from "./components/layout/Mainlayout";
import { ROLES } from "./types/roles";
import {Teams} from "./pages/Teams";
import { AdminPanel } from "./pages/AdminPanel";
import { Dashboard } from "@mui/icons-material";
import { MatchEditor } from "./pages/MatchEditor";



//Az App, ami összefogja a routert
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />

        {/* Védett útvonalak */}
        <Route
          element={
            <RoleRoute
              allowedRoles={[ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER, ROLES.FAN]}
            />
          }
        >
          <Route element={<MainLayout />}>
            <Route path="/" element={<Dashboard />} />

            <Route
              element={<RoleRoute allowedRoles={[ROLES.COACH, ROLES.ADMIN]} />}
            >
              <Route path="/matcheditor" element={<MatchEditor/>} />
            </Route>
            

            <Route
              element={
                <RoleRoute
                  allowedRoles={[ROLES.ADMIN, ROLES.COACH, ROLES.PLAYER]}
                />
              }
            >
              <Route path="/teams" element={<Teams/>} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.ADMIN]} />}>
              <Route path="/adminpanel" element={<AdminPanel />} />
            </Route>
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
