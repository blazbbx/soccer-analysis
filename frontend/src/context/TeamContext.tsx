import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { TeamDto, CreateTeamRequest, UpdateTeamRequest } from '../types/team';
import { teamService } from '../services/teamService';
import { useAuth } from './AuthContext';

interface TeamContextType {
  teams: TeamDto[];
  isLoadingTeams: boolean;
  refreshTeams: () => Promise<void>;
  addTeam: (data: CreateTeamRequest) => Promise<void>;
  editTeam: (id: string, data: UpdateTeamRequest) => Promise<void>;
  removeTeam: (id: string) => Promise<void>;
}

const TeamContext = createContext<TeamContextType | undefined>(undefined);

export const TeamProvider = ({ children }: { children: ReactNode }) => {
  const [teams, setTeams] = useState<TeamDto[]>([]);
  const [isLoadingTeams, setIsLoadingTeams] = useState<boolean>(true);
  
  // Szükségünk van az AuthContext-re, hogy tudjuk, ki van bejelentkezve
  const { user } = useAuth();

  const fetchTeams = async () => {
    if (!user) {
      setTeams([]);
      setIsLoadingTeams(false);
      return;
    }
    
    try {
      setIsLoadingTeams(true);
      const data = await teamService.getMyTeams();
      setTeams(data);
    } catch (error) {
      console.error("Hiba a csapatok betöltésekor:", error);
    } finally {
      setIsLoadingTeams(false);
    }
  };

  // Ha változik a bejelentkezett felhasználó (pl. belép/kilép), frissítjük a csapatokat
  useEffect(() => {
    fetchTeams();
  }, [user]);

  const addTeam = async (data: CreateTeamRequest) => {
    const newTeam = await teamService.createTeam(data);
    setTeams((prev) => [...prev, newTeam]);
  };

  const editTeam = async (id: string, data: UpdateTeamRequest) => {
    const updatedTeam = await teamService.updateTeam(id, data);
    setTeams((prev) => prev.map((t) => (t.id === id ? updatedTeam : t)));
  };

  const removeTeam = async (id: string) => {
    await teamService.deleteTeam(id);
    setTeams((prev) => prev.filter((t) => t.id !== id));
  };

  return (
    <TeamContext.Provider value={{ teams, isLoadingTeams, refreshTeams: fetchTeams, addTeam, editTeam, removeTeam }}>
      {children}
    </TeamContext.Provider>
  );
};

export const useTeams = () => {
  const context = useContext(TeamContext);
  if (context === undefined) {
    throw new Error('A useTeams hookot csak egy TeamProvider-en belül lehet használni!');
  }
  return context;
};