import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { CashDepositRequest, PenaltyService } from '../../../services/penalty.service';
import { UserService } from '../../../services/user.service';
import { CounterDeposit } from './counter-deposit';

describe('CounterDeposit', () => {
  let fixture: ComponentFixture<CounterDeposit>;
  const storageKey = 'huit_lib_pending_cash_deposit:staff-1';
  const getReaderProfile = vi.fn();
  const recordCashDeposit = vi.fn();

  function mount(): ComponentFixture<CounterDeposit> {
    const view = TestBed.createComponent(CounterDeposit);
    view.detectChanges();
    return view;
  }

  beforeEach(() => {
    sessionStorage.removeItem(storageKey);
    getReaderProfile.mockReset().mockReturnValue(
      of({
        student: { userId: 'reader-1', mssv: '2001240001', fullName: 'Sinh viên A' },
        card: { depositBalance: 50000, totalDebt: 30000 },
      }),
    );
    recordCashDeposit.mockReset();
    TestBed.configureTestingModule({
      imports: [CounterDeposit],
      providers: [
        { provide: AuthService, useValue: { getUserProfile: () => ({ userId: 'staff-1' }) } },
        { provide: UserService, useValue: { getReaderProfile } },
        { provide: PenaltyService, useValue: { recordCashDeposit } },
      ],
    });
    fixture = mount();
  });

  afterEach(() => sessionStorage.removeItem(storageKey));

  it('recovers the same cash submission after a lost response and shows the receipt', () => {
    recordCashDeposit.mockReturnValueOnce(throwError(() => ({ status: 0 })));
    const form = fixture.componentInstance;
    form.mssv = '2001240001';
    form.lookup();
    form.amount = 50000;
    form.note = 'Quầy 1';
    form.submit();
    const request = recordCashDeposit.mock.calls[0][0] as CashDepositRequest;
    expect(request).toMatchObject({ userId: 'reader-1', amount: 50000, note: 'Quầy 1' });
    fixture.destroy();

    recordCashDeposit.mockReturnValueOnce(
      of({
        transactionId: 12,
        receiptNumber: 'COC-12',
        userId: 'reader-1',
        amount: 50000,
        paymentMethod: 'CASH',
        confirmedBy: 'staff-1',
        paidAt: '2026-11-06T10:00:00',
        depositBalance: 100000,
      }),
    );
    fixture = mount();
    expect(recordCashDeposit).toHaveBeenCalledTimes(1);
    const retry: HTMLButtonElement = fixture.nativeElement.querySelector('button[type=submit]');
    retry.click();
    fixture.detectChanges();
    expect(recordCashDeposit).toHaveBeenCalledTimes(2);
    expect(recordCashDeposit).toHaveBeenLastCalledWith(request);
    expect(fixture.nativeElement.textContent).toContain('Biên nhận COC-12');
    expect(fixture.nativeElement.textContent).toContain('100,000 VNĐ');
    expect(sessionStorage.getItem(storageKey)).toBeNull();
  });
});
