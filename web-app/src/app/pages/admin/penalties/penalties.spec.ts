import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { PenaltyService } from '../../../services/penalty.service';
import { Penalties } from './penalties';

describe('Penalties', () => {
  let fixture: ComponentFixture<Penalties>;
  let component: Penalties;
  const service = {
    getCases: vi.fn(() => of([{
      caseId: 9, userId: 'user-1', ticketId: 5, loanDetailId: 6, barcode: 'BC-LOST',
      incidentType: 'LOST', description: 'Báo mất', settlementMethod: 'CASH', repairFee: 0,
      compensationAmount: 500000, technicalFee: 50000, overdueFineSnapshot: 3000,
      totalAmount: 550000, paidAmount: 0, status: 'OUTSTANDING',
      reportedAt: '2026-10-18T09:00:00', settlementDueAt: '2026-11-17T09:00:00',
    }])),
    getAdjustments: vi.fn(() => of([])),
    createCase: vi.fn(() => of({})),
    recordPayment: vi.fn(() => of({ receiptNumber: 'RCPT-01', allocations: [], remainingDebt: 0 })),
    acceptReplacement: vi.fn(() => of({})),
    submitReview: vi.fn(() => of({})),
    reviewWaiver: vi.fn(() => of({})),
    createRate: vi.fn(() => of({})),
    requestAdjustment: vi.fn(() => of({})),
    reviewAdjustment: vi.fn(() => of({})),
  };
  const auth = { getRole: vi.fn(() => 'LIBRARIAN') };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [Penalties],
      providers: [
        { provide: PenaltyService, useValue: service },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Penalties);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders server-backed penalty cases and the full fee breakdown', () => {
    const text = fixture.nativeElement.textContent;
    expect(service.getCases).toHaveBeenCalled();
    expect(text).toContain('BC-LOST');
    expect(text).toContain('550,000');
    expect(text).toContain('Còn nợ');
  });

  it('submits an outstanding case for external waiver review', () => {
    component.submitReview(9);
    expect(service.submitReview).toHaveBeenCalledWith(9);
  });
});
