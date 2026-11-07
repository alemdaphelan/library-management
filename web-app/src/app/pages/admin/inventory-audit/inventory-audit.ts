import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  InventoryAudit as InventoryAuditModel,
  InventoryAuditService,
  InventoryScanStatus,
} from '../../../services/inventory-audit.service';
import { DialogService } from '../../../services/dialog.service';
import { DisposalWorkspace } from './disposal-workspace';
import { LocationNode, LocationService } from '../../../services/location.service';
import { BrowserMultiFormatReader, IScannerControls } from '@zxing/browser';

@Component({
  selector: 'app-inventory-audit',
  standalone: true,
  imports: [CommonModule, FormsModule, DisposalWorkspace],
  templateUrl: './inventory-audit.html',
  styleUrl: './inventory-audit.css',
})
export class InventoryAudit implements OnInit, OnDestroy {
  private readonly locations = inject(LocationService);
  floors: LocationNode[] = [];
  bookshelves: LocationNode[] = [];
  shelves: LocationNode[] = [];
  floorId?: number;
  bookshelfId?: number;
  shelfId?: number;
  cameraOpen = false;
  private cameraVersion = 0;
  private camera?: IScannerControls;
  private lastCameraBarcode = '';
  @ViewChild('auditVideo') auditVideo?: ElementRef<HTMLVideoElement>;
  view: 'audit' | 'disposals' = 'audit';
  disposalsLoaded = false;
  disposalAuditId?: number;
  showDisposals(fromSelected = false): void {
    this.stopCamera();
    this.disposalsLoaded = true; this.view = 'disposals';
    if (fromSelected) this.disposalAuditId = this.selectedAudit?.auditId;
  }
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
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadAudits();
    this.locations.getFloors().subscribe({ next: floors => this.floors = floors,
      error: () => this.dialog.show({type:'error',message:'Không tải được vị trí thư viện. Tải lại trang để chọn ngăn kiểm kê.'}) });
  }

  ngOnDestroy(): void { this.stopCamera(); }

  selectFloor(id?: number): void {
    this.floorId = id;
    this.bookshelves = [];
    this.selectBookshelf();
    if (id) this.locations.getBookshelves(id).subscribe({
      next: shelves => { if (this.floorId === id) this.bookshelves = shelves; },
      error: () => this.dialog.show({type:'error',message:'Không tải được dãy kệ. Chọn lại tầng để thử lại.'}),
    });
  }

  selectBookshelf(id?: number): void {
    this.bookshelfId = id;
    this.shelves = [];
    this.selectShelf();
    if (id) this.locations.getShelves(id).subscribe({
      next: shelves => { if (this.bookshelfId === id) this.shelves = shelves; },
      error: () => this.dialog.show({type:'error',message:'Không tải được ngăn kệ. Chọn lại dãy kệ để thử lại.'}),
    });
  }

  selectShelf(id?: number): void {
    this.shelfId = id;
    this.scannedLocation = [this.floors.find(n => n.id === this.floorId)?.name,
      this.bookshelves.find(n => n.id === this.bookshelfId)?.name, this.shelves.find(n => n.id === id)?.name]
      .filter(Boolean).join(' / ');
    this.lastCameraBarcode = '';
    if (!id) this.stopCamera();
  }

  async startCamera(): Promise<void> {
    if (this.cameraOpen || !this.shelfId || this.selectedAudit?.status !== 'IN_PROGRESS') return;
    const version = ++this.cameraVersion;
    this.cameraOpen = true;
    this.lastCameraBarcode = '';
    this.cdr.detectChanges();
    const video = this.auditVideo?.nativeElement;
    if (!video) { this.stopCamera(); return; }
    try {
      const reader = new BrowserMultiFormatReader(undefined, {delayBetweenScanAttempts: 120, delayBetweenScanSuccess: 350});
      const controls = await reader.decodeFromConstraints({video:{facingMode:{ideal:'environment'}},audio:false}, video, result => {
        const value = result?.getText().trim();
        if (version !== this.cameraVersion || !value || this.isSubmitting || value === this.lastCameraBarcode
            || this.selectedAudit?.details.some(scan => scan.barcode === value)) return;
        this.lastCameraBarcode = value;
        this.barcode = value;
        this.submitScan();
      });
      if (version !== this.cameraVersion) controls.stop(); else this.camera = controls;
    } catch {
      if (version !== this.cameraVersion) return;
      this.stopCamera();
      this.dialog.show({type:'error',message:'Không mở được camera. Kiểm tra quyền camera hoặc dùng máy quét mã vạch.'});
    }
  }

  stopCamera(): void {
    this.cameraVersion++;
    this.camera?.stop();
    this.camera = undefined;
    this.cameraOpen = false;
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
          this.cdr.detectChanges();
        }
      },
      error: error => this.handleError('Không thể tải lịch sử kiểm kê.', error),
    });
  }

  selectAudit(audit: InventoryAuditModel): void {
    if (this.isSubmitting) return;
    this.stopCamera();
    this.isLoading = true;
    this.inventoryAuditService.getAudit(audit.auditId).subscribe({
      next: detail => {
        this.selectedAudit = detail;
        this.replaceAudit(detail);
        this.isLoading = false;
        if (detail.status === 'IN_PROGRESS') this.focusBarcode();
        this.cdr.detectChanges();
      },
      error: error => this.handleError('Không thể tải chi tiết phiên kiểm kê.', error),
    });
  }

  startAudit(): void {
    if (this.isSubmitting) return;
    this.stopCamera();
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
    if (this.isSubmitting || !this.selectedAudit || this.selectedAudit.status !== 'IN_PROGRESS' || !cleanBarcode || !this.shelfId) return;

    this.isSubmitting = true;
    this.clearMessages();
    this.inventoryAuditService.scan(
      this.selectedAudit.auditId,
      cleanBarcode,
      this.scannedLocation.trim(),
      this.shelfId,
    ).subscribe({
      next: scan => {
        const auditId = this.selectedAudit!.auditId;
        this.barcode = '';
        const message = `${scan.barcode} · ${this.scanMessage(scan.scanStatus)}`;
        if (scan.scanStatus === 'MISPLACED') this.errorMessage = `${message} Vị trí đúng: ${scan.expectedLocation || 'Chưa xác định'}.`;
        else this.successMessage = message;
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
    if (this.isSubmitting || !this.selectedAudit || this.selectedAudit.status !== 'IN_PROGRESS') return;
    this.stopCamera();
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
    if (!this.cameraOpen) setTimeout(() => this.barcodeInput?.nativeElement.focus());
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
