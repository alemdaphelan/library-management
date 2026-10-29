import { TestBed, ComponentFixture } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { Dashboard } from './dashboard';
import { CirculationDashboard } from './circulation-dashboard';
import { HoldingsDashboard } from './holdings-dashboard';
import { FinanceDashboard } from './finance-dashboard';
import { StockDashboard } from './stock-dashboard';
import { DashboardAnalyticsService } from '../../../services/dashboard-analytics.service';
import { DigitalAssetsService } from '../../../services/digital-assets.service';
import { ImportsService } from '../../../services/imports.service';
import { ReportFileSaver } from '../../../services/report-file-saver';
import { CirculationReport } from '../../../services/dashboard-analytics.models';
import * as F from './dashboard-test-data';

describe('Librarian dashboard B14–B15',()=>{
  let fixture:ComponentFixture<Dashboard>;
  let api:ReturnType<typeof mockApi>;
  function mockApi(){return {
    circulation:vi.fn<DashboardAnalyticsService['circulation']>(()=>of(F.circulation)),rankings:vi.fn<DashboardAnalyticsService['rankings']>(()=>of(F.ranks)),overdue:vi.fn<DashboardAnalyticsService['overdue']>(()=>of(F.late)),
    catalog:vi.fn(()=>of(F.catalog)),holdings:vi.fn(()=>of(F.holdings)),materials:vi.fn(()=>of(F.materials)),
    copies:vi.fn(()=>of({...F.snapshot,page:0,size:25,total:1,items:[{barcode:'BC-001',status:'AVAILABLE',conditionNote:null,location:'Tầng 1 / Kệ A / Ngăn 1'}]})),
    assets:vi.fn(()=>of({...F.snapshot,page:0,size:25,total:1,items:[{id:3,name:'giáo trình.pdf',format:'PDF',size:100,accessLevel:'STAFF_ONLY'}]})),
    classification:vi.fn(()=>of({current:{version:1,sourceReference:'Biên bản khoa 12',majors:F.catalog.majors,updatedByName:'Thủ thư A',updatedAt:F.snapshot.snapshotAt},history:[]})),
    finance:vi.fn<DashboardAnalyticsService['finance']>(()=>of(F.finance)),entries:vi.fn(()=>of({...F.snapshot,page:0,size:25,total:26,items:[F.entry]})),
    allocations:vi.fn(()=>of({...F.snapshot,page:0,size:25,total:1,items:[{allocationId:1,debtTransactionId:2,feeType:'REPAIR_FEE',amount:150000,eligible:true}]})),
    stock:vi.fn<DashboardAnalyticsService['stock']>(()=>of(F.stock)),receipts:vi.fn(()=>of({...F.snapshot,page:0,size:25,total:26,items:[{receiptId:8,receiptCode:'PN-8',completedAt:F.snapshot.snapshotAt,importedTitles:1,importedCopies:3}]})),
  };}
  const imports={getReceipt:vi.fn(()=>of({receiptId:8,receiptCode:'PN-8',supplierName:'NXB A',quantity:3,status:'COMPLETED',lines:[{title:'Giáo trình C++',isbn:'9780132350884',quantity:3,locationPath:'Tầng 1 / Kệ A',barcodes:['BC-001']}]}))};
  beforeEach(async()=>{
    api=mockApi();imports.getReceipt.mockClear();
    await TestBed.configureTestingModule({imports:[Dashboard],providers:[provideRouter([]),{provide:DashboardAnalyticsService,useValue:api},{provide:DigitalAssetsService,useValue:{downloadAsset:vi.fn(()=>of({body:new Blob(['pdf'])}))}},{provide:ImportsService,useValue:imports},{provide:ReportFileSaver,useValue:{save:vi.fn()}}]}).compileComponents();
    fixture=TestBed.createComponent(Dashboard);render();await fixture.whenStable();render();
  });
  function child<T>(type:new(...args:any[])=>T):T{return fixture.debugElement.query(By.directive(type)).componentInstance;}
  function render(){fixture.changeDetectorRef.markForCheck();fixture.detectChanges();}
  function text(){return fixture.nativeElement.textContent as string;}
  async function tab(id:string){fixture.componentInstance.tab=id;render();await fixture.whenStable();render();}
  it('loads only initial circulation and shows item/ticket distinction, zero bucket and real report links',()=>{
    expect(api.circulation).toHaveBeenCalledTimes(1);expect(api.finance).not.toHaveBeenCalled();
    expect(text()).toContain('Tài liệu đã giao');expect(text()).toContain('Phiếu có giao');expect(text()).toContain('Lịch sử chưa đủ chứng cứ');
    const links=Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    expect(links.some(a=>a.getAttribute('href')?.includes('view=report')&&a.getAttribute('href')?.includes('q=2001240001'))).toBe(true);
  });
  it('applies the same half-open range and period to report and rankings only after confirmation',()=>{
    const d=fixture.componentInstance;d.draft={from:'2026-01-03',until:'2026-04-02',period:'QUARTER'};render();
    expect(api.circulation).toHaveBeenCalledTimes(1);expect(text()).toContain('Ngày chưa áp dụng');
    d.apply();render();expect(api.circulation).toHaveBeenLastCalledWith(d.range);expect(api.rankings).toHaveBeenLastCalledWith(d.range,'MOST_BORROWED',0);
    expect(api.overdue).toHaveBeenLastCalledWith(0);
  });
  it('rejects reversed, impossible and overlong dates without fetching',()=>{
    const d=fixture.componentInstance;
    for(const [from,until] of [['2026-03-01','2026-01-01'],['2026-02-31','2026-04-01'],['1900-01-01','2026-01-01']]){d.draft={from,until,period:'MONTH'};d.apply();expect(d.error).toBeTruthy();}
    expect(api.circulation).toHaveBeenCalledTimes(1);
  });
  it('clears old totals on loading/error, keeps independent sections, and retries',()=>{
    const p=child(CirculationDashboard);api.circulation.mockReturnValueOnce(throwError(()=>new Error('offline')));p.loadReport();render();
    expect(p.report.data).toBeUndefined();expect(text()).toContain('Không thể tải dữ liệu');expect(text()).toContain('Giáo trình C++');
    p.loadReport();render();expect(p.report.data?.totals.checkoutItems).toBe(3);
  });
  it('cancels stale requests when range changes and when leaving a tab',async()=>{
    const old=new Subject<CirculationReport>();const next=new Subject<CirculationReport>();api.circulation.mockReturnValueOnce(old).mockReturnValueOnce(next);
    const p=child(CirculationDashboard);p.loadReport();p.loadReport();old.next({...F.circulation,totals:{...F.counts,checkoutItems:99}});expect(p.report.data).toBeUndefined();
    next.next(F.circulation);expect(p.report.data?.totals.checkoutItems).toBe(3);
    await tab('finance');expect(next.observed).toBe(false);
  });
  it('requests server ranking pages and resets the page for each mode',()=>{
    const p=child(CirculationDashboard);p.loadRanks(1);expect(api.rankings).toHaveBeenLastCalledWith(fixture.componentInstance.range,'MOST_BORROWED',1);
    p.ranking='CURRENTLY_BORROWED';p.loadRanks(0);expect(api.rankings).toHaveBeenLastCalledWith(fixture.componentInstance.range,'CURRENTLY_BORROWED',0);
  });
  it('loads holdings with shared server filters/search, marks unapplied edits and paginates',async()=>{
    await tab('holdings');const p=child(HoldingsDashboard);p.filter={facultyId:'FIT',majorId:'IT1',type:'HYBRID',state:'ALL',q:' C++ '};render();expect(text()).toContain('Bộ lọc đã đổi');
    p.apply();expect(api.holdings).toHaveBeenLastCalledWith({...p.filter,q:'C++'});expect(api.materials).toHaveBeenLastCalledWith({...p.filter,q:'C++'},0);
    p.loadList(1);expect(api.materials).toHaveBeenLastCalledWith(p.applied,1);render();expect(text()).toContain('Chưa phân loại');expect(text()).toContain('Snapshot hiện tại');
  });
  it('warns about physical copies outside the current catalog without inventing faculty totals',async()=>{
    api.holdings.mockReturnValueOnce(of({...F.holdings,coverage:{totalPhysicalCopies:103,copiesWithoutCurrentCatalog:100,linkedDigitalFilesWithoutCurrentCatalog:0}}));
    await tab('holdings');expect(text()).toContain('100 bản chưa liên kết');expect(text()).toContain('Bản sao thuộc đầu đã chọn');
  });
  it('shows material position, files/access level and classification source without writing',async()=>{
    await tab('holdings');child(HoldingsDashboard).open(F.material);render();
    expect(api.copies).toHaveBeenCalledWith(F.material,0);expect(text()).toContain('Tầng 1 / Kệ A / Ngăn 1');expect(text()).toContain('Chỉ nhân viên');expect(text()).toContain('Biên bản khoa 12');
  });
  it('shows current debt independently and preserves negative adjustments',async()=>{
    await tab('finance');expect(text()).toContain('Không phải dư nợ cuối kỳ');expect(text()).toMatch(/-5[,.]000/);expect(text()).toMatch(/150[,.]000/);
    const p=child(FinanceDashboard);p.drill('DEPOSIT_APPLICATION');expect(api.entries).toHaveBeenLastCalledWith(fixture.componentInstance.range,'DEPOSIT_APPLICATION',0);
    p.loadEntries(1);expect(api.entries).toHaveBeenLastCalledWith(fixture.componentInstance.range,'DEPOSIT_APPLICATION',1);
  });
  it('opens paged allocations for the selected receipt',async()=>{
    await tab('finance');child(FinanceDashboard).loadAllocations(5);render();expect(api.allocations).toHaveBeenCalledWith(5,0);expect(text()).toContain('Phân bổ receipt 5');expect(text()).toContain('REPAIR_FEE');
  });
  it('shows unavailable disposal data and opens actual receipt detail',async()=>{
    await tab('stock');expect(text()).toContain('Chờ Admin');expect(text()).toContain('Chưa có dữ liệu');expect(text()).toContain('2 phiếu hoàn tất thiếu ngày');
    child(StockDashboard).open(8);render();expect(imports.getReceipt).toHaveBeenCalledWith(8);expect(text()).toContain('BC-001');
  });
  it('keeps one applied range across financial/import tabs; failed finance never becomes zero',async()=>{
    const d=fixture.componentInstance;d.draft={from:'2026-02-01',until:'2026-07-01',period:'QUARTER'};d.apply();render();
    api.finance.mockReturnValueOnce(throwError(()=>new Error('offline')));await tab('finance');expect(api.finance).toHaveBeenLastCalledWith(d.range);expect(text()).toContain('Không thể tải dữ liệu');expect(text()).not.toContain('0 ₫');
    await tab('stock');expect(api.stock).toHaveBeenLastCalledWith(d.range);
  });
});
