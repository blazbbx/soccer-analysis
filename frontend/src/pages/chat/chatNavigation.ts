/** Navigation state of a link opened from the chat: the clip viewer shows a back button to the chat. */
export const FROM_CHAT_STATE = { fromChat: true } as const;

export const isFromChat = (state: unknown): boolean =>
  typeof state === 'object' && state !== null && (state as { fromChat?: unknown }).fromChat === true;

// The last opened team chat of this tab, so the chat menu item returns to the same team.
const LAST_TEAM_KEY = 'chat.lastTeamId';

export const readLastChatTeam = (): string => {
  try {
    return sessionStorage.getItem(LAST_TEAM_KEY) ?? '';
  } catch {
    return '';
  }
};

export const saveLastChatTeam = (teamId: string) => {
  try {
    if (teamId) sessionStorage.setItem(LAST_TEAM_KEY, teamId);
  } catch {
    // Storage blocked: the chat simply opens on the first team.
  }
};
