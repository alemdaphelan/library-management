import { ChangeDetectorRef, Component, Directive, EventEmitter, Input, Output, inject, OnDestroy } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
export class LoadState<T> { data?: T; busy=false; error=''; }
@Directive()
export abstract class AnalyticsPanel implements OnDestroy {
  private readonly cdr=inject(ChangeDetectorRef);
  private readonly requests=new Map<object,Subscription>();
  load<T>(state: LoadState<T>, source: Observable<T>, done?: (data:T)=>void) {
    this.requests.get(state)?.unsubscribe();state.data=undefined;state.busy=true;state.error='';
    const request=source.subscribe({next:data=>{state.data=data;state.busy=false;done?.(data);this.cdr.markForCheck();},
      error:()=>{state.busy=false;state.error='Không thể tải dữ liệu. Thử lại để đối chiếu số liệu.';this.cdr.markForCheck();}});
    this.requests.set(state,request);
  }
  ngOnDestroy(){for(const r of this.requests.values())r.unsubscribe();}
}
@Component({selector:'app-analytics-pager',standalone:true,template:`<div class="pager"><button type="button" [disabled]="busy || page===0" (click)="move.emit(page-1)">Trước</button><span>Trang {{page+1}} · {{total}} dòng · {{size}} dòng/trang</span><button type="button" [disabled]="busy || (page+1)*size>=total" (click)="move.emit(page+1)">Sau</button></div>`,styles:[`.pager{display:flex;flex-wrap:wrap;align-items:center;gap:12px;margin-top:14px;font-size:.85rem}button{padding:8px 14px;border:1px solid #cbd5e1;border-radius:8px;background:white;cursor:pointer}button:disabled{opacity:.4;cursor:default}`]})
export class AnalyticsPager { @Input() page=0;@Input() size=25;@Input() total=0;@Input() busy=false;@Output() move=new EventEmitter<number>(); }
