import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  InventoryAudit as InventoryAuditModel,
  InventoryAuditService,
  InventoryScanStatus,
} from '../../../services/inventory-audit.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'app-inventory-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './inventory-audit.html',
  styleUrl: './inventory-audit.css',
})
export class InventoryAudit implements OnInit {
  @ViewChild('barcodeInput') barcodeInput?: ElementRef<HTMLInputElement>;

  audits: InventoryAuditModel[] = [];
  selectedAudit?: InventoryAuditModel;
  barcode = '';
  scannedLocation = '';
  isLoading = false;
  isSubmitting = false;
  successMessage = '';
  errorMessage = '';

  constructor(
    private readonly inventoryAuditService: InventoryAuditService,
    private readonly dialog: DialogService,
  ) {}

  ngOnInit(): void {
    this.loadAudits();
  }

  loadAudits(preferredAuditId?: number): void {
    this.isLoading = true;
    this.clearMessages();
    this.inventoryAuditService.getAudits().subscribe({
      next: audits => {
        this.audits = audits;
        const selected = audits.find(audit => audit.auditId === preferredAuditId)
          ?? audits.find(audit => audit.status === 'IN_PROGRESS')
          ?? audits[0];
        if (selected) {
          this.selectAudit(selected);
        } else {
          this.selectedAudit = undefined;
          this.isLoading = false;
        }
      },
      error: error => this.handleError('Không thể tải lịch sử kiểm kê.', error),
    });
  }

  selectAudit(audit: InventoryAuditModel): void {
    this.isLoading = true;
    this.inventoryAuditService.getAudit(audit.auditId).subscribe({
      next: detail => {
        this.selectedAudit = detail;
        this.replaceAudit(detail);
        this.isLoading = false;
        if (detail.status === 'IN_PROGRESS') this.focusBarcode();
      },
      error: error => this.handleError('Không thể tải chi tiết phiên kiểm kê.', error),
    });
  }

  startAudit(): void {
    this.isSubmitting = true;
    this.clearMessages();
    this.inventoryAuditService.createAudit().subscribe({
      next: audit => {
        this.audits = [audit, ...this.audits];
        this.selectedAudit = audit;
        this.isSubmitting = false;
        this.successMessage = `Đã bắt đầu phiên kiểm kê #${audit.auditId}.`;
        this.focusBarcode();
      },
      error: error => this.handleError('Không thể bắt đầu phiên kiểm kê.', error),
    });
  }

  submitScan(): void {
    const cleanBarcode = this.barcode.trim();
    if (!this.selectedAudit || this.selectedAudit.status !== 'IN_PROGRESS' || !cleanBarcode) return;

    this.isSubmitting = true;
    this.clearMessages();
    this.inventoryAuditService.scan(
      this.selectedAudit.auditId,
      cleanBarcode,
      this.scannedLocation.trim(),
    ).subscribe({
      next: scan => {
        const auditId = this.selectedAudit!.auditId;
        this.barcode = '';
        this.successMessage = this.scanMessage(scan.scanStatus);
        this.inventoryAuditService.getAudit(auditId).subscribe({
          next: audit => {
            this.selectedAudit = audit;
            this.replaceAudit(audit);
            this.isSubmitting = false;
            this.focusBarcode();
          },
          error: error => this.handleError('Đã quét nhưng không thể làm mới kết quả.', error),
        });
      },
      error: error => this.handleError(this.apiError(error, 'Không thể ghi nhận barcode.'), error),
    });
  }

  async completeAudit(): Promise<void> {
    if (!this.selectedAudit || this.selectedAudit.status !== 'IN_PROGRESS') return;
    const confirmed = await this.dialog.confirm({
      title: 'Hoàn tất kiểm kê',
      message: `Hoàn tất phiên kiểm kê #${this.selectedAudit.auditId}? Sau đó sẽ không thể quét thêm.`,
      type: 'warning',
      confirmText: 'Hoàn tất',
    });
    if (!confirmed) return;

    this.isSubmitting = true;
    this.clearMessages();
    this.inventoryAuditService.complete(this.selectedAudit.auditId).subscribe({
      next: audit => {
        this.selectedAudit = audit;
        this.replaceAudit(audit);
        this.isSubmitting = false;
        this.successMessage = `Đã hoàn tất phiên kiểm kê #${audit.auditId}.`;
      },
      error: error => this.handleError('Không thể hoàn tất phiên kiểm kê.', error),
    });
  }

  missingCount(audit: InventoryAuditModel): number {
    return Math.max(0, audit.totalSystemBooks - audit.totalActualBooks);
  }

  statusLabel(status: InventoryScanStatus): string {
    return {
      MATCHED: 'Đúng vị trí',
      MISPLACED: 'Sai vị trí',
      FOUND: 'Đã tìm thấy',
      UNKNOWN: 'Không có trong hệ thống',
    }[status];
  }

  private replaceAudit(updated: InventoryAuditModel): void {
    const index = this.audits.findIndex(audit => audit.auditId === updated.auditId);
    if (index >= 0) this.audits[index] = { ...this.audits[index], ...updated };
  }

  private scanMessage(status: InventoryScanStatus): string {
    return status === 'MATCHED' ? 'Barcode đúng vị trí.'
      : status === 'MISPLACED' ? 'Đã ghi nhận bản sao đang sai vị trí.'
      : status === 'UNKNOWN' ? 'Đã ghi nhận barcode không có trong hệ thống.'
      : 'Đã ghi nhận barcode.';
  }

  private focusBarcode(): void {
    setTimeout(() => this.barcodeInput?.nativeElement.focus());
  }

  private clearMessages(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }

  private handleError(message: string, error: unknown): void {
    console.error(message, error);
    this.errorMessage = message;
    this.successMessage = '';
    this.isLoading = false;
    this.isSubmitting = false;
    this.focusBarcode();
  }

  private apiError(error: any, fallback: string): string {
    const message = error?.error?.message ?? error?.error?.detail;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
}
