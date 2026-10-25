import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PenaltyService } from '../../../services/penalty.service';
import { MyPenalties } from './my-penalties';

describe('MyPenalties', () => {
  let component: MyPenalties;
  let fixture: ComponentFixture<MyPenalties>;

  const penaltyService = {
    getMyLedger: vi.fn(),
    getMyCases: vi.fn(),
  };

  beforeEach(async () => {
    penaltyService.getMyLedger.mockReturnValue(of([
      { transactionId: 1, type: 'FINE', amount: 10000, paidAmount: 4000, status: 'PARTIALLY_PAID', reason: 'Trễ hạn' },
      { transactionId: 2, type: 'TECHNICAL_FEE', amount: 50000, paidAmount: 0, status: 'OUTSTANDING', reason: 'Làm lại nhãn' },
      { transactionId: 3, type: 'PAYMENT', amount: -4000, status: 'COMPLETED', reason: 'Thu tiền mặt' },
    ]));
    penaltyService.getMyCases.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [MyPenalties],
      providers: [provideRouter([]), { provide: PenaltyService, useValue: penaltyService }],
    }).compileComponents();

    fixture = TestBed.createComponent(MyPenalties);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads personal ledger and separates payments from debt', () => {
    expect(component.debtEntries).toHaveLength(2);
    expect(component.paymentEntries).toHaveLength(1);
    expect(component.outstandingDebt).toBe(56000);
    expect(component.totalPaid).toBe(4000);
  });

  it('maps finance labels to Vietnamese', () => {
    expect(component.feeTypeLabel('TECHNICAL_FEE')).toBe('Phí xử lý kỹ thuật');
    expect(component.statusLabel('PARTIALLY_PAID')).toBe('Đã thanh toán một phần');
  });
});
