import { CommonModule } from '@angular/common';
import { Component, OnInit, Optional } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { PaymentReceipt, PenaltyCase, PenaltyCaseRequest, PenaltyService } from '../../../services/penalty.service';

@Component({
  selector: 'app-penalties',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './penalties.html',
  styleUrl: './penalties.css',
})
export class Penalties implements OnInit {
  cases: PenaltyCase[] = [];
  adjustments: any[] = [];
  loading = false;
  submitting = false;
  errorMessage = '';
  successMessage = '';
  receipt: PaymentReceipt | null = null;
  caseStatusFilter: 'ALL' | 'UNPAID' | 'PARTIAL' | 'PAID' = 'ALL';
  highlightedCaseId?: number;

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
    }
    this.load();
  }

  get isAdmin(): boolean {
    return this.authService.getRole() === 'ADMIN';
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
        this.loading = false;
        this.focusReferencedCase();
      },
      error: error => this.fail(error, 'Không thể tải hồ sơ mất/hỏng.'),
    });
    if (this.isAdmin) {
      this.penaltyService.getAdjustments().subscribe({ next: value => this.adjustments = value || [] });
    }
  }

  createCase(): void {
    if (!this.caseForm.loanDetailId || !this.caseForm.description.trim()) {
      this.errorMessage = 'Cần nhập mã chi tiết mượn và mô tả sự cố.';
      return;
    }
    const payload: PenaltyCaseRequest = { ...this.caseForm, description: this.caseForm.description.trim() };
    if (payload.incidentType === 'LOST') payload.damageSeverity = undefined;
    this.run(this.penaltyService.createCase(payload), 'Đã lập hồ sơ mất/hỏng.');
  }

  pay(): void {
    if (!this.paymentForm.userId.trim() || this.paymentForm.amount <= 0) {
      this.errorMessage = 'Cần nhập user ID và số tiền thu hợp lệ.';
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
    const message = error?.error?.message ?? error?.error;
    this.errorMessage = typeof message === 'string' && message.trim() ? message : fallback;
  }
}
