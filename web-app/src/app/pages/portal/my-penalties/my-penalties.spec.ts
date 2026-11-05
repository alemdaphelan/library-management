import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PenaltyService } from '../../../services/penalty.service';
import { AuthService } from '../../../services/auth.service';
import { DialogService } from '../../../services/dialog.service';
import { MyPenalties } from './my-penalties';

describe('MyPenalties', () => {
  let component: MyPenalties;
  let fixture: ComponentFixture<MyPenalties>;

  const penaltyService = {
    getMyLedger: vi.fn(),
    getMyCases: vi.fn(),
    createOnlinePayment: vi.fn(),
    getPaymentCapabilities: vi.fn(() => of({ mockEnabled: false })),
  };
  const authService = { fetchLibraryCard: vi.fn() };
  const dialogService = { show: vi.fn() };

  beforeEach(async () => {
    penaltyService.getMyLedger.mockReturnValue(of([
      { transactionId: 1, type: 'FINE', amount: 10000, paidAmount: 4000, status: 'PARTIALLY_PAID', reason: 'Trễ hạn' },
      { transactionId: 2, type: 'TECHNICAL_FEE', amount: 50000, paidAmount: 0, status: 'OUTSTANDING', reason: 'Làm lại nhãn' },
      { transactionId: 3, type: 'PAYMENT', amount: -4000, status: 'COMPLETED', reason: 'Thu tiền mặt' },
    ]));
    penaltyService.getMyCases.mockReturnValue(of([]));
    authService.fetchLibraryCard.mockReturnValue(of({
      issued: true, ownerName: 'Sinh viên', cardStatus: 'ACTIVE', depositBalance: 50000, totalDebt: 56000,
    }));

    await TestBed.configureTestingModule({
      imports: [MyPenalties],
      providers: [
        provideRouter([]),
        { provide: PenaltyService, useValue: penaltyService },
        { provide: AuthService, useValue: authService },
        { provide: DialogService, useValue: dialogService },
      ],
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

  it('prepares real online debt and deposit amounts', () => {
    expect(component.paymentAmount).toBe(56000);
    component.selectPaymentPurpose('DEPOSIT');
    expect(component.paymentAmount).toBe(50000);
    component.selectPaymentPurpose('DEBT');
    expect(component.paymentAmount).toBe(56000);
  });

  it('treats waived/paid/deposit-satisfied charges as closed while retaining partial debt',()=>{
    component.ledger=[{transactionId:1,type:'FINE',amount:10000,paidAmount:10000,status:'PAID',reason:''},{transactionId:2,type:'COMPENSATION',amount:50000,paidAmount:0,status:'WAIVED',reason:''},{transactionId:3,type:'DEPOSIT_TOPUP',amount:50000,status:'COMPLETED',reason:''}] as any;
    expect(component.outstandingDebt).toBe(0);expect(component.openDebtEntries).toEqual([]);
  });

  it('hides settled charges by default but allows viewing their history without changing totals',()=>{
    component.ledger.push({transactionId:4,type:'REPAIR_FEE',amount:50000,paidAmount:50000,status:'PAID',reason:'Đã sửa xong'});
    const total=component.outstandingDebt;
    expect(component.filteredDebtEntries.map(e=>e.transactionId)).toEqual([1,2]);
    component.debtStatus='PAID';expect(component.filteredDebtEntries.map(e=>e.transactionId)).toEqual([4]);
    expect(component.outstandingDebt).toBe(total);expect(component.totalAssessed).toBe(110000);
    component.debtStatus='ALL';expect(component.filteredDebtEntries).toHaveLength(3);
  });

  it('filters before paging and resets a page when the user changes a filter',async()=>{
    component.ledger=Array.from({length:23},(_,n)=>({transactionId:n+1,type:n<21?'FINE':'REPAIR_FEE',amount:1000,paidAmount:0,status:'OUTSTANDING',reason:'Phạt quá hạn'}));
    fixture.changeDetectorRef.markForCheck();fixture.detectChanges();await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('#debt-list tbody tr')).toHaveLength(10);
    const pager=fixture.nativeElement.querySelector('nav[aria-label="Phân trang khoản phí"]');
    pager.querySelectorAll('button')[1].click();fixture.detectChanges();
    expect(component.debtPage).toBe(1);expect(component.visibleDebtEntries[0].transactionId).toBe(11);
    const typeSelect=fixture.nativeElement.querySelector('#debt-list select');
    typeSelect.value='PAID';typeSelect.dispatchEvent(new Event('change'));await fixture.whenStable();
    expect(component.debtPage).toBe(0);expect(component.filteredDebtEntries).toEqual([]);
    component.debtStatus='UNPAID';component.debtType='REPAIR_FEE';component.debtQuery='phat qua han';
    expect(component.visibleDebtEntries.map(e=>e.transactionId)).toEqual([22,23]);
    expect(component.outstandingDebt).toBe(23000);
  });

  it('keeps case and payment pages independent and resets them after refreshing shorter data',()=>{
    component.penaltyCases=Array.from({length:13},(_,i)=>({caseId:i+1,status:'OUTSTANDING',totalAmount:1000,paidAmount:0} as any));
    component.penaltyCases.push({caseId:99,status:'PAID',totalAmount:1000,paidAmount:1000} as any);
    component.casePage=1;expect(component.visibleCases).toHaveLength(3);expect(component.filteredCases).toHaveLength(13);
    component.caseStatus='CLOSED';component.casePage=0;expect(component.visibleCases.map(c=>c.caseId)).toEqual([99]);
    component.ledger=Array.from({length:12},(_,i)=>({transactionId:i+1,type:'PAYMENT',amount:-1000,status:'COMPLETED',reason:''}));
    component.paymentPage=1;expect(component.visiblePayments).toHaveLength(2);expect(component.debtPage).toBe(0);
    component.debtPage=4;component.casePage=2;component.loadFinancials();
    expect([component.debtPage,component.casePage,component.paymentPage]).toEqual([0,0,0]);
  });

  it('translates the card status and gives only active cards the green class',()=>{
    fixture.changeDetectorRef.markForCheck();fixture.detectChanges();
    let badge=fixture.nativeElement.querySelector('.card-status');
    expect(badge.textContent).toBe('Đang hoạt động');expect(badge.classList.contains('active')).toBe(true);
    component.libraryCard={...component.libraryCard!,cardStatus:'LOCKED'};fixture.changeDetectorRef.markForCheck();fixture.detectChanges();
    badge=fixture.nativeElement.querySelector('.card-status');expect(badge.textContent).toBe('Đã khóa');expect(badge.classList.contains('active')).toBe(false);
    component.libraryCard={...component.libraryCard!,cardStatus:'EXPIRED'};expect(component.cardStatusLabel).toBe('Đã hết hạn');
    component.libraryCard=null;expect(component.cardStatusLabel).toBe('Chưa cấp thẻ');
    expect(component.caseRemaining({totalAmount:50000,paidAmount:0,status:'WAIVED'} as any)).toBe(0);
  });

});
