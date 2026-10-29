import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { DashboardAnalyticsService } from '../../../services/dashboard-analytics.service';
import { DigitalAssetsService } from '../../../services/digital-assets.service';
import { ReportFileSaver } from '../../../services/report-file-saver';
import * as M from '../../../services/dashboard-analytics.models';
import { AnalyticsPanel, AnalyticsPager, LoadState } from './analytics-shared';

@Component({selector:'app-holdings-dashboard',standalone:true,imports:[CommonModule,FormsModule,RouterLink,AnalyticsPager],templateUrl:'./holdings-dashboard.html',styleUrls:['./analytics.css']})
export class HoldingsDashboard extends AnalyticsPanel implements OnInit {
  catalog=new LoadState<M.AcademicCatalog>();report=new LoadState<M.HoldingsReport>();list=new LoadState<M.MaterialPage>();
  copies=new LoadState<M.ItemPage<M.MaterialCopy>>();assets=new LoadState<M.ItemPage<M.MaterialAsset>>();classification=new LoadState<M.Classification>();download=new LoadState<HttpResponse<Blob>>();
  filter: M.HoldingFilter={facultyId:'',majorId:'',type:'ALL',state:'ALL',q:''};applied={...this.filter};
  page=0;copyPage=0;assetPage=0;selected?: M.Material;
  readonly types=[{id:'ALL',label:'Mọi loại'},{id:'PHYSICAL',label:'Chỉ vật lý'},{id:'DIGITAL',label:'Chỉ số'},{id:'HYBRID',label:'Vật lý và số'}];
  readonly states=[['ALL','Mọi trạng thái'],['AVAILABLE','Có bản sẵn sàng'],['BORROWING','Có bản đang mượn'],['RESERVED','Có bản đang giữ'],['LOST','Có bản mất'],['DAMAGED','Có bản hỏng'],['MAINTENANCE','Có bản bảo trì'],['RETIRED','Có bản ngừng khai thác'],['NO_COPIES','Chưa có bản sao'],['NO_DIGITAL_FILE','Chưa có tệp số']];
  constructor(private api:DashboardAnalyticsService,private digital:DigitalAssetsService,private saver:ReportFileSaver){super();}
  ngOnInit(){this.loadCatalog();this.apply();}
  loadCatalog(){this.load(this.catalog,this.api.catalog());}
  get majors(){return this.catalog.data?.majors.filter(m=>!this.filter.facultyId||m.facultyId===this.filter.facultyId)||[];}
  get dirty(){return JSON.stringify(this.filter)!==JSON.stringify(this.applied);}
  facultyChanged(){this.filter.majorId='';}
  apply(){if(this.filter.q.trim().length>200)return;this.applied={...this.filter,q:this.filter.q.trim()};this.filter={...this.applied};this.selected=undefined;this.loadSummary();this.loadList(0);}
  loadSummary(){this.load(this.report,this.api.holdings(this.applied));}
  loadList(page=0){this.page=page;this.load(this.list,this.api.materials(this.applied,page));}
  open(m:M.Material){this.selected=m;this.loadCopies(0);this.loadAssets(0);this.load(this.classification,this.api.classification(m));}
  loadCopies(page:number){if(!this.selected)return;this.copyPage=page;this.load(this.copies,this.api.copies(this.selected,page));}
  loadAssets(page:number){if(!this.selected)return;this.assetPage=page;this.load(this.assets,this.api.assets(this.selected,page));}
  downloadAsset(a:M.MaterialAsset){this.load(this.download,this.digital.downloadAsset(a.id),r=>{if(r.body)this.saver.save(r.body,a.name.replace(/[\x00-\x1f<>:"/\\|?*]/g,'_'));});}
}
