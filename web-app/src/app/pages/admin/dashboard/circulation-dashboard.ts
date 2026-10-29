import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DashboardAnalyticsService } from '../../../services/dashboard-analytics.service';
import * as M from '../../../services/dashboard-analytics.models';
import { AnalyticsPanel, AnalyticsPager, LoadState } from './analytics-shared';

@Component({selector:'app-circulation-dashboard',standalone:true,imports:[CommonModule,FormsModule,RouterLink,AnalyticsPager],templateUrl:'./circulation-dashboard.html',styleUrls:['./analytics.css']})
export class CirculationDashboard extends AnalyticsPanel implements OnChanges {
  @Input({required:true}) range!: M.AnalyticsRange;
  report=new LoadState<M.CirculationReport>();ranks=new LoadState<M.RankingPage>();late=new LoadState<M.OverduePage>();
  ranking: M.Ranking='MOST_BORROWED';rankPage=0;latePage=0;
  readonly modes: {id:M.Ranking;label:string}[]=[{id:'MOST_BORROWED',label:'Mượn nhiều trong kỳ'},{id:'LEAST_BORROWED',label:'Mượn ít trong nhóm có lượt'},{id:'NEVER_RECORDED',label:'Chưa có chứng cứ mượn trong lịch sử'},{id:'NO_RECORDED_IN_PERIOD',label:'Không ghi nhận mượn trong kỳ'},{id:'CURRENTLY_BORROWED',label:'Đang mượn hiện tại'}];
  constructor(private api:DashboardAnalyticsService){super();}
  ngOnChanges(){this.refresh();}
  refresh(){this.loadReport();this.loadRanks(0);this.loadLate(0);}
  loadReport(){this.load(this.report,this.api.circulation(this.range));}
  loadRanks(page=0){this.rankPage=page;this.load(this.ranks,this.api.rankings(this.range,this.ranking,page));}
  loadLate(page=0){this.latePage=page;this.load(this.late,this.api.overdue(page));}
  width(count:number){return 100*count/Math.max(1,...(this.report.data?.buckets.flatMap(b=>[b.counts.checkoutItems,b.counts.physicalReturnItems])||[]));}
}
