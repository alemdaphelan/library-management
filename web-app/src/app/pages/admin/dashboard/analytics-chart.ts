import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BucketUnit, Period } from '../../../services/dashboard-analytics.models';
import { axisLabel, intervalText, unitLabel } from './analytics-time';
export interface ChartPoint {label:string;until?:string;values:number[];}
@Component({selector:'app-analytics-chart',standalone:true,imports:[CommonModule],template:`
<figure><figcaption>{{title}}</figcaption><div class="legend"><span *ngFor="let s of series;let i=index"><i [style.background]="colors[i%colors.length]"></i>{{s}}</span></div>
<p class="axis-note" *ngIf="dateLabels">Theo {{unitName.toLowerCase()}} · Giờ Việt Nam</p>
<p *ngIf="!data.length">Chưa có dữ liệu biểu đồ.</p><div class="chart-scroll" *ngIf="data.length" tabindex="0" role="region" aria-label="Biểu đồ có thể cuộn ngang; số liệu chi tiết ở bảng bên dưới"><svg [attr.viewBox]="'0 0 '+width+' 350'" [style.width.px]="width" height="350" role="img" [attr.aria-label]="title+'. Xem bảng bên dưới để đối chiếu số chính xác.'">
<text x="5" y="18">{{max | number:'1.0-2'}}</text><text x="5" y="278">{{min | number:'1.0-2'}}</text><line x1="60" [attr.y1]="y(0)" [attr.x2]="width-10" [attr.y2]="y(0)" stroke="#94a3b8"/>
<g *ngFor="let d of data;let n=index"><g *ngFor="let value of d.values;let i=index"><rect [attr.x]="72+n*columnWidth+i*barWidth" [attr.y]="value>=0?y(value):y(0)" [attr.width]="barWidth-2" [attr.height]="height(value)" [attr.fill]="colors[i%colors.length]"><title>{{tooltip(d)}} · {{series[i]}}: {{value | number:'1.0-2'}}</title></rect></g><text class="x-label" [attr.x]="72+n*columnWidth" y="306">{{tick(d)}}</text></g>
<text *ngIf="dateLabels && bucketUnit==='HOUR' && data.length" class="end-label" [attr.x]="width-50" y="306">{{endTick}}</text>
<text x="60" y="338">{{dateLabels ? unitName : 'Khoa'}}</text></svg></div></figure>`,styles:[`:host{display:block;min-width:0}figure{margin:18px 0}figcaption{font-weight:600;margin-bottom:10px}.legend{display:flex;gap:14px;flex-wrap:wrap;font-size:.85rem}.legend i{display:inline-block;width:12px;height:12px;margin-right:6px;border-radius:2px}.axis-note{font-size:.8rem;color:#64748b}.chart-scroll{overflow-x:auto;border-bottom:1px solid #cbd5e1;margin-top:10px;max-width:100%}.chart-scroll:focus-visible{outline:3px solid #38bdf8}svg{display:block;min-width:100%;font-size:10px}text{fill:#475569}`]})
export class AnalyticsChart implements OnChanges {
  @Input() title='Biểu đồ';@Input() series:string[]=[];@Input() data:ChartPoint[]=[];@Input() dateLabels=true;
  @Input() bucketUnit:BucketUnit='DAY'; @Input() period?:Period;
  readonly colors=['#0284c7','#0d9488','#d97706','#be123c'];width=600;max=1;min=0;barWidth=20;
  get columnWidth(){return this.period==='WEEK'&&this.bucketUnit==='DAY'?112:70;}
  get unitName(){return unitLabel(this.bucketUnit);}
  tick(point:ChartPoint){return this.dateLabels?axisLabel(point.label,this.bucketUnit,this.period):point.label.slice(0,16);}
  tooltip(point:ChartPoint){return this.dateLabels&&point.until?intervalText(point.label,point.until,this.bucketUnit,this.period):point.label;}
  get endTick(){const last=this.data.at(-1);return last?.until?.slice(11,16)==='00:00'?'24:00':last?.until?.slice(11,16)||'';}
  ngOnChanges(){this.width=Math.max(600,150+this.data.length*this.columnWidth);const values=this.data.flatMap(d=>d.values);this.max=Math.max(1,...values);this.min=Math.min(0,...values);this.barWidth=Math.min(22,60/Math.max(1,this.series.length));}
  y(value:number){return 30+(this.max-value)/(this.max-this.min)*240;}
  height(value:number){return Math.abs(this.y(value)-this.y(0));}
}
