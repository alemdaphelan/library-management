import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { CirculationService } from '../../../services/circulation.service';
import { RecallRequests } from './recall-requests';

describe('RecallRequests',()=>{
  let fixture:ComponentFixture<RecallRequests>;let component:RecallRequests;
  const item:any={ticketId:33,detailId:45,userId:'reader-1',mssv:'2001240001',readerName:'Sinh viên A',bookTitle:'Giáo trình',barcode:'BC-45',dueDate:'2026-11-18T10:00:00',itemStatus:'BORROWING',isRecalled:false};
  const result:any={...item,status:'BORROWING',isRecalled:true,dueDate:'2026-11-11T10:00:00',recallDueDate:'2026-11-11T10:00:00',recallPreviousDueDate:item.dueDate,recallRequestedAt:'2026-11-04T10:00:00',recallReason:'Cần học phần'};
  const api={getRecallItems:vi.fn(),requestRecall:vi.fn()};
  beforeEach(async()=>{
    vi.clearAllMocks();api.getRecallItems.mockReturnValue(of({items:[item],total:21,page:0,size:10}));api.requestRecall.mockReturnValue(of(result));
    await TestBed.configureTestingModule({imports:[RecallRequests],providers:[{provide:CirculationService,useValue:api}]}).compileComponents();
    fixture=TestBed.createComponent(RecallRequests);component=fixture.componentInstance;fixture.detectChanges();
  });
  it('loads a server page, applies filters before paging and does not send recalls on selection',()=>{
    expect(api.getRecallItems).toHaveBeenCalledWith('','ALL',0,10);
    component.changePage(1);expect(api.getRecallItems).toHaveBeenLastCalledWith('','ALL',1,10);
    component.q=' 2001240001 ';component.state='NOT_RECALLED';component.applyFilters();
    expect(api.getRecallItems).toHaveBeenLastCalledWith('2001240001','NOT_RECALLED',0,10);
    component.open(item);expect(api.requestRecall).not.toHaveBeenCalled();component.close();expect(api.requestRecall).not.toHaveBeenCalled();
  });
  it('keeps the latest filter response when a prior request arrives late',()=>{
    const old=new Subject<any>();api.getRecallItems.mockReturnValueOnce(old);component.load();
    api.getRecallItems.mockReturnValueOnce(of({items:[],total:0,page:0,size:10}));component.q='mới';component.applyFilters();
    old.next({items:[item],total:21,page:0,size:10});old.complete();expect(component.items).toEqual([]);expect(component.total).toBe(0);
  });
  it('validates reasons, locks duplicate clicks, and shows server dates only after confirmation',()=>{
    component.open(item);component.reason=' ';component.submit();component.reason='x'.repeat(1001);component.submit();expect(api.requestRecall).not.toHaveBeenCalled();
    const pending=new Subject<any>();api.requestRecall.mockReturnValueOnce(pending);const done=vi.fn();component.recalled.subscribe(done);
    component.reason=' Cần học phần ';component.submit();component.submit();component.close();
    expect(component.saving).toBe(true);expect(component.selected).not.toBeNull();expect(api.requestRecall).toHaveBeenCalledExactlyOnceWith(33,45,'Cần học phần');expect(done).not.toHaveBeenCalled();
    pending.next(result);pending.complete();fixture.changeDetectorRef.markForCheck();fixture.detectChanges();
    expect(component.selected).toBeNull();expect(component.result?.recallPreviousDueDate).toBe(item.dueDate);expect(component.result?.recallDueDate).toBe(result.recallDueDate);
    expect(done).toHaveBeenCalledTimes(1);expect(fixture.nativeElement.querySelector('.success').textContent).toContain('11/11/2026 10:00');
  });
  it('keeps the reason and same request after an ambiguous error without auto-retrying',()=>{
    component.open(item);component.reason='Cần học phần';api.requestRecall.mockReturnValueOnce(throwError(()=>({status:0})));component.submit();
    expect(component.uncertain).toBe(true);expect(component.saving).toBe(false);expect(component.reason).toBe('Cần học phần');expect(api.requestRecall).toHaveBeenCalledTimes(1);
    component.submit();expect(api.requestRecall).toHaveBeenLastCalledWith(33,45,'Cần học phần');expect(component.result).toEqual(result);
  });
  it('releases the spinner after timeout and rejects incomplete or invalid-date receipts',()=>{
    component.open(item);component.reason='Cần học phần';api.requestRecall.mockReturnValueOnce(new Subject<any>());
    vi.useFakeTimers();try{component.submit();vi.advanceTimersByTime(30000);expect(component.saving).toBe(false);expect(component.uncertain).toBe(true);}finally{vi.useRealTimers();}
    api.requestRecall.mockReturnValueOnce(of({...result,recallDueDate:'not-a-date'}));component.submit();expect(component.result).toBeNull();expect(component.formError).toContain('Chưa xác nhận');
  });
  it('prevents recalling reported losses, missing dates/readers and existing recalls',()=>{
    for(const blocked of [{...item,isRecalled:true},{...item,lossReportedAt:'2026-11-04T10:00:00'},{...item,dueDate:null},{...item,userId:null}]){
      expect(component.canRequest(blocked)).toBe(false);component.open(blocked);expect(component.selected).toBeNull();
    }
    expect(api.requestRecall).not.toHaveBeenCalled();
  });
  it('recovers list errors and clamps the page when the last item disappears from a filter',()=>{
    api.getRecallItems.mockReturnValueOnce(throwError(()=>({status:500})));component.load();expect(component.loading).toBe(false);expect(component.error).toBeTruthy();
    component.page=2;api.getRecallItems.mockReturnValueOnce(of({items:[],total:1,page:2,size:10})).mockReturnValueOnce(of({items:[item],total:1,page:0,size:10}));component.load();
    expect(component.page).toBe(0);expect(component.items).toEqual([item]);expect(api.getRecallItems).toHaveBeenLastCalledWith('','ALL',0,10);
  });
});
