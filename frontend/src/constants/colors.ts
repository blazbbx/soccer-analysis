// Single source of truth for the app's "success" green — also used as
// theme.palette.success.main (see theme/theme.ts) so status chips and stats
// can't drift apart.
export const SUCCESS_GREEN = '#22c55e';

export const STAT_COLORS = {
  wins: SUCCESS_GREEN,
  draws: '#a1a1aa',
  losses: '#ef4444',
  points: '#eab308',
  blueAccent: '#3b82f6',
  panelBg: 'rgba(255, 255, 255, 0.04)',
};

export const APP_COLORS = {
  filledAction: {
    main: '#14b8a6',      
    hover: '#0f766e',      
    contrastText: '#ffffff', 
  },
  sideBarButton:{
    activeHoverBackGround: 'rgba(16, 185, 129, 0.15)',
    active: '#10b981',
    activeBackGround: 'rgba(16, 185, 129, 0.1)'

  }
};