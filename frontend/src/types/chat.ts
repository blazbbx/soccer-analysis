export interface ChatMessage {
  id: string;
  teamId: string;
  senderId: string;
  senderFirstName: string;
  senderLastName: string;
  senderRole: 'admin' | 'coach' | 'player';
  content: string;
  sentAt: string;
}

export interface SendMessageRequest {
  content: string;
}
