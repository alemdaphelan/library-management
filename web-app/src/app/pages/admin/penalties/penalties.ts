import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, Optional, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, of } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { PaymentReceipt, PenaltyCase, PenaltyCaseRequest, PenaltyService } from '../../../services/penalty.service';
import { UserService, ReaderProfile } from '../../../services/user.service';
import { CirculationService, LoanItem, LoanTicketDetail } from '../../../services/circulation.service';
import { CounterDeposit } from './counter-deposit';

@Component({
  selector: 'app-penalties',
  standalone: true,
  imports: [CommonModule, FormsModule, CounterDeposit],
  templateUrl: './penalties.html',
  styleUrl: './penalties.css',
})
export class Penalties implements OnInit {
  private readonly destroyRef=inject(DestroyRef);
  private caseLookupVersion=0;
  private paymentLookupVersion=0;
  caseMssv='';paymentMssv='';
  caseReader?:ReaderProfile;paymentReader?:ReaderProfile;
  caseItems:LoanItem[]=[];
  caseLookupLoading=false;paymentLookupLoading=false;
  caseLookupError='';paymentLookupError='';
  cases: PenaltyCase[] = [];
  adjustments: any[] = [];
  loading = false;
  submitting = false;
  errorMessage = '';
  successMessage = '';
  receipt: PaymentReceipt | null = null;
  caseStatusFilter: 'ALL' | 'UNPAID' | 'PARTIAL' | 'PAID' = 'ALL';
  highlightedCaseId?: number;
  lossReportId?: number;
  lossReport?: LoanTicketDetail;
  lossReportLoading = false;
  lossReportError = '';

  caseForm: PenaltyCaseRequest = {
    loanDetailId: 0,
    incidentType: 'LOST',
    damageSeverity: 'HEAVY',
    description: '',
    materialType: 'BOOK',
    languageCategory: 'VIETNAMESE',
    stillInMarket: true,
    settlementMethod: 'REPLACEMENT',
  };
  paymentForm = { userId: '', amount: 0, method: 'CASH', note: '' };
  replacementForm = { caseId: 0, replacementBarcode: '', verificationNote: '' };
  waiverForm = {
    caseId: 0, action: 'APPROVE', waiverAmount: 0, externalDecisionMaker: '',
    decisionDocumentNo: '', decisionDate: '', reason: '',
  };
  rateForm = {
    materialType: 'BOOK', languageCategory: 'VIETNAMESE', amount: 0,
    validFrom: new Date().toISOString().slice(0, 10), validUntil: '', note: '',
  };
  adjustmentForm = { transactionId: 0, amount: 0, reason: '' };

  constructor(
    private penaltyService: PenaltyService,
    public authService: AuthService,
    private users:UserService,
    private circulation:CirculationService,
    private cdr:ChangeDetectorRef,
    @Optional() private route?: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const referenceType = this.route?.snapshot.queryParamMap.get('referenceType');
    const referenceId = this.route?.snapshot.queryParamMap.get('referenceId') || '';
    const numericReferenceId = Number(referenceId);
    if (referenceType === 'PENALTY_CASE' && Number.isSafeInteger(numericReferenceId) && numericReferenceId > 0) {
      this.highlightedCaseId = numericReferenceId;
      this.caseStatusFilter = 'ALL';
    } else if (referenceType === 'TRANSACTION' && Number.isSafeInteger(numericReferenceId) && numericReferenceId > 0) {
      this.adjustmentForm.transactionId = numericReferenceId;
    } else if (referenceType === 'USER' && referenceId) {
      this.paymentForm.userId = referenceId;
    } else if (referenceType === 'LOAN_LOSS_REPORT' && Number.isSafeInteger(numericReferenceId) && numericReferenceId > 0) {
      this.lossReportId = numericReferenceId;
      this.loadLossReport();
    }
    this.load();
  }

  get isAdmin(): boolean {
    return this.authService.getRole() === 'ADMIN';
  }

  get lossReportItem(): LoanItem | undefined { return this.lossReport?.items[0]; }

  get lossReportCase(): PenaltyCase | undefined {
    return this.cases.find(item => item.loanDetailId === this.lossReportId && !['CANCELLED', 'REJECTED'].includes(item.status));
  }

