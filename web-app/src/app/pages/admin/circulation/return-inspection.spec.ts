import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NgModel } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { Subject, of, throwError } from 'rxjs';
import { CirculationService, LoanItem, LoanTicketDetail, LoanTicketSummary } from '../../../services/circulation.service';
import { ReturnInspectionReceipt, ReturnInspectionRequest } from '../../../services/return-inspection.models';
import { ReturnInspection } from './return-inspection';

const loan: LoanItem = { ticketId: 19, detailId: 29, bookId: 9, bookTitle: 'Clean Code', barcode: 'BC-019',
  ticketType: 'BORROW', status: 'BORROWING', itemStatus: 'BORROWING', renewalCount: 0, lateFineAccrued: 8000 };
const ticket: LoanTicketDetail = { ticketId: 19, userId: 'reader-19', mssv: '2001190019',
  readerName: 'Nguyễn Văn A', status: 'BORROWING', ticketType: 'BORROW', items: [loan] };
const summary: LoanTicketSummary = { ...ticket, itemCount: 1, overdueItemCount: 0, totalLateFine: 8000 };

function receipt(condition: 'NORMAL' | 'DAMAGED' | 'LOST' = 'NORMAL'): ReturnInspectionReceipt {
  return {
    operationId: 'inspection-1', operationType: 'RETURN_INSPECT', processedAt: '2026-10-29T09:00:00', replayed: false,
    items: [{ loanDetailId: 29, ticketId: 19, userId: 'reader-19', barcode: 'BC-019', condition,
      detailStatus: condition === 'NORMAL' ? 'RETURNED' : condition,
      copyStatus: condition === 'NORMAL' ? 'AVAILABLE' : condition === 'LOST' ? 'LOST' : 'MAINTENANCE',
      penaltyCaseId: condition === 'NORMAL' ? null : 77, penaltyStatus: condition === 'NORMAL' ? null : 'OUTSTANDING',
      lateFineAdded: 1000, lateFineTotal: 9000, penaltyAmount: condition === 'NORMAL' ? 0 : 200000,
      depositApplied: condition === 'NORMAL' ? 1000 : 50000, debtAdded: condition === 'NORMAL' ? 0 : 151000,
      penaltyOutstanding: condition === 'NORMAL' ? 0 : 151000,
      returnDate: condition === 'NORMAL' ? '2026-10-29T09:00:00' : null,
      fineStoppedAt: condition === 'NORMAL' ? null : '2026-10-29T09:00:00' }],
    tickets: [{ ticketId: 19, status: condition === 'NORMAL' ? 'RETURNED' : 'BORROWING' }],
    balances: [{ userId: 'reader-19', depositBalance: 0, totalDebt: condition === 'NORMAL' ? 0 : 161000 }],
  };
}

function stubService() {
  return {
    getActiveLoanItemByBarcode: vi.fn(() => of({ ...loan })),
    getLoan: vi.fn(() => of({ ...ticket, items: [{ ...loan }] })),
    getStaffLoans: vi.fn(() => of([summary])),
    inspectReturn: vi.fn((_request: ReturnInspectionRequest) => of(receipt())),
    returnBooks: vi.fn(),
  };
}

