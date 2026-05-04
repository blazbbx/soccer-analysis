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
        event: "Gól",
        icon: <SportsSoccer sx = {{color : 'text.primary'}}/>,
        color: 'text.primary',
        hotkey: "g"
    },
    {
        event: "Szöglet",
        icon: <Flag sx = {{color : "#40e0d0"}}/>,
        color: '#40e0d0',
        hotkey: "c"
    },
    {
        event: "Szabadrúgás",
        icon: <Sports sx = {{color : "#ff0000"}}/>,
        color: '#ff0000',
        hotkey: "f"
    },
    {
        event: "Kiemelés",
        icon: <Star sx = {{color : "#d79334"}}/>,
        color: '#d79334',
        hotkey: "h"
    }
    
]