  get canPrepareLossReport(): boolean {
    const item = this.lossReportItem;
    return !!item && !this.lossReportCase && !item.returnDate && !item.physicalReturnAt
      && ['BORROWING', 'OVERDUE'].includes(item.itemStatus || '')
      && ['BORROWING', 'OVERDUE'].includes(this.lossReport?.status || '');
  }

  loadLossReport(): void {
    if (!this.lossReportId || this.lossReportLoading) return;
    this.lossReportLoading = true;
    this.lossReportError = '';
    this.circulation.getLossReport(this.lossReportId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: report => {
        this.lossReportLoading = false;
        if (!report || !Array.isArray(report.items) || report.items.length !== 1 || report.items[0].detailId !== this.lossReportId
            || !report.items[0].lossReportedAt || !report.userId) {
          this.lossReportError = 'Thông tin báo mất không khớp tài liệu. Vui lòng tải lại.';
        } else this.lossReport = report;
        this.cdr.markForCheck();
      },
      error: () => {
        this.lossReportLoading = false;
        this.lossReportError = 'Không tải được báo mất. Vui lòng thử lại.';
        this.cdr.markForCheck();
      },
    });
  }

  prepareLossReport(): void {
    if (!this.canPrepareLossReport || this.submitting || this.caseLookupLoading) return;
    if (!this.lossReport?.mssv) {
      this.lossReportError = 'Bạn đọc chưa có MSSV; cần đối chiếu hồ sơ trước khi lập hồ sơ phạt.';
      return;
    }
    this.caseMssv = this.lossReport.mssv;
    this.lookupCaseReader(this.lossReport);
  }

  openLossReportCase(): void {
    if (!this.lossReportCase) return;
    this.highlightedCaseId = this.lossReportCase.caseId;
    this.caseStatusFilter = 'ALL';
    this.focusReferencedCase();
  }

  get filteredCases(): PenaltyCase[] {
    return this.cases.filter(item => {
      const total = Number(item.totalAmount || 0);
      const paid = Number(item.paidAmount || 0);
      if (this.caseStatusFilter === 'UNPAID') return total > 0 && paid <= 0;
      if (this.caseStatusFilter === 'PARTIAL') return paid > 0 && paid < total;
      if (this.caseStatusFilter === 'PAID') return total <= 0 || paid >= total || ['PAID', 'WAIVED'].includes(item.status);
      return true;
    });
  }

  paymentStatusLabel(item: PenaltyCase): string {
    const total = Number(item.totalAmount || 0);
    const paid = Number(item.paidAmount || 0);
    if (total <= 0 || paid >= total || ['PAID', 'WAIVED'].includes(item.status)) return 'Đã thanh toán';
    if (paid > 0) return 'Đã thanh toán một phần';
    return 'Chưa thanh toán';
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.penaltyService.getCases().subscribe({
      next: cases => {
        this.cases = cases || [];
        this.caseItems=this.caseItems.filter(i=>!this.cases.some(c=>c.loanDetailId===i.detailId && c.status!=='CANCELLED'));
        if(!this.caseItems.some(i=>i.detailId===this.caseForm.loanDetailId))this.caseForm.loanDetailId=0;
        this.loading = false;
        this.focusReferencedCase();
        this.cdr.markForCheck();
      },
      error: error => this.fail(error, 'Không thể tải hồ sơ mất/hỏng.'),
    });
    if (this.isAdmin) {
      this.penaltyService.getAdjustments().subscribe({ next: value => this.adjustments = value || [] });
    }
  }

  createCase(): void {
    if(this.submitting)return;
    if (!this.caseReader || !this.caseItems.some(i=>i.detailId===this.caseForm.loanDetailId) || !this.caseForm.description.trim()) {
      this.errorMessage = 'Tra cứu MSSV, chọn tài liệu đang mượn và nhập mô tả sự cố.';
      return;
    }
    const payload: PenaltyCaseRequest = { ...this.caseForm, description: this.caseForm.description.trim() };
    if (payload.incidentType === 'LOST') payload.damageSeverity = undefined;
    this.run(this.penaltyService.createCase(payload), 'Đã lập hồ sơ mất/hỏng.');
  }

  pay(): void {
    if(this.submitting)return;
    if (!this.paymentReader || this.paymentReader.student.userId!==this.paymentForm.userId || this.paymentForm.amount <= 0) {
      this.errorMessage = 'Tra cứu MSSV người nộp và nhập số tiền thu hợp lệ.';
      return;
    }
    this.submitting = true;
    this.errorMessage = '';
    this.penaltyService.recordPayment({
      ...this.paymentForm,
      userId: this.paymentForm.userId.trim(),
      idempotencyKey: crypto.randomUUID(),
    }).subscribe({
      next: receipt => {
        this.receipt = receipt;
        this.submitting = false;
        this.successMessage = `Đã lập biên nhận ${receipt.receiptNumber}.`;
        if(this.paymentReader){this.paymentReader={...this.paymentReader,card:{...this.paymentReader.card,totalDebt:receipt.remainingDebt}};}
        this.paymentForm.amount=receipt.remainingDebt;
        this.load();
      },
      error: error => this.fail(error, 'Không thể ghi nhận thanh toán.'),
    });
  }

  acceptReplacement(): void {
    const form = this.replacementForm;
    if (!form.caseId || !form.replacementBarcode.trim() || !form.verificationNote.trim()) {
      this.errorMessage = 'Cần nhập hồ sơ, barcode mới và nội dung xác minh.';
      return;
    }
    this.run(this.penaltyService.acceptReplacement(
      form.caseId, form.replacementBarcode.trim(), form.verificationNote.trim()),
      'Đã tiếp nhận sách thay thế và tạo bản sao mới.',
    );
  }

  submitReview(caseId: number): void {
    this.run(this.penaltyService.submitReview(caseId), `Hồ sơ #${caseId} đã chuyển chờ xét miễn giảm.`);
  }

  reviewWaiver(): void {
    const form = this.waiverForm;
    this.run(this.penaltyService.reviewWaiver(form.caseId, {
      action: form.action,
      waiverAmount: form.action === 'APPROVE' && form.waiverAmount > 0 ? form.waiverAmount : undefined,
      externalDecisionMaker: form.externalDecisionMaker.trim(),
      decisionDocumentNo: form.decisionDocumentNo.trim(),
      decisionDate: form.decisionDate,
      reason: form.reason.trim(),
    }), 'Đã ghi nhận quyết định miễn giảm bên ngoài.');
  }

  createRate(): void {
    const form = this.rateForm;
    this.run(this.penaltyService.createRate({
      ...form,
      validUntil: form.validUntil || undefined,
    }), 'Đã lưu biểu phí đền bù.');
  }

  requestAdjustment(): void {
    this.run(this.penaltyService.requestAdjustment(this.adjustmentForm), 'Đã gửi yêu cầu điều chỉnh cho Admin.');
  }

  reviewAdjustment(id: number, action: 'APPROVE' | 'REJECT'): void {
    this.run(this.penaltyService.reviewAdjustment(id, action), `Đã ${action === 'APPROVE' ? 'duyệt' : 'từ chối'} điều chỉnh.`);
  }

  statusLabel(status: string): string {
    return ({
      OUTSTANDING: 'Còn nợ', PENDING_REVIEW: 'Chờ miễn giảm', WAITING_REPLACEMENT: 'Chờ sách thay thế',
      PAID: 'Đã hoàn tất', WAIVED: 'Đã miễn giảm', CANCELLED: 'Đã hủy', REJECTED: 'Từ chối',
    } as Record<string, string>)[status] || status;
  }

  private focusReferencedCase(): void {
    if (!this.highlightedCaseId) return;
    const elementId = `penalty-case-${this.highlightedCaseId}`;
    globalThis.setTimeout(() => {
      document.getElementById(elementId)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  }

  private run(observable: any, message: string): void {
    if(this.submitting)return;
    this.submitting = true;
    this.errorMessage = '';
    observable.subscribe({
      next: () => {
        this.submitting = false;
        this.successMessage = message;
        this.load();
      },
      error: (error: any) => this.fail(error, 'Không thể hoàn tất thao tác.'),
    });
  }

  private fail(error: any, fallback: string): void {
    this.loading = false;
    this.submitting = false;
    const message = error?.error?.detail ?? error?.error?.message ?? error?.error;
    this.errorMessage = typeof message === 'string' && message.trim() ? message : fallback;
    this.cdr.markForCheck();
  }
  resetCaseReader(){this.caseLookupVersion++;this.caseReader=undefined;this.caseItems=[];this.caseForm.loanDetailId=0;this.caseLookupError='';this.caseLookupLoading=false;}
  resetPaymentReader(){this.paymentLookupVersion++;this.paymentReader=undefined;this.paymentForm.userId='';this.paymentLookupError='';this.paymentLookupLoading=false;}
  lookupCaseReader(source?: LoanTicketDetail):void {
    this.resetCaseReader();const mssv=this.caseMssv.trim();if(!mssv){this.caseLookupError='Nhập MSSV cần tra cứu.';return;}
    const version=this.caseLookupVersion;this.caseLookupLoading=true;
    this.users.getReaderProfile(mssv).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:profile=>{
      if(version!==this.caseLookupVersion)return;
      const active=profile.loans.filter(l=>['BORROWING','BORROWED','OVERDUE'].includes(l.status));
      const requests=active.map(l=>this.circulation.getLoan(l.ticketId));
      (requests.length?forkJoin(requests):of([])).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:tickets=>{
        if(version!==this.caseLookupVersion)return;
        this.caseReader=profile;this.caseItems=tickets.filter(t=>t.userId===profile.student.userId).flatMap(t=>t.items)
          .filter(i=>!i.returnDate && !i.physicalReturnAt && ['BORROWING','BORROWED','OVERDUE'].includes(i.itemStatus||i.status) && !this.cases.some(c=>c.loanDetailId===i.detailId && c.status!=='CANCELLED'));
        this.caseLookupLoading=false;
        if(!this.caseItems.length)this.caseLookupError='Sinh viên không có tài liệu đang mượn đủ điều kiện lập hồ sơ mới.';
        if (source) {
          const selected = this.caseItems.find(item => item.detailId === source.items[0].detailId && item.ticketId === source.ticketId);
          if (profile.student.userId !== source.userId || !selected) {
            this.caseReader = undefined;
            this.caseItems = [];
            this.caseForm.loanDetailId = 0;
            this.caseLookupError = 'Báo mất không còn khớp tài liệu đang mượn của bạn đọc. Hãy kiểm tra hồ sơ hiện tại.';
          } else {
            this.caseForm.loanDetailId = selected.detailId;
            this.caseForm.incidentType = 'LOST';
            this.caseForm.description = source.items[0].lossReportDescription || '';
            this.caseForm.settlementMethod = 'REPLACEMENT';
          }
        }
        this.cdr.markForCheck();
      },error:()=>{if(version!==this.caseLookupVersion)return;this.caseLookupLoading=false;this.caseLookupError='Không tải được các tài liệu đang mượn. Hãy tra cứu lại.';this.cdr.markForCheck();}});
    },error:()=>{if(version!==this.caseLookupVersion)return;this.caseLookupLoading=false;this.caseLookupError='Không tìm thấy MSSV hoặc không tải được hồ sơ sinh viên.';this.cdr.markForCheck();}});
  }
  lookupPaymentReader():void {
    this.resetPaymentReader();const mssv=this.paymentMssv.trim();if(!mssv){this.paymentLookupError='Nhập MSSV người nộp.';return;}
    const version=this.paymentLookupVersion;this.paymentLookupLoading=true;
    this.users.getReaderProfile(mssv).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:profile=>{
      if(version!==this.paymentLookupVersion)return;this.paymentReader=profile;this.paymentForm.userId=profile.student.userId;
      this.paymentForm.amount=profile.card.totalDebt;this.paymentLookupLoading=false;this.cdr.markForCheck();
    },error:()=>{if(version!==this.paymentLookupVersion)return;this.paymentLookupLoading=false;this.paymentLookupError='Không tìm thấy MSSV hoặc không tải được công nợ.';this.cdr.markForCheck();}});
  }
}
