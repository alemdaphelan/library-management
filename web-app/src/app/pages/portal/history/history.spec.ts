import { ComponentFixture, TestBed } from '@angular/core/testing';

import { History } from './history';
import { provideRouter } from '@angular/router';
import { LibraryPolicyService } from '../../../services/library-policy.service';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { CirculationService } from '../../../services/circulation.service';
import { DebtEntry, PenaltyService } from '../../../services/penalty.service';

describe('History', () => {
  let component: History;
  let fixture: ComponentFixture<History>;
  const penalties = { getMyLedger: vi.fn(), getMyCases: vi.fn() };
  const circulation = { getMyLoans: vi.fn(), renewLoan: vi.fn(), reportLoss: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    penalties.getMyLedger.mockReturnValue(of([]));
    penalties.getMyCases.mockReturnValue(of([]));
    circulation.getMyLoans.mockReturnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [History],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isLoggedIn: () => false } },
        { provide: PenaltyService, useValue: penalties },
        { provide: CirculationService, useValue: circulation },
        {
          provide: LibraryPolicyService,
          useValue: { getPublicPolicy: () => of({ maximumRenewalCount: 1, renewalDays: 10 }) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(History);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('separates returned and cancelled tickets and keeps reserved tickets pending', () => {
    (component as any).processLoans([
      { ticketId: 1, status: 'RETURNED', bookTitle: 'A' },
      { ticketId: 2, status: 'CANCELLED', bookTitle: 'B' },
      { ticketId: 3, status: 'REJECTED', bookTitle: 'C', decisionNote: 'Thẻ đã hết hạn' },
      { ticketId: 4, status: 'RESERVED', bookTitle: 'D', pickupDeadline: '2026-10-25T17:00:00' },
      { ticketId: 5, status: 'OVERDUE', bookTitle: 'E' },
    ]);

    expect(component.pastList.map((item) => item.ticketId)).toEqual([1]);
    expect(component.cancelledList.map((item) => item.ticketId)).toEqual([2, 3]);
    expect(component.cancelledList[1].decisionNote).toBe('Thẻ đã hết hạn');
    expect(component.pendingList[0].pickupDeadline).toBe('2026-10-25T17:00:00');
    expect(component.borrowingList.map((item) => item.ticketId)).toContain(5);
  });

  it('hides the debt warning after full deposit application, even with a paid case in history', () => {
    component.isLoggedIn = true;
    component.isLoading = false;
    component.debtEntries = [
      {
        transactionId: 1,
        type: 'FINE',
        amount: 10000,
        paidAmount: 10000,
        status: 'PAID',
        reason: 'Cấn cọc',
      },
      { transactionId: 2, type: 'DEPOSIT', amount: 50000, status: 'COMPLETED', reason: 'Nạp cọc' },
    ];
    expect(component.outstandingDebt).toBe(0);
    expect(component.openDebtEntries).toEqual([]);
    component.penaltyCases = [
      { caseId: 1, status: 'PAID', totalAmount: 10000, paidAmount: 10000 } as any,
    ];
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.debt-summary')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('còn phải thanh toán');
  });

  it('keeps a warning only for the unpaid portion and never lists closed penalty cases in it', () => {
    component.isLoggedIn = true;
    component.isLoading = false;
    component.debtEntries = [
      {
        transactionId: 1,
        type: 'FINE',
        amount: 10000,
        paidAmount: 4000,
        status: 'PARTIALLY_PAID',
        reason: 'Khoản còn thiếu',
      },
    ];
    component.penaltyCases = [
      { caseId: 99, status: 'PAID', totalAmount: 10000, paidAmount: 10000 } as any,
    ];
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    const banner = fixture.nativeElement.querySelector('.debt-summary');
    expect(banner.textContent).toContain('6,000');
    expect(banner.textContent).not.toContain('Hồ sơ #99');
  });

  it('refreshes financials as well as loans and hides a warning when a delayed payment update arrives', async () => {
    component.isLoggedIn = true;
    component.isLoading = false;
    component.debtEntries = [
      { transactionId: 1, type: 'FINE', amount: 10000, status: 'OUTSTANDING', reason: 'Phạt' },
    ];
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    const pending = new Subject<DebtEntry[]>();
    penalties.getMyLedger.mockReturnValueOnce(pending);
    component.refresh();
    expect(circulation.getMyLoans).toHaveBeenCalled();
    expect(penalties.getMyLedger).toHaveBeenCalled();
    pending.next([{ ...component.debtEntries[0], paidAmount: 10000, status: 'PAID' }]);
    pending.complete();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.debt-summary')).toBeNull();
  });

  function mixedTicket(): void {
    component.isLoggedIn = true;
    component.isLoading = false;
    (component as any).processLoans([
      {
        ticketId: 33,
        detailId: 47,
        status: 'BORROWING',
        itemStatus: 'DAMAGED',
        bookTitle: 'Sách hỏng',
        renewalCount: 1,
        physicalReturnAt: '2026-10-31T10:22:53',
        returnDate: '2026-10-31T10:22:53',
      },
      {
        ticketId: 33,
        detailId: 48,
        status: 'BORROWING',
        itemStatus: 'BORROWING',
        bookTitle: 'Sách còn giữ',
        renewalCount: 0,
      },
      {
        ticketId: 33,
        detailId: 49,
        status: 'BORROWING',
        itemStatus: 'LOST',
        bookTitle: 'Sách mất',
        renewalCount: 0,
      },
    ]);
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  it('shows the receipt and each item renewal count while only the still-held item can renew', () => {
    mixedTicket();
    const rows = [...fixture.nativeElement.querySelectorAll('.ticket-book-row')] as HTMLElement[];
    expect(rows).toHaveLength(3);
    expect(rows[0].textContent).toContain('Đã trả · sách hỏng');
    expect(rows[0].textContent).toContain('31/10/2026 10:22');
    expect(rows[0].querySelector('.renewal-count-badge')?.textContent).toContain('1/1');
    expect(rows[1].querySelector('.renewal-count-badge')?.textContent).toContain('0/1');
    expect(rows[0].querySelector('button')).toBeNull();
    expect(rows[1].querySelector('button')?.disabled).toBe(false);
    expect(rows[2].querySelector('button')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('.item-header-meta .renewal-count-badge'),
    ).toBeNull();
  });

  it('uses the same renewal guard in All and rejects direct calls for incidents or received rows', async () => {
    mixedTicket();
    component.setTab('ALL');
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    const buttons = [
      ...fixture.nativeElement.querySelectorAll('.btn-renew-primary'),
    ] as HTMLElement[];
    expect(buttons).toHaveLength(1);
    expect(buttons[0].textContent).toContain('Sách còn giữ');
    const items = component.allList[0].items;
    await component.renewBook(items[0]);
    await component.renewBook(items[2]);
    await component.renewBook({ ...items[1], physicalReturnAt: '2026-11-01T10:00:00' });
    await component.renewBook({
      ...items[1],
      itemStatus: 'RETURNED',
      returnDate: '2026-11-01T10:00:00',
    });
    expect(circulation.renewLoan).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.renewal-count-badge')).toHaveLength(3);
  });

  it('puts a completed damaged ticket in Past even when its penalty remains unpaid', () => {
    component.isLoggedIn = true;
    component.isLoading = false;
    component.selectedTab = 'PAST';
    component.debtEntries = [
      {
        transactionId: 1,
        type: 'REPAIR_FEE',
        amount: 200000,
        paidAmount: 0,
        reason: 'Sửa sách',
        status: 'OUTSTANDING',
      },
    ];
    (component as any).processLoans([
      {
        ticketId: 33,
        detailId: 47,
        status: 'RETURNED',
        itemStatus: 'DAMAGED',
        bookTitle: 'Sách hỏng',
        physicalReturnAt: '2026-10-31T10:22:53',
        returnDate: '2026-10-31T10:22:53',
        renewalCount: 1,
      },
    ]);
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(component.borrowingList).toHaveLength(0);
    expect(component.pastList).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('.debt-summary')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.tl-card').textContent).toContain(
      'Đã trả · sách hỏng',
    );
    expect(fixture.nativeElement.querySelector('.tl-card').textContent).toContain('1/1');
    expect(fixture.nativeElement.querySelector('.btn-renew-primary')).toBeNull();
  });

  it('keeps the overdue warning for a held item and never offers renewal for it', () => {
    component.isLoggedIn = true;
    component.isLoading = false;
    (component as any).processLoans([
      {
        ticketId: 50,
        detailId: 51,
        status: 'OVERDUE',
        itemStatus: 'OVERDUE',
        bookTitle: 'Quá hạn',
        dueDate: '2026-10-31T10:00:00',
        daysRemaining: -4,
      },
    ]);
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Quá hạn 4 ngày!');
    expect(fixture.nativeElement.querySelector('.btn-renew-primary')).toBeNull();
  });

  it('shows the library reason and revised deadline and blocks renewal in both tabs', async () => {
    component.isLoggedIn = true;
    component.isLoading = false;
    (component as any).processLoans([
      {
        ticketId: 33,
        detailId: 45,
        status: 'BORROWING',
        itemStatus: 'BORROWING',
        bookTitle: 'Giáo trình',
        isRecalled: true,
        recallReason: 'Cần học phần',
        recallRequestedAt: '2026-11-04T10:00:00',
        recallDueDate: '2026-11-11T10:00:00',
        dueDate: '2026-11-11T10:00:00',
        renewalCount: 0,
      },
    ]);
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.recall-notice').textContent).toContain(
      'Cần học phần',
    );
    expect(fixture.nativeElement.querySelector('.recall-notice').textContent).toContain(
      '11/11/2026 10:00',
    );
    expect(fixture.nativeElement.querySelector('.btn-renew-primary')).toBeNull();
    await component.renewBook(component.allList[0].items[0]);
    expect(circulation.renewLoan).not.toHaveBeenCalled();
    component.setTab('ALL');
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.recall-notice').textContent).toContain(
      'Cần học phần',
    );
    expect(fixture.nativeElement.querySelector('.btn-renew-primary')).toBeNull();
  });

  it('offers loss reporting per held book in both tabs but never for an incident or received book', () => {
    mixedTicket();
    expect(fixture.nativeElement.querySelectorAll('.loan-item-actions .btn-cancel')).toHaveLength(
      1,
    );
    component.setTab('ALL');
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(
      [...fixture.nativeElement.querySelectorAll('.btn-cancel')].filter((b: any) =>
        b.textContent.includes('Báo mất'),
      ),
    ).toHaveLength(1);
    const held = component.allList[0].items[1];
    expect(component.canReportLoss({ ...held, status: 'OVERDUE', itemStatus: 'OVERDUE' })).toBe(
      true,
    );
    expect(component.canReportLoss({ ...held, itemStatus: 'PENDING' })).toBe(false);
    expect(component.canReportLoss({ ...held, physicalReturnAt: '2026-11-04T10:00:00' })).toBe(
      false,
    );
    component.openLossReport(held);
    component.closeLossReport();
    expect(circulation.reportLoss).not.toHaveBeenCalled();
  });

  it('keeps the submission locked until server confirmation and updates the exact item without reloading', async () => {
    mixedTicket();
    const item = component.allList[0].items[1];
    const pending = new Subject<any>();
    circulation.reportLoss.mockReturnValueOnce(pending);
    component.openLossReport(item);
    component.lossReportDescription = '  Mất trên xe buýt  ';
    component.submitLossReport();
    component.submitLossReport();
    component.closeLossReport();
    expect(component.lossReportSubmitting).toBe(true);
    expect(component.lossReportItem).toBe(item);
    expect(circulation.reportLoss).toHaveBeenCalledExactlyOnceWith(33, 48, 'Mất trên xe buýt');
    pending.next({
      ...item,
      lossReportedAt: '2026-11-04T10:00:00',
      lossReportDescription: 'Mất trên xe buýt',
    });
    pending.complete();
    await fixture.whenStable();
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(component.lossReportItem).toBeNull();
    expect(component.lossReportSubmitting).toBe(false);
    const reported = component.allList[0].items[1];
    expect(component.canReportLoss(reported)).toBe(false);
    expect(component.canRenewItem(reported)).toBe(false);
    await component.renewBook(reported);
    expect(circulation.renewLoan).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Đã báo mất · chờ thủ thư');
    expect(circulation.getMyLoans).not.toHaveBeenCalled();
  });

  it('validates the description and retains the draft after errors or an unconfirmed response', () => {
    mixedTicket();
    const item = component.allList[0].items[1];
    component.openLossReport(item);
    component.lossReportDescription = ' ';
    component.submitLossReport();
    component.lossReportDescription = 'x'.repeat(1001);
    component.submitLossReport();
    expect(circulation.reportLoss).not.toHaveBeenCalled();
    component.lossReportDescription = 'Mất trên xe';
    circulation.reportLoss.mockReturnValueOnce(throwError(() => ({ status: 0 })));
    component.submitLossReport();
    expect(component.lossReportSubmitting).toBe(false);
    expect(component.lossReportDescription).toBe('Mất trên xe');
    expect(component.lossReportError).toContain('Chưa xác nhận');
    circulation.reportLoss.mockReturnValueOnce(of({ ticketId: 999, detailId: 999 }));
    component.submitLossReport();
    expect(component.lossReportError).toContain('Chưa xác nhận');
    expect(component.canReportLoss(item)).toBe(true);
    expect(component.lossReportItem).toBe(item);
  });

  it('releases the submit button after a timeout without automatically retrying', () => {
    mixedTicket();
    component.openLossReport(component.allList[0].items[1]);
    component.lossReportDescription = 'Chưa tìm thấy sách';
    circulation.reportLoss.mockReturnValueOnce(new Subject<any>());
    vi.useFakeTimers();
    try {
      component.submitLossReport();
      vi.advanceTimersByTime(30_000);
      expect(component.lossReportSubmitting).toBe(false);
      expect(component.lossReportError).toContain('Chưa xác nhận');
      expect(circulation.reportLoss).toHaveBeenCalledTimes(1);
      expect(component.lossReportDescription).toBe('Chưa tìm thấy sách');
    } finally {
      vi.useRealTimers();
    }
  });
});
