import type { PenaltyCaseRequest } from './penalty.service';

export type ReturnCondition = 'NORMAL' | 'DAMAGED' | 'LOST';

export interface ReturnInspectionItem {
  loanDetailId: number;
  condition: ReturnCondition;
  returnConditionNote?: string;
  penalty?: PenaltyCaseRequest;
}

export interface ReturnInspectionRequest {
  idempotencyKey: string;
  items: ReturnInspectionItem[];
}

export interface ReturnInspectionResult {
  loanDetailId: number;
  ticketId: number;
  userId: string;
  barcode: string;
  condition: ReturnCondition;
  detailStatus: string;
  copyStatus: string;
  penaltyCaseId: number | null;
  penaltyStatus: string | null;
  lateFineAdded: number;
  lateFineTotal: number;
  penaltyAmount: number;
  depositApplied: number;
  debtAdded: number;
  penaltyOutstanding: number;
  returnDate: string | null;
  fineStoppedAt: string | null;
}

export interface ReturnInspectionReceipt {
  operationId: string;
  operationType: 'RETURN_INSPECT';
  processedAt: string;
  replayed: boolean;
  items: ReturnInspectionResult[];
  tickets: Array<{ ticketId: number; status: string }>;
  balances: Array<{ userId: string; depositBalance: number; totalDebt: number }>;
}
