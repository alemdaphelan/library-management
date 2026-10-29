import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DashboardAnalyticsService } from '../../../services/dashboard-analytics.service';
import { ImportsService, ImportReceipt } from '../../../services/imports.service';
import * as M from '../../../services/dashboard-analytics.models';
import { AnalyticsPanel, AnalyticsPager, LoadState } from './analytics-shared';
@Component({selector:'app-stock-dashboard',standalone:true,imports:[CommonModule,RouterLink,AnalyticsPager],templateUrl:'./stock-dashboard.html',styleUrls:['./analytics.css']})
export class StockDashboard extends AnalyticsPanel implements OnChanges {
  @Input({required:true}) range!:M.AnalyticsRange;
  report=new LoadState<M.StockReport>();receipts=new LoadState<M.ItemPage<M.AnalyticsReceipt>>();detail=new LoadState<ImportReceipt>();page=0;selectedId?:number;
  constructor(private api:DashboardAnalyticsService,private imports:ImportsService){super();}
  ngOnChanges(){this.loadReport();this.loadReceipts(0);this.selectedId=undefined;}
  loadReport(){this.load(this.report,this.api.stock(this.range));}
  loadReceipts(page=0){this.page=page;this.load(this.receipts,this.api.receipts(this.range,page));}
  open(id:number){this.selectedId=id;this.load(this.detail,this.imports.getReceipt(id));}
}