describe('Return inspection desk', () => {
  let fixture: ComponentFixture<ReturnInspection>;
  let component: ReturnInspection;
  let service: ReturnType<typeof stubService>;
  const storageKey = 'huit_lib_pending_inspection:b03-staff';

  beforeEach(async () => {
    localStorage.setItem('huit_lib_profile', JSON.stringify({ userId: 'b03-staff' }));
    sessionStorage.removeItem(storageKey);
    service = stubService();
    await TestBed.configureTestingModule({
      imports: [ReturnInspection], providers: [provideRouter([]), { provide: CirculationService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(ReturnInspection);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('counterOpen', true);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    fixture.destroy();
    sessionStorage.removeItem(storageKey);
    localStorage.removeItem('huit_lib_profile');
  });

  it('stages scans and manual barcodes without submitting a return; cancel has no mutation', () => {
    component.barcodeInput = ' BC-019 ';
    component.addBarcode();
    fixture.detectChanges();
    expect(service.getActiveLoanItemByBarcode).toHaveBeenCalledWith('BC-019');
    expect(component.drafts).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Clean Code');
    expect(service.inspectReturn).not.toHaveBeenCalled();
    component.cancelDraft();
    expect(component.drafts).toHaveLength(0);
    expect(service.inspectReturn).not.toHaveBeenCalled();
    expect(service.returnBooks).not.toHaveBeenCalled();
  });

  it('rejects duplicate barcodes and a fourth item', () => {
    component.addBarcode('BC-019');
    component.addBarcode('BC-019');
    expect(service.getActiveLoanItemByBarcode).toHaveBeenCalledTimes(1);
    expect(component.errorMessage).toContain('đã có');
    component.addItem({ ...loan, detailId: 30, barcode: 'BC-020' });
    component.addItem({ ...loan, detailId: 31, barcode: 'BC-021' });
    component.addItem({ ...loan, detailId: 32, barcode: 'BC-022' });
    expect(component.drafts).toHaveLength(3);
    expect(component.errorMessage).toContain('tối đa 3');
  });

  it('loads a ticket and reports a lost item without any barcode lookup', () => {
    component.lookupInput = 'HUIT-PM-19';
    component.lookup();
    component.addItem(component.selectedTicket!.items[0], true);
    const draft = component.drafts[0];
    draft.penalty.description = 'Bạn đọc báo mất';
    draft.market = 'NO';
    draft.penalty.settlementMethod = 'CASH';
    draft.penalty.coverPrice = 30000;
    service.inspectReturn.mockReturnValueOnce(of(receipt('LOST')));
    component.submitInspection();
    expect(service.getLoan).toHaveBeenCalledWith(19);
    expect(service.getActiveLoanItemByBarcode).not.toHaveBeenCalled();
    expect(service.inspectReturn).toHaveBeenCalledTimes(1);
    expect(service.inspectReturn.mock.calls[0][0].items[0]).toMatchObject({ loanDetailId: 29, condition: 'LOST',
      penalty: { loanDetailId: 29, incidentType: 'LOST', stillInMarket: false, settlementMethod: 'CASH', coverPrice: 30000 } });
    expect(service.returnBooks).not.toHaveBeenCalled();
  });

  it('searches a reader and lets staff pick the correct active ticket', () => {
    service.getStaffLoans.mockReturnValueOnce(of([summary, { ...summary, ticketId: 20, status: 'RETURNED' }]));
    component.lookupMode = 'reader';
    component.lookupInput = '2001190019';
    component.lookup();
    expect(component.matchingLoans.map(item => item.ticketId)).toEqual([19]);
    component.loadTicket(19);
    expect(component.selectedTicket?.readerName).toBe('Nguyễn Văn A');
    expect(service.inspectReturn).not.toHaveBeenCalled();
  });

  it('rejects previously damaged or settled details even if their ticket is still BORROWING', () => {
    component.addItem({ ...loan, itemStatus: 'DAMAGED' }, true);
    component.addItem({ ...loan, itemStatus: 'SETTLED' });
    expect(component.drafts).toHaveLength(0);
    expect(component.errorMessage).toContain('đã có hồ sơ');
    expect(service.inspectReturn).not.toHaveBeenCalled();
  });

  it('loads a return-ticket deep link without approving or checking out the ticket', () => {
    fixture.componentRef.setInput('initialTicketId', 19);
    fixture.detectChanges();
    expect(service.getLoan).toHaveBeenCalledWith(19);
    expect(component.selectedTicket?.status).toBe('BORROWING');
  });

  it('renders the fields for the selected condition and settlement', async () => {
    component.addItem({ ...loan });
    fixture.detectChanges();
    await changeSelect('condition-29', 'LOST');
    expect(control('market-29')).not.toBeNull();
    expect(control('repair-29')).toBeNull();
    await changeSelect('market-29', 'NO');
    await changeSelect('settlement-29', 'CASH');
    expect(control('price-29')).not.toBeNull();
    await changeSelect('language-29', 'FOREIGN');
    const price: HTMLInputElement = control('price-29');
    price.value = '10';
    price.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(control('fx-29')).not.toBeNull();
    await changeSelect('condition-29', 'NORMAL');
    expect(control('description-29')).toBeNull();
  });

  it('does not submit an incomplete incident form', () => {
    component.addItem({ ...loan }, true);
    component.submitInspection();
    expect(component.errorMessage).toContain('mô tả');
    expect(service.inspectReturn).not.toHaveBeenCalled();
    component.drafts[0].penalty.description = 'Báo mất';
    component.submitInspection();
    expect(component.errorMessage).toContain('còn lưu hành');
    expect(service.inspectReturn).not.toHaveBeenCalled();
  });

  it('locks the form and prevents duplicate submissions while HTTP is pending', () => {
    const response = new Subject<ReturnInspectionReceipt>();
    service.inspectReturn.mockReturnValueOnce(response);
    component.addItem({ ...loan });
    component.submitInspection();
    component.submitInspection();
    component.cancelDraft();
    component.removeItem(29);
    fixture.detectChanges();
    expect(service.inspectReturn).toHaveBeenCalledTimes(1);
    expect(component.drafts).toHaveLength(1);
    expect(component.locked).toBe(true);
    expect(fixture.nativeElement.querySelector('.item-form').disabled).toBe(true);
    response.next(receipt());
    response.complete();
    expect(component.locked).toBe(false);
    expect(component.drafts).toHaveLength(0);
  });

  it('retries the frozen payload and original key after network failure, including after closing time', () => {
    service.inspectReturn.mockReturnValueOnce(throwError(() => ({ status: 0 })));
    component.addItem({ ...loan });
    component.submitInspection();
    const first = service.inspectReturn.mock.calls[0][0];
    expect(component.hasPending).toBe(true);
    expect(component.editableFailure).toBe(false);
    component.drafts[0].note = 'A programmatic form change must not alter an uncertain request';
    fixture.componentRef.setInput('counterOpen', false);
    component.submitInspection();
    expect(service.inspectReturn.mock.calls[1][0]).toEqual(first);
    expect(component.receipt?.operationId).toBe('inspection-1');
    expect(sessionStorage.getItem(storageKey)).toBeNull();
  });

  it('restores an uncertain request in the same user session without automatically sending it', async () => {
    service.inspectReturn.mockReturnValueOnce(throwError(() => ({ status: 500 })));
    component.addItem({ ...loan });
    component.submitInspection();
    const first = service.inspectReturn.mock.calls[0][0];
    expect(sessionStorage.getItem(storageKey)).toContain(first.idempotencyKey);
    fixture.destroy();
    fixture = TestBed.createComponent(ReturnInspection);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('counterOpen', true);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.hasPending).toBe(true);
    expect(service.inspectReturn).toHaveBeenCalledTimes(1);
    component.submitInspection();
    expect(service.inspectReturn.mock.calls[1][0]).toEqual(first);
  });

  it('does not restore another staff member’s pending request', async () => {
    service.inspectReturn.mockReturnValueOnce(throwError(() => ({ status: 0 })));
    component.addItem({ ...loan });
    component.submitInspection();
    fixture.destroy();
    localStorage.setItem('huit_lib_profile', JSON.stringify({ userId: 'another-staff' }));
    fixture = TestBed.createComponent(ReturnInspection);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.hasPending).toBe(false);
    expect(component.drafts).toHaveLength(0);
  });

  it('allows an explicitly rejected request to be corrected with a new key', () => {
    service.inspectReturn.mockReturnValueOnce(throwError(() => ({ status: 409, error: { detail: 'Thiếu biểu phí hiệu lực' } })));
    component.addItem({ ...loan });
    component.submitInspection();
    const oldKey = service.inspectReturn.mock.calls[0][0].idempotencyKey;
    expect(component.errorMessage).toContain('Thiếu biểu phí');
    expect(component.editableFailure).toBe(true);
    component.editRejectedRequest();
    component.drafts[0].note = 'Đã đối chiếu';
    component.submitInspection();
    expect(service.inspectReturn.mock.calls[1][0].idempotencyKey).not.toBe(oldKey);
  });

  it('shows server charges, applied deposit, debt and case link without claiming incident stock is available', () => {
    service.inspectReturn.mockReturnValueOnce(of(receipt('DAMAGED')));
    component.addItem({ ...loan });
    const draft = component.drafts[0];
    draft.condition = 'DAMAGED';
    draft.penalty.description = 'Rách 2 trang';
    draft.penalty.repairFee = 200000;
    component.submitInspection();
    fixture.detectChanges();
    const view = fixture.nativeElement.querySelector('.receipt');
    expect(view.textContent).toContain('Chờ sửa chữa');
    expect(view.textContent).not.toContain('Sẵn sàng trong kho');
    expect(view.textContent).not.toContain('về kho');
    expect(view.querySelector('.receipt-totals').textContent).toContain('201,000');
    expect(view.querySelector('.receipt-totals').textContent).not.toContain('209,000');
    expect(view.textContent).toContain('50,000');
    expect(view.textContent).toContain('161,000');
    expect(view.querySelector('a').getAttribute('href')).toContain('referenceId=77');
    expect(view.querySelector('a').getAttribute('href')).toContain('/admin/penalties');
    expect(service.returnBooks).not.toHaveBeenCalled();
  });

  it('keeps the original request pending when a success response is incomplete', () => {
    service.inspectReturn.mockReturnValueOnce(of({} as ReturnInspectionReceipt));
    component.addItem({ ...loan });
    component.submitInspection();
    const first = service.inspectReturn.mock.calls[0][0];
    expect(component.receipt).toBeNull();
    expect(component.hasPending).toBe(true);
    expect(component.errorMessage).toContain('chưa đủ thông tin');
    component.submitInspection();
    expect(service.inspectReturn.mock.calls[1][0]).toEqual(first);
    expect(component.receipt?.operationId).toBe('inspection-1');
  });

  it('blocks new operations outside counter hours and reports lookup failures without fallback data', () => {
    fixture.componentRef.setInput('counterOpen', false);
    component.addItem({ ...loan });
    component.submitInspection();
    expect(service.inspectReturn).not.toHaveBeenCalled();
    component.cancelDraft();
    service.getActiveLoanItemByBarcode.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Không có lượt mượn' } })));
    component.addBarcode('INVALID');
    expect(component.drafts).toHaveLength(0);
    expect(component.loading).toBe(false);
    expect(component.errorMessage).toContain('Không có lượt mượn');
  });

  function control(name: string): any {
    return fixture.debugElement.queryAll(By.directive(NgModel))
      .find(element => element.injector.get(NgModel).name === name)?.nativeElement ?? null;
  }

  async function changeSelect(name: string, value: string): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    const select: HTMLSelectElement = control(name);
    select.value = value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
});
