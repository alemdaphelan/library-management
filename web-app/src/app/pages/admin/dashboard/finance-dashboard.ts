import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DashboardAnalyticsService } from '../../../services/dashboard-analytics.service';
import * as M from '../../../services/dashboard-analytics.models';
import { AnalyticsPanel, AnalyticsPager, LoadState } from './analytics-shared';
@Component({selector:'app-finance-dashboard',standalone:true,imports:[CommonModule,FormsModule,AnalyticsPager],templateUrl:'./finance-dashboard.html',styleUrls:['./analytics.css']})
export class FinanceDashboard extends AnalyticsPanel implements OnChanges {
  @Input({required:true}) range!:M.AnalyticsRange;
  report=new LoadState<M.FinanceReport>();entries=new LoadState<M.ItemPage<M.FinanceEvent>>();allocations=new LoadState<M.ItemPage<M.Allocation>>();
  page=0;allocationPage=0;paymentId?:number;kind:M.EventKind|''='';
  readonly kinds:{id:M.EventKind|'';label:string}[]=[{id:'',label:'Mọi chỉ tiêu'},{id:'CHARGE',label:'Phạt phát sinh'},{id:'PAYMENT',label:'Tiền mới thu'},{id:'DEPOSIT_APPLICATION',label:'Cấn cọc'},{id:'WAIVER',label:'Miễn giảm'},{id:'ADJUSTMENT',label:'Điều chỉnh'},{id:'DEPOSIT_TOPUP',label:'Nạp cọc'}];
  readonly metrics:{key:keyof M.Money;label:string;kind:M.EventKind}[]=[{key:'chargesAccrued',label:'Phạt/phí phát sinh',kind:'CHARGE'},{key:'moneyReceived',label:'Tiền mới thu',kind:'PAYMENT'},{key:'depositApplied',label:'Cọc đã cấn',kind:'DEPOSIT_APPLICATION'},{key:'waived',label:'Miễn giảm',kind:'WAIVER'},{key:'adjusted',label:'Điều chỉnh giảm nợ (có dấu)',kind:'ADJUSTMENT'},{key:'depositTopups',label:'Nạp cọc riêng',kind:'DEPOSIT_TOPUP'}];
  constructor(private api:DashboardAnalyticsService){super();}
  ngOnChanges(){this.loadReport();this.loadEntries(0);this.paymentId=undefined;}
  loadReport(){this.load(this.report,this.api.finance(this.range));}
  loadEntries(page=0){this.page=page;this.paymentId=undefined;this.load(this.entries,this.api.entries(this.range,this.kind,page));}
  drill(kind:M.EventKind){this.kind=kind;this.loadEntries(0);}
  loadAllocations(id:number,page=0){this.paymentId=id;this.allocationPage=page;this.load(this.allocations,this.api.allocations(id,page));}
  label(kind:M.EventKind){return this.kinds.find(k=>k.id===kind)?.label || kind;}
  get hasCoverageIssues(){return Object.values(this.report.data?.coverage || {}).some(value=>value!==0);}
}
