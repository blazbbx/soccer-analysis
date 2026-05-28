import type { RecordActionType } from '../constants/recordActionTypes';

export interface RecordData {
  t: number;
  type: RecordActionType;
  m: number;
}
