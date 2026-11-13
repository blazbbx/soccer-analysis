import { Link as RouterLink } from "react-router-dom";
import { Chip, Stack } from "@mui/material";
import EmojiEventsOutlinedIcon from "@mui/icons-material/EmojiEventsOutlined";
import { useTranslation } from "react-i18next";
import { useMatchCupLinks } from "../../api/cups";

/** 2.3: on the analysed match's page, a chip for each cup match it is linked to. Leads to the cup page. */
export const MatchCupChips = ({ matchId }: { matchId: string }) => {
  const { t } = useTranslation();
  const { data } = useMatchCupLinks(matchId);
  if (!data || data.length === 0) return null;

  return (
    <Stack direction="row" spacing={1} sx={{ px: 2, py: 1, flexWrap: "wrap", rowGap: 1 }}>
      {data.map((link) => (
        <Chip
          key={link.cupMatchId}
          icon={<EmojiEventsOutlinedIcon />}
          label={t("cups.cup-match-chip", { cup: link.cupName })}
          component={RouterLink}
          to={`/cups/${link.cupId}`}
          clickable
          size="small"
          variant="outlined"
        />
      ))}
    </Stack>
  );
};
