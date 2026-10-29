import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  LibraryPolicy as LibraryPolicyModel,
  LibraryPolicyHistory,
  LibraryPolicyService,
} from '../../../services/library-policy.service';

type PolicyValueKey =
  'maximumOpenItems' | 'homeLoanDays' | 'renewalDays' | 'maximumRenewalCount'
  | 'waitlistHoldHours' | 'homeLateFinePerDay' | 'onsiteLateFinePerDay'
  | 'technicalProcessingFee' | 'compensationSettlementDays';

interface PolicyChange {
  label: string;
  before: string;
  after: string;
}

@Component({
  selector: 'app-library-policy',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './library-policy.html',
  styleUrl: './library-policy.css',
})
export class LibraryPolicy implements OnInit {
  policy: LibraryPolicyModel | null = null;
  history: LibraryPolicyHistory[] = [];
  loading = false;
  loadingHistory = false;
  saving = false;
  error = '';
  historyError = '';
  success = '';

  private readonly historyFields: Array<{ key: PolicyValueKey; label: string; unit: string; money?: boolean }> = [
    { key: 'maximumOpenItems', label: 'Số tài liệu tối đa', unit: 'tài liệu' },
    { key: 'homeLoanDays', label: 'Thời hạn mượn về nhà', unit: 'ngày' },
    { key: 'renewalDays', label: 'Thời gian mỗi lần gia hạn', unit: 'ngày' },
    { key: 'maximumRenewalCount', label: 'Số lần gia hạn tối đa', unit: 'lần' },
    { key: 'waitlistHoldHours', label: 'Thời gian giữ sách', unit: 'giờ' },
    { key: 'homeLateFinePerDay', label: 'Phạt mượn về quá hạn', unit: 'VNĐ/ngày', money: true },
    { key: 'onsiteLateFinePerDay', label: 'Phạt đọc tại chỗ quá hạn', unit: 'VNĐ/ngày', money: true },
    { key: 'technicalProcessingFee', label: 'Phí xử lý kỹ thuật', unit: 'VNĐ', money: true },
    { key: 'compensationSettlementDays', label: 'Thời hạn đền bù', unit: 'ngày' },
  ];

  constructor(private service: LibraryPolicyService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.load();
    this.loadHistory();
  }

  loadHistory(): void {
    this.loadingHistory = true;
    this.historyError = '';
    this.service.getHistory().subscribe({
      next: value => {
        this.history = value;
        this.loadingHistory = false;
        this.cdr.detectChanges();
      },
      error: error => {
        this.loadingHistory = false;
        this.historyError = error?.error?.message || error?.error?.detail || 'Không thể tải lịch sử thay đổi.';
        this.cdr.detectChanges();
      },
    });
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.service.getPolicy().subscribe({
      next: value => {
        this.policy = value;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: error => this.fail(error, 'Không thể tải quy định thư viện.'),
    });
  }

  save(): void {
    if (!this.policy || !this.isValid()) return;
    this.saving = true;
    this.error = '';
    this.success = '';
    this.service.updatePolicy(this.policy).subscribe({
      next: value => {
        this.policy = value;
        this.saving = false;
        this.success = 'Đã cập nhật quy định. Các giao dịch mới sẽ áp dụng ngay.';
        this.loadHistory();
        this.cdr.detectChanges();
      },
      error: error => this.fail(error, 'Không thể lưu quy định thư viện.'),
    });
  }

  changesFor(index: number): PolicyChange[] {
    const current = this.history[index];
    const previous = this.history[index + 1];
    if (!current || !previous) return [];
    return this.historyFields
      .filter(field => Number(current[field.key]) !== Number(previous[field.key]))
      .map(field => ({
        label: field.label,
        before: this.formatValue(previous[field.key], field.unit, field.money),
        after: this.formatValue(current[field.key], field.unit, field.money),
      }));
  }

  trackHistory(_: number, item: LibraryPolicyHistory): number {
    return item.historyId;
  }

  private formatValue(value: number, unit: string, money = false): string {
    const formatted = money
      ? new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value))
      : String(value);
    return `${formatted} ${unit}`;
  }

  private isValid(): boolean {
    const values = [
      this.policy!.maximumOpenItems,
      this.policy!.homeLoanDays,
      this.policy!.renewalDays,
      this.policy!.waitlistHoldHours,
      this.policy!.compensationSettlementDays,
    ];
    const money = [
      this.policy!.homeLateFinePerDay,
      this.policy!.onsiteLateFinePerDay,
      this.policy!.technicalProcessingFee,
    ];
    if (values.some(value => !Number.isFinite(Number(value)) || Number(value) < 1)
        || !Number.isFinite(Number(this.policy!.maximumRenewalCount))
        || Number(this.policy!.maximumRenewalCount) < 0
        || money.some(value => !Number.isFinite(Number(value)) || Number(value) < 0)) {
      this.error = 'Vui lòng nhập số hợp lệ; thời hạn phải lớn hơn 0 và mức phí không được âm.';
      return false;
    }
    return true;
  }

  private fail(error: any, fallback: string): void {
    this.loading = false;
    this.saving = false;
    this.error = error?.error?.message || error?.error?.detail || fallback;
    this.cdr.detectChanges();
  }
}
