import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CirculationDashboard } from './circulation-dashboard';
import { HoldingsDashboard } from './holdings-dashboard';
import { FinanceDashboard } from './finance-dashboard';
import { StockDashboard } from './stock-dashboard';
import { AnalyticsRange } from '../../../services/dashboard-analytics.models';
function localDate(date:Date){return new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(date);}
@Component({selector:'app-dashboard',standalone:true,imports:[CommonModule,FormsModule,CirculationDashboard,HoldingsDashboard,FinanceDashboard,StockDashboard],templateUrl:'./dashboard.html',styleUrls:['./analytics.css','./dashboard.css']})
export class Dashboard {
  readonly tabs=[{id:'circulation',label:'Mượn / trả'},{id:'holdings',label:'Danh mục / khoa ngành'},{id:'finance',label:'Tài chính'},{id:'stock',label:'Nhập / xử lý'}];
  tab='circulation';
  draft:AnalyticsRange={from:localDate(new Date()).slice(0,4)+'-01-01',until:localDate(new Date(Date.now()+86400000)),period:'MONTH'};
  range={...this.draft};error='';
  get dirty(){return JSON.stringify(this.draft)!==JSON.stringify(this.range);}
  apply(){
    const from=new Date(this.draft.from+'T00:00:00Z'),until=new Date(this.draft.until+'T00:00:00Z');
    const max=new Date(from);max.setUTCFullYear(max.getUTCFullYear()+50);
    if(!/^\d{4}-\d{2}-\d{2}$/.test(this.draft.from)||!/^\d{4}-\d{2}-\d{2}$/.test(this.draft.until)||!Number.isFinite(+from)||!Number.isFinite(+until)||from>=until||until>max||from.getUTCFullYear()<1||until.getUTCFullYear()>9999){this.error='Chọn khoảng ngày hợp lệ: từ bao gồm, đến loại trừ, tối đa 50 năm.';return;}
    if(from.toISOString().slice(0,10)!==this.draft.from || until.toISOString().slice(0,10)!==this.draft.until){this.error='Ngày không tồn tại trong lịch.';return;}
    this.range={...this.draft};this.error='';
  }
}
