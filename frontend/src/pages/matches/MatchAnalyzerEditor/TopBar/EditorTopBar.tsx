import React from "react";
import { Box, Typography, IconButton, Button, Tooltip, useTheme } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ContentCutIcon from "@mui/icons-material/ContentCut";
import SportsSoccerIcon from "@mui/icons-material/SportsSoccer";
import { useNavigate } from "react-router-dom";

interface EditorTopBarProps {
  homeTeam?: string;
  awayTeam?: string;
  date?: string;
  backPath: string;
  onSnippetClick?: () => void;
  isEditor?: boolean;
  show2DView?: boolean;
  onToggle2DView?: () => void;
}

export const EditorTopBar: React.FC<EditorTopBarProps> = ({
  homeTeam,
  awayTeam,
  date,
  backPath,
  onSnippetClick,
  isEditor = false,
  show2DView = false,
  onToggle2DView,
}) => {
  const navigate = useNavigate();
  const theme = useTheme();

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        width: "100%",
        height: "60px", 
        bgcolor: theme.palette.background.paper, 
        borderBottom: `1px solid ${theme.palette.divider}`,
        padding: "0 16px",
      }}
    >
      {/* Bal oldal: Vissza gomb és Mérkőzés infók */}
      <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
        <IconButton
          onClick={() => navigate(backPath)}
          sx={{
            color: theme.palette.text.secondary,
            "&:hover": { color: theme.palette.text.primary, bgcolor: theme.palette.action.hover  },
          }}
        >
          <ArrowBackIcon />
        </IconButton>

        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          {/* Hazai csapat */}
          <Box sx={{ width: 16, height: 16, backgroundColor: "#8b5cf6", borderRadius: "4px" }} />
          <Typography sx={{ color: theme.palette.text.primary, fontSize: "15px", fontWeight: 500 }}>
            {homeTeam}
          </Typography>

          {/* VS jelölő a kép stílusában (A képen "0 - 2" van) */}
          <Box
            sx={{
              backgroundColor: theme.palette.action.selected,
              padding: "2px 8px",
              borderRadius: "4px",
              color: theme.palette.text.primary,
              fontSize: "13px",
              fontWeight: "bold",
            }}
          >
            VS
          </Box>

          {/* Ellenfél csapat */}
          <Typography sx={{ color: theme.palette.text.primary, fontSize: "15px", fontWeight: 500 }}>
            {awayTeam}
          </Typography>
          <Box sx={{ width: 16, height: 16, backgroundColor: theme.palette.primary.main, borderRadius: "4px" }} />

          {/* Dátum (és esetleg helyszín, ahogy a képen) */}
          <Typography sx={{ color: theme.palette.text.secondary, fontSize: "13px", ml: 1 }}>
            • {date?.split('T')[0]}
          </Typography>
        </Box>
      </Box>

      {/* Jobb oldal: 2D kapcsoló és Klip gomb */}
      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
        <Tooltip title="2D Pályakép">
          <IconButton
            onClick={onToggle2DView}
            sx={{
              color: show2DView ? theme.palette.primary.main : theme.palette.text.secondary,
              bgcolor: show2DView ? theme.palette.action.selected : "transparent",
              "&:hover": { bgcolor: theme.palette.action.hover },
            }}
          >
            <SportsSoccerIcon />
          </IconButton>
        </Tooltip>

        {isEditor && (
          <Button
            variant="contained"
            startIcon={<ContentCutIcon sx={{ transform: "rotate(270deg)" }} />}
            onClick={onSnippetClick}
            sx={{
              backgroundColor: "#8b5cf6",
              color: "#ffffff",
              textTransform: "none",
              fontWeight: 500,
              borderRadius: "6px",
              px: 2,
              boxShadow: "none",
              "&:hover": {
                backgroundColor: "#7c3aed",
                boxShadow: "none",
              },
            }}
          >
            Klipp (S)
          </Button>
        )}
      </Box>
    </Box>
  );
};