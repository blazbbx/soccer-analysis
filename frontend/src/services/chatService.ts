import type { ChatMessage, SendMessageRequest } from '../types/chat';
import type { ClipResponse } from '../api/generated/model';

const MOCK_MESSAGES: ChatMessage[] = [
  {
    id: '1',
    teamId: 'mock-team',
    senderId: 'user-coach-1',
    senderFirstName: 'Gábor',
    senderLastName: 'Nagy',
    senderRole: 'coach',
    content: 'Jó meccs volt tegnap, gratulálok mindenkinek!',
    sentAt: new Date(Date.now() - 7200000).toISOString(),
  },
  {
    id: '2',
    teamId: 'mock-team',
    senderId: 'user-player-1',
    senderFirstName: 'Péter',
    senderLastName: 'Kovács',
    senderRole: 'player',
    content: 'Köszönöm edző! Keményen dolgoztunk érte.',
    sentAt: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: '3',
    teamId: 'mock-team',
    senderId: 'user-player-2',
    senderFirstName: 'Ádám',
    senderLastName: 'Szabó',
    senderRole: 'player',
    content: 'Mikor lesz a következő edzés?',
    sentAt: new Date(Date.now() - 1800000).toISOString(),
  },
];

export const chatService = {
  async getMessages(_teamId: string): Promise<ChatMessage[]> {
    // TODO: replace with real API call
    // return customInstance<ChatMessage[]>(`/api/chat/${_teamId}/messages`, { method: 'GET' });
    return Promise.resolve(MOCK_MESSAGES);
  },

  async sendMessage(teamId: string, req: SendMessageRequest, senderId: string, senderFirstName: string, senderLastName: string, senderRole: 'admin' | 'coach' | 'player'): Promise<ChatMessage> {
    // TODO: replace with real API call
    // return customInstance<ChatMessage>(`/api/chat/${teamId}/messages`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(req) });
    const newMessage: ChatMessage = {
      id: crypto.randomUUID(),
      teamId,
      senderId,
      senderFirstName,
      senderLastName,
      senderRole,
      content: req.content,
      sentAt: new Date().toISOString(),
    };
    return Promise.resolve(newMessage);
  },

  async getSharedClips(_teamId: string): Promise<ClipResponse[]> {
    // TODO: replace with real API call
    // return customInstance<ClipResponse[]>(`/api/chat/${_teamId}/clips`, { method: 'GET' });
    return Promise.resolve([]);
  },
};
