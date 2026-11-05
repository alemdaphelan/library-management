import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CirculationDashboard } from './circulation-dashboard';
import { HoldingsDashboard } from './holdings-dashboard';
import { FinanceDashboard } from './finance-dashboard';
import { StockDashboard } from './stock-dashboard';
import { AnalyticsRange, Period } from '../../../services/dashboard-analytics.models';
import { calendarRange, rangeError } from './analytics-time';
function localDate(date:Date){return new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(date);}
@Component({selector:'app-dashboard',standalone:true,imports:[CommonModule,FormsModule,CirculationDashboard,HoldingsDashboard,FinanceDashboard,StockDashboard],templateUrl:'./dashboard.html',styleUrls:['./analytics.css','./dashboard.css']})
export class Dashboard {
  readonly tabs=[{id:'circulation',label:'Mượn / trả'},{id:'holdings',label:'Danh mục / khoa ngành'},{id:'finance',label:'Tài chính'},{id:'stock',label:'Nhập / xử lý'}];
  tab='circulation';
  readonly periods:{id:Period;label:string}[]=[{id:'DAY',label:'Ngày'},{id:'WEEK',label:'Tuần'},{id:'MONTH',label:'Tháng'},{id:'YEAR',label:'Năm'}];
  anchorDate=localDate(new Date());preset='MONTH';
  draft:AnalyticsRange=calendarRange(this.anchorDate,'MONTH')!;
  range={...this.draft};error='';
  choosePeriod(period:Period){this.preset=period;this.choosePreset();}
  chooseCustom(){this.preset='CUSTOM';this.draft={...this.draft,period:this.draft.period==='WEEK'?'MONTH':this.draft.period};this.error='';}
  choosePreset():boolean {
    if(this.preset==='CUSTOM')return true;
    const next=calendarRange(this.anchorDate,this.preset as Period);
    if(!next){this.error='Ngày tham chiếu không hợp lệ hoặc kỳ vượt giới hạn năm 1–9999.';return false;}
    this.draft=next;this.error='';return true;
  }
  get dirty(){return JSON.stringify(this.draft)!==JSON.stringify(this.range);}
  apply(){
    if(!this.choosePreset())return;
    this.error=rangeError(this.draft);if(this.error)return;
    if(!this.dirty)return;
    this.range={...this.draft};this.error='';
  }
}
