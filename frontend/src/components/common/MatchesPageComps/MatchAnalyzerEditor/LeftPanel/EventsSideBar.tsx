import React from "react";
import { Box, useTheme } from "@mui/material";
import { EventsList } from "./EventsList";
import { HotkeysPanel } from "./HotkeysPanel";

interface EventsSideBarProps {
  isEditor: boolean;
}

export const EventsSideBar: React.FC<EventsSideBarProps> = ({ isEditor }) => {
  const theme = useTheme();

  return (
    <Box
      sx={{
        width: "260px",
        backgroundColor: theme.palette.background.paper,
        borderRight: `1px solid ${theme.palette.divider}`,
        display: "flex",
        flexDirection: "column",
        height: "100%",
      }}
    >
      <EventsList />
      {isEditor && <HotkeysPanel />}
    </Box>
  );
};
