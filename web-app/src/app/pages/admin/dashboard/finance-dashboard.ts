import { AnalyticsIntervalPipe } from './analytics-time';
import { AnalyticsChart, ChartPoint } from './analytics-chart';
import { Component, ElementRef, Input, OnChanges, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DashboardAnalyticsService } from '../../../services/dashboard-analytics.service';
import * as M from '../../../services/dashboard-analytics.models';
import { AnalyticsPanel, AnalyticsPager, LoadState } from './analytics-shared';
@Component({
  selector: 'app-finance-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, AnalyticsPager, AnalyticsChart, AnalyticsIntervalPipe],
  templateUrl: './finance-dashboard.html',
  styleUrls: ['./analytics.css'],
})
export class FinanceDashboard extends AnalyticsPanel implements OnChanges {
  @ViewChild('entrySection') entrySection?: ElementRef<HTMLElement>;
  @Input({ required: true }) range!: M.AnalyticsRange;
  report = new LoadState<M.FinanceReport>();
  entries = new LoadState<M.ItemPage<M.FinanceEvent>>();
  allocations = new LoadState<M.ItemPage<M.Allocation>>();
  page = 0;
  allocationPage = 0;
  paymentId?: number;
  kind: M.EventKind | '' = '';
  readonly kinds: { id: M.EventKind | ''; label: string }[] = [
    { id: '', label: 'Mọi chỉ tiêu' },
    { id: 'CHARGE', label: 'Phạt phát sinh' },
    { id: 'PAYMENT', label: 'Tiền mới thu' },
    { id: 'DEPOSIT_APPLICATION', label: 'Cấn cọc' },
    { id: 'WAIVER', label: 'Miễn giảm' },
    { id: 'ADJUSTMENT', label: 'Điều chỉnh' },
    { id: 'DEPOSIT_TOPUP', label: 'Nạp cọc' },
  ];
  readonly metrics: { key: keyof M.Money; label: string; kind: M.EventKind }[] = [
    { key: 'chargesAccrued', label: 'Phạt/phí phát sinh', kind: 'CHARGE' },
    { key: 'moneyReceived', label: 'Tiền mới thu', kind: 'PAYMENT' },
    { key: 'depositApplied', label: 'Cọc đã cấn', kind: 'DEPOSIT_APPLICATION' },
    { key: 'waived', label: 'Miễn giảm', kind: 'WAIVER' },
    { key: 'adjusted', label: 'Điều chỉnh giảm nợ (có dấu)', kind: 'ADJUSTMENT' },
    { key: 'depositTopups', label: 'Nạp cọc riêng', kind: 'DEPOSIT_TOPUP' },
  ];
  chart: ChartPoint[] = [];
  constructor(private api: DashboardAnalyticsService) {
    super();
  }
  ngOnChanges() {
    this.loadReport();
    this.loadEntries(0);
    this.paymentId = undefined;
  }
  loadReport() {
    this.load(
      this.report,
      this.api.finance(this.range),
      (r) =>
        (this.chart = r.buckets.map((b) => ({
          label: b.start,
          until: b.until,
          values: [
            b.amounts.chargesAccrued,
            b.amounts.moneyReceived,
            b.amounts.depositApplied,
            b.amounts.adjusted,
          ],
        }))),
    );
  }
  loadEntries(page = 0) {
    this.page = page;
    this.paymentId = undefined;
    this.load(this.entries, this.api.entries(this.range, this.kind, page));
  }
  drill(kind: M.EventKind) {
    this.kind = kind;
    this.loadEntries(0);
    const section = this.entrySection?.nativeElement;
    section?.focus({ preventScroll: true });
    section?.scrollIntoView?.({ block: 'start' });
  }
  loadAllocations(id: number, page = 0) {
    this.paymentId = id;
    this.allocationPage = page;
    this.load(this.allocations, this.api.allocations(id, page));
  }
  label(kind: M.EventKind) {
    return this.kinds.find((k) => k.id === kind)?.label || kind;
  }
  feeLabel(value: string) {
    return (
      (
        {
          FINE: 'Phạt trễ hạn',
          REPAIR_FEE: 'Sửa sách hỏng',
          COMPENSATION: 'Bồi thường',
          TECHNICAL_FEE: 'Phí thư viện',
          PAYMENT: 'Thanh toán',
          DEPOSIT_TOPUP: 'Nạp cọc',
          WAIVER_ADJUSTMENT: 'Miễn giảm',
          ADJUSTMENT: 'Điều chỉnh',
        } as Record<string, string>
      )[value] || value
    );
  }
  methodLabel(value: string | null) {
    return value
      ? (
          {
            CASH: 'Tiền mặt',
            BANK_TRANSFER: 'Chuyển khoản',
            VNPAY: 'VNPAY',
            MOCK: 'Giả lập',
            SECURITY_DEPOSIT: 'Cấn cọc',
          } as Record<string, string>
        )[value] || value
      : 'Chưa ghi nhận phương thức';
  }
  get hasCoverageIssues() {
    return Object.values(this.report.data?.coverage || {}).some((value) => value !== 0);
  }
}
