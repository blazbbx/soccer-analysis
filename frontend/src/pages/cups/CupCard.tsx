import { useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Box,
  Typography,
  Chip,
  Stack,
  IconButton,
  Tooltip,
  Collapse,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { useTranslation } from "react-i18next";
import { type CupListItem } from "../../api/paging";
import { type TeamResponse } from "../../api/generated/model";
import { CupDetails } from "./CupDetails";
import { CupActions } from "./CupActions";

interface CupCardProps {
  cup: CupListItem;
  myTeams: TeamResponse[];
}

/** One cup in the list: a quick view that folds open. The full view is the detail page (2.4). */
export const CupCard = ({ cup, myTeams }: CupCardProps) => {
  const { t } = useTranslation();
  const [isExpanded, setIsExpanded] = useState(false);
  const cupId = cup.id ?? "";

  return (
    <Box
      sx={{
        backgroundColor: "background.paper",
        borderRadius: 3,
        p: 3,
        border: "1px solid",
        borderColor: "divider",
      }}
    >
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="flex-start"
        sx={{ mb: isExpanded ? 2 : 0, gap: 1, flexWrap: "wrap" }}
      >
        <Box onClick={() => setIsExpanded((open) => !open)} sx={{ cursor: "pointer", flex: 1, minWidth: 0 }}>
          <Typography variant="h6" fontWeight="bold">
            {cup.name}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
            <Chip label={`${cup.teamCount} ${t("cups.teams")}`} size="small" variant="outlined" />
            {cup.createdAt && (
              <Chip
                label={new Date(cup.createdAt).toLocaleDateString()}
                size="small"
                variant="outlined"
                sx={{ color: "text.secondary" }}
              />
            )}
          </Stack>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          {cup.canManage && <CupActions cup={{ id: cupId, name: cup.name }} myTeams={myTeams} />}
          <Tooltip title={t("cups.open-cup")}>
            <IconButton size="small" component={RouterLink} to={`/cups/${cupId}`}>
              <OpenInNewIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <IconButton
            size="small"
            onClick={() => setIsExpanded((open) => !open)}
            aria-expanded={isExpanded}
            aria-controls={`cup-details-${cupId}`}
            aria-label={t(isExpanded ? "cups.hide-details" : "cups.show-details")}
          >
            <ExpandMoreIcon sx={{ transform: isExpanded ? "rotate(180deg)" : "none", transition: "transform 0.2s" }} />
          </IconButton>
        </Stack>
      </Stack>

      <Collapse in={isExpanded} timeout="auto" unmountOnExit>
        <Box id={`cup-details-${cupId}`}>
          <CupDetails cupId={cupId} canManage={cup.canManage} enabled={isExpanded} />
        </Box>
      </Collapse>
    </Box>
  );
};
