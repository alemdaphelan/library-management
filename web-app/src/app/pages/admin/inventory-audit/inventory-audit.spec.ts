import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { InventoryAudit } from './inventory-audit';
import {
  InventoryAudit as InventoryAuditModel,
  InventoryAuditService,
  InventoryScan,
} from '../../../services/inventory-audit.service';

describe('InventoryAudit', () => {
  let component: InventoryAudit;
  let fixture: ComponentFixture<InventoryAudit>;

  const scan: InventoryScan = {
    detailId: 'detail-1',
    auditId: 9,
    barcode: 'BC-001',
    scanStatus: 'MATCHED',
    expectedLocation: 'Tầng 1 / Dãy A / Ngăn 01',
    scannedLocation: 'Tầng 1 / Dãy A / Ngăn 01',
    bookId: 2,
    bookTitle: 'Clean Code',
    copyStatus: 'AVAILABLE',
    scannedAt: '2026-10-17T10:00:00',
  };
  const audit: InventoryAuditModel = {
    auditId: 9,
    conductedBy: 'user-1',
    totalSystemBooks: 10,
    totalActualBooks: 1,
    status: 'IN_PROGRESS',
    createdAt: '2026-10-17T09:00:00',
    updatedAt: '2026-10-17T10:00:00',
    details: [scan],
  };

  const service = {
    getAudits: vi.fn(() => of([audit])),
    getAudit: vi.fn(() => of(audit)),
    createAudit: vi.fn(() => of(audit)),
    scan: vi.fn(() => of(scan)),
    complete: vi.fn(() => of({ ...audit, status: 'COMPLETED' as const })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [InventoryAudit],
      providers: [{ provide: InventoryAuditService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(InventoryAudit);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads and renders the current audit with real scan results', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;

    expect(service.getAudits).toHaveBeenCalled();
    expect(service.getAudit).toHaveBeenCalledWith(9);
    expect(text).toContain('Clean Code');
    expect(text).toContain('Đúng vị trí');
    expect(text).toContain('1 / 10 bản sao');
  });

  it('trims and submits a barcode before refreshing the audit', () => {
    component.barcode = '  BC-001  ';
    component.scannedLocation = ' Tầng 1 / Dãy A / Ngăn 01 ';

    component.submitScan();

    expect(service.scan).toHaveBeenCalledWith(9, 'BC-001', 'Tầng 1 / Dãy A / Ngăn 01');
    expect(service.getAudit).toHaveBeenCalledWith(9);
    expect(component.barcode).toBe('');
  });

  it('starts a new server-backed audit session', () => {
    component.startAudit();

    expect(service.createAudit).toHaveBeenCalled();
    expect(component.selectedAudit?.auditId).toBe(9);
    expect(component.successMessage).toContain('Đã bắt đầu');
  });
});
