import { Box } from "@mui/material";

export const Logo = () => {
  return (
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
  );
};
