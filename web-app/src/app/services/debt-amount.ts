import { DebtEntry } from './penalty.service';
const feeTypes=new Set(['FINE','OVERDUE_FINE','REPAIR_FEE','REPAIR','COMPENSATION','TECHNICAL_FEE']);
const closed=new Set(['PAID','WAIVED','ADJUSTED','CANCELLED','COMPLETED','SETTLED','CLOSED']);
export function isDebtCharge(entry:DebtEntry):boolean{return Number(entry.amount)>0 && feeTypes.has(entry.type);}
export function remainingDebt(entry:DebtEntry):number {
  return !isDebtCharge(entry)||closed.has(entry.status)?0:Math.max(0,Number(entry.amount)-Number(entry.paidAmount||0));
}
