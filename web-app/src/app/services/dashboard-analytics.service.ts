import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
import * as M from './dashboard-analytics.models';

@Injectable({providedIn:'root'})
export class DashboardAnalyticsService {
  private readonly base = `${environment.apiUrl}/dashboard/analytics`;
  constructor(private http: HttpClient) {}
  private get<T>(path: string, values: object = {}) {
    let params = new HttpParams();
    for (const [key,value] of Object.entries(values)) if(value !== '' && value != null) params=params.set(key,String(value));
    return this.http.get<T>(this.base+path,{params});
  }
  circulation(r: M.AnalyticsRange){return this.get<M.CirculationReport>('/circulation',r);}
  rankings(r: M.AnalyticsRange, ranking: M.Ranking,page=0){return this.get<M.RankingPage>('/rankings',{...r,ranking,page,size:25});}
  overdue(page=0){return this.get<M.OverduePage>('/overdue',{page,size:25});}
  catalog(){return this.get<M.AcademicCatalog>('/academic-catalog');}
  holdings(f: M.HoldingFilter){return this.get<M.HoldingsReport>('/holdings',f);}
  materials(f: M.HoldingFilter,page=0){return this.get<M.MaterialPage>('/materials',{...f,page,size:25});}
  copies(m: M.Material,page=0){return this.get<M.ItemPage<M.MaterialCopy>>(`/materials/${m.kind}/${m.id}/copies`,{page,size:25});}
  assets(m: M.Material,page=0){return this.get<M.ItemPage<M.MaterialAsset>>(`/materials/${m.kind}/${m.id}/digital-assets`,{page,size:25});}
  classification(m: M.Material){return this.get<M.Classification>(`/materials/${m.kind}/${m.id}/classification`);}
  finance(r: M.AnalyticsRange){return this.get<M.FinanceReport>('/finance',r);}
  entries(r: M.AnalyticsRange,kind: M.EventKind|'',page=0){return this.get<M.ItemPage<M.FinanceEvent>>('/finance/entries',{...r,kind,page,size:25});}
  allocations(id: number,page=0){return this.get<M.ItemPage<M.Allocation>>(`/finance/payments/${id}/allocations`,{page,size:25});}
  stock(r: M.AnalyticsRange){return this.get<M.StockReport>('/stock-movements',r);}
  receipts(r: M.AnalyticsRange,page=0){return this.get<M.ItemPage<M.AnalyticsReceipt>>('/stock-movements/receipts',{...r,page,size:25});}
}
