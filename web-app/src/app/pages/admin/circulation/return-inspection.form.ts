import type { LoanItem } from '../../../services/circulation.service';
import type { ReturnCondition, ReturnInspectionItem } from '../../../services/return-inspection.models';
import type { PenaltyCaseRequest } from '../../../services/penalty.service';

export interface InspectionDraft {
  loan: LoanItem;
  readerLabel: string;
  condition: ReturnCondition;
  note: string;
  market: '' | 'YES' | 'NO';
  penalty: PenaltyCaseRequest;
}

export function isActiveReturnItem(item: LoanItem): boolean {
  return Number.isSafeInteger(item.detailId) && item.detailId > 0 && !!item.barcode && !item.returnDate
    && ['BORROWING', 'OVERDUE'].includes(item.status)
    && ['BORROWING', 'OVERDUE'].includes(item.itemStatus || '');
}

export function createInspectionDraft(loan: LoanItem, readerLabel = `Phiếu #${loan.ticketId}`): InspectionDraft {
  return {
    loan, readerLabel, condition: 'NORMAL', note: '', market: '',
    penalty: {
      loanDetailId: loan.detailId, incidentType: 'DAMAGED', damageSeverity: 'LIGHT', description: '',
      materialType: 'BOOK', languageCategory: 'VIETNAMESE', stillInMarket: false, settlementMethod: 'REPAIR',
    },
  };
}

export function treatedAsLoss(draft: InspectionDraft): boolean {
  return draft.condition === 'LOST' || (draft.condition === 'DAMAGED' && draft.penalty.damageSeverity === 'HEAVY');
}

export function normalizeSettlement(draft: InspectionDraft): void {
  if (!treatedAsLoss(draft)) draft.penalty.settlementMethod = 'REPAIR';
  else if (draft.market !== 'NO' || draft.penalty.settlementMethod === 'REPAIR') {
    draft.penalty.settlementMethod = 'REPLACEMENT';
  }
}

export function draftError(draft: InspectionDraft): string | null {
  const form = draft.penalty;
  if (draft.note.length > 500) return 'Ghi chú khi trả tối đa 500 ký tự.';
  if (draft.condition === 'NORMAL') return null;
  if (!form.description.trim() || form.description.length > 1000) return 'Cần mô tả sự cố (tối đa 1.000 ký tự).';
  if (form.evidenceUrl && (form.evidenceUrl.length > 500 || !/^https?:\/\/\S+$/i.test(form.evidenceUrl.trim()))) {
    return 'Đường dẫn chứng cứ cần bắt đầu bằng http:// hoặc https://, tối đa 500 ký tự.';
  }
  if (draft.condition === 'DAMAGED') {
    if (!['LIGHT', 'HEAVY'].includes(form.damageSeverity || '')) return 'Cần chọn mức hư hỏng.';
    if (form.pagesAffected != null && (!Number.isInteger(form.pagesAffected) || form.pagesAffected < 0)) {
      return 'Số trang ảnh hưởng phải là số nguyên không âm.';
    }
    if (form.damageSeverity === 'LIGHT' && (form.pagesAffected || 0) > 10) return 'Trên 10 trang cần chọn hỏng nặng.';
  }
  if (!treatedAsLoss(draft)) {
    return Number.isFinite(form.repairFee) && Number(form.repairFee) > 0 ? null : 'Cần nhập chi phí sửa chữa lớn hơn 0.';
  }
  if (!draft.market) return 'Cần xác định tài liệu còn lưu hành hay không.';
  if (draft.market === 'YES' && form.settlementMethod !== 'REPLACEMENT') return 'Tài liệu còn lưu hành phải nộp sách thay thế.';
  if (!['REPLACEMENT', 'CASH'].includes(form.settlementMethod)) return 'Cần chọn cách đền bù.';
  if (form.settlementMethod !== 'CASH') return null;
  if (form.coverPrice != null && (!Number.isFinite(form.coverPrice) || form.coverPrice < 0)) return 'Giá bìa không hợp lệ.';
  if (form.languageCategory === 'FOREIGN' && Number(form.coverPrice) > 0) {
    if (!Number.isFinite(form.fxRate) || Number(form.fxRate) <= 0) return 'Cần tỷ giá lớn hơn 0 cho tài liệu ngoại văn có giá bìa.';
    if (!form.fxSource?.trim() || form.fxSource.length > 255) return 'Cần nguồn tỷ giá (tối đa 255 ký tự).';
  }
  if (form.originalCurrency && !/^[a-z]{3}$/i.test(form.originalCurrency.trim())) return 'Mã tiền tệ gồm 3 chữ cái, ví dụ USD.';
  return null;
}

/** Send only fields belonging to the selected condition/settlement; amounts are calculated by the server. */
export function inspectionPayload(draft: InspectionDraft): ReturnInspectionItem {
  const item: ReturnInspectionItem = {
    loanDetailId: draft.loan.detailId, condition: draft.condition,
    returnConditionNote: draft.note.trim() || undefined,
  };
  if (draft.condition === 'NORMAL') return item;
  const form = draft.penalty;
  item.penalty = {
    loanDetailId: draft.loan.detailId, incidentType: draft.condition,
    damageSeverity: draft.condition === 'DAMAGED' ? form.damageSeverity : undefined,
    pagesAffected: draft.condition === 'DAMAGED' ? form.pagesAffected ?? undefined : undefined,
    description: form.description.trim(), evidenceUrl: form.evidenceUrl?.trim() || undefined,
    materialType: form.materialType, languageCategory: form.languageCategory,
    stillInMarket: draft.market === 'YES', settlementMethod: form.settlementMethod,
  };
  if (!treatedAsLoss(draft)) item.penalty.repairFee = form.repairFee;
  else if (form.settlementMethod === 'CASH') {
    item.penalty.coverPrice = form.coverPrice ?? undefined;
    item.penalty.originalCurrency = form.originalCurrency?.trim().toUpperCase() || undefined;
    if (form.languageCategory === 'FOREIGN' && Number(form.coverPrice) > 0) {
      item.penalty.fxRate = form.fxRate;
      item.penalty.fxSource = form.fxSource?.trim();
    }
  }
  return item;
}
