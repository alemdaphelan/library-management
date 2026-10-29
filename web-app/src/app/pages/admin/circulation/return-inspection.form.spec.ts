import { LoanItem } from '../../../services/circulation.service';
import { createInspectionDraft, draftError, inspectionPayload, isActiveReturnItem, normalizeSettlement } from './return-inspection.form';

const loan: LoanItem = { ticketId: 19, detailId: 29, barcode: 'BC-019', bookTitle: 'Clean Code',
  ticketType: 'BORROW', status: 'BORROWING', itemStatus: 'BORROWING', renewalCount: 0, lateFineAccrued: 0 };

describe('Return inspection form policy', () => {
  it('checks the item state independently from its borrowing ticket', () => {
    expect(isActiveReturnItem(loan)).toBe(true);
    for (const itemStatus of ['LOST', 'DAMAGED', 'RETURNED', 'SETTLED', 'PENDING', undefined]) {
      expect(isActiveReturnItem({ ...loan, itemStatus })).toBe(false);
    }
    expect(isActiveReturnItem({ ...loan, returnDate: '2026-10-29T09:00:00' })).toBe(false);
  });

  it('omits stale penalty fields when a book is returned normally', () => {
    const draft = createInspectionDraft(loan);
    draft.penalty.coverPrice = 100000;
    draft.penalty.repairFee = 9000;
    expect(draftError(draft)).toBeNull();
    expect(inspectionPayload(draft).penalty).toBeUndefined();
  });

  it('validates light damage and sends only repair fields', () => {
    const draft = createInspectionDraft(loan);
    draft.condition = 'DAMAGED';
    draft.penalty.description = ' Rách trang ';
    draft.penalty.repairFee = 200000;
    draft.penalty.coverPrice = 99999;
    draft.penalty.fxRate = 25000;
    draft.penalty.pagesAffected = 11;
    expect(draftError(draft)).toContain('hỏng nặng');
    draft.penalty.pagesAffected = 2;
    expect(draftError(draft)).toBeNull();
    expect(inspectionPayload(draft).penalty).toMatchObject({ loanDetailId: 29, incidentType: 'DAMAGED',
      description: 'Rách trang', repairFee: 200000, settlementMethod: 'REPAIR' });
    expect(inspectionPayload(draft).penalty?.coverPrice).toBeUndefined();
    expect(inspectionPayload(draft).penalty?.fxRate).toBeUndefined();
    draft.penalty.repairFee = 0;
    expect(draftError(draft)).toContain('lớn hơn 0');
  });

  it('requires explicit market verification for lost or heavily damaged items', () => {
    const draft = createInspectionDraft(loan);
    draft.condition = 'LOST';
    draft.penalty.description = 'Báo mất';
    normalizeSettlement(draft);
    expect(draftError(draft)).toContain('còn lưu hành');
    draft.market = 'YES';
    draft.penalty.settlementMethod = 'CASH';
    normalizeSettlement(draft);
    expect(draft.penalty.settlementMethod).toBe('REPLACEMENT');
    expect(draftError(draft)).toBeNull();
    expect(inspectionPayload(draft).penalty?.damageSeverity).toBeUndefined();
    draft.condition = 'DAMAGED';
    draft.penalty.damageSeverity = 'HEAVY';
    draft.market = '';
    expect(draftError(draft)).toContain('còn lưu hành');
  });

  it('requires FX evidence only for foreign cash compensation with a cover price', () => {
    const draft = createInspectionDraft(loan);
    draft.condition = 'LOST';
    draft.market = 'NO';
    draft.penalty.description = 'Báo mất';
    draft.penalty.settlementMethod = 'CASH';
    draft.penalty.languageCategory = 'FOREIGN';
    draft.penalty.coverPrice = 10;
    expect(draftError(draft)).toContain('tỷ giá');
    draft.penalty.fxRate = 25000;
    expect(draftError(draft)).toContain('nguồn tỷ giá');
    draft.penalty.fxSource = 'Nguồn đã xác minh';
    expect(draftError(draft)).toBeNull();
    draft.penalty.coverPrice = undefined;
    expect(draftError(draft)).toBeNull(); // server uses configured rate, never a client estimate
    expect(inspectionPayload(draft).penalty?.fxRate).toBeUndefined();
  });
});
