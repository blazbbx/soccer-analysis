import { Flag, Sports, SportsSoccer, Star } from "@mui/icons-material";

export interface LabelItemConfig{
    event: string;
    icon: React.ReactNode;
    color: string;
    hotkey: string;
}

export const TRACKING_LABEL_KEY_MAP: Record<string, string> = {
  goal: 'g',
  corner: 'c',
  freekick: 'f',
  highlight: 'h',
};

export const LABEL_ITEMS : LabelItemConfig[] = [
    {
        event: "labels.goal",
        icon: <SportsSoccer sx = {{color : 'text.primary'}}/>,
        color: 'text.primary',
        hotkey: "g"
    },
    {
        event: "labels.corner",
        icon: <Flag sx = {{color : "#40e0d0"}}/>,
        color: '#40e0d0',
        hotkey: "c"
    },
    {
        event: "labels.freekick",
        icon: <Sports sx = {{color : "#ff0000"}}/>,
        color: '#ff0000',
        hotkey: "f"
    },
    {
        event: "labels.highlight",
        icon: <Star sx = {{color : "#d79334"}}/>,
        color: '#d79334',
        hotkey: "h"
    }

]
