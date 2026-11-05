import { Pipe, PipeTransform } from '@angular/core';
import { AnalyticsRange, BucketUnit, Period } from '../../../services/dashboard-analytics.models';

// API timestamps are Vietnam local time. Read their calendar fields without browser timezone conversion.
export const bucketUnit = (period: Period): BucketUnit => period === 'DAY' ? 'HOUR' : period === 'YEAR' ? 'MONTH' : period === 'QUARTER' ? 'QUARTER' : 'DAY';
export const unitLabel = (unit: BucketUnit): string => ({HOUR:'Giờ',DAY:'Ngày',MONTH:'Tháng',QUARTER:'Quý'})[unit];
export function dateText(value: string): string {
  const date=value.slice(0,10).split('-');return `${date[2]}/${date[1]}/${date[0]}`;
}
export function axisLabel(value: string, unit: BucketUnit): string {
  if(unit==='HOUR')return value.slice(11,16);
  if(unit==='DAY')return dateText(value).slice(0,5);
  if(unit==='MONTH')return `${value.slice(5,7)}/${value.slice(0,4)}`;
  return `Q${Math.floor((Number(value.slice(5,7))-1)/3)+1}/${value.slice(0,4)}`;
}
export function intervalText(start: string, until: string, unit: BucketUnit): string {
  if(unit==='HOUR') {
    const end=until.slice(11,16)==='00:00'&&until.slice(0,10)!==start.slice(0,10)?'24:00':until.slice(11,16);
    return `${dateText(start)} · ${start.slice(11,16)}–${end}`;
  }
  return `${dateText(start)} → trước ${dateText(until)}`;
}
@Pipe({name:'analyticsInterval',standalone:true})
export class AnalyticsIntervalPipe implements PipeTransform {
  transform(start: string, until: string, unit: BucketUnit): string {return intervalText(start,until,unit);}
}
function parsedDate(value: string): Date | undefined {
  if(!/^\d{4}-\d{2}-\d{2}$/.test(value)||value<'0001-01-01'||value>'9999-12-31')return;
  const date=new Date(`${value}T00:00:00Z`);
  return Number.isFinite(+date)&&date.toISOString().slice(0,10)===value?date:undefined;
}
export function calendarRange(value: string, period: Period): AnalyticsRange | undefined {
  const start=parsedDate(value);if(!start)return;
  if(period==='WEEK')start.setUTCDate(start.getUTCDate()-((start.getUTCDay()+6)%7));
  if(period==='MONTH'||period==='QUARTER')start.setUTCDate(1);
  if(period==='QUARTER')start.setUTCMonth(Math.floor(start.getUTCMonth()/3)*3);
  if(period==='YEAR')start.setUTCMonth(0,1);
  const end=new Date(start);
  if(period==='DAY')end.setUTCDate(end.getUTCDate()+1);
  if(period==='WEEK')end.setUTCDate(end.getUTCDate()+7);
  if(period==='MONTH')end.setUTCMonth(end.getUTCMonth()+1);
  if(period==='QUARTER')end.setUTCMonth(end.getUTCMonth()+3);
  if(period==='YEAR')end.setUTCFullYear(end.getUTCFullYear()+1);
  const range={from:start.toISOString().slice(0,10),until:end.toISOString().slice(0,10),period};
  return rangeError(range)?undefined:range;
}
export function rangeError(range: AnalyticsRange): string {
  const from=parsedDate(range.from),until=parsedDate(range.until);
  if(!from||!until||from>=until)return 'Chọn ngày hợp lệ; ngày kết thúc phải sau ngày bắt đầu.';
  const max=new Date(from);max.setUTCFullYear(max.getUTCFullYear()+50);
  if(until>max)return 'Khoảng thống kê tối đa 50 năm.';
  const unit=bucketUnit(range.period);
  const days=(+until-+from)/86400000;
  const months=(until.getUTCFullYear()-from.getUTCFullYear())*12+until.getUTCMonth()-from.getUTCMonth()+(until.getUTCDate()>1?1:0);
  const quarters=(until.getUTCFullYear()-from.getUTCFullYear())*4+Math.floor(until.getUTCMonth()/3)-Math.floor(from.getUTCMonth()/3)+(until.getUTCMonth()%3!==0||until.getUTCDate()>1?1:0);
  const count=unit==='HOUR'?days*24:unit==='DAY'?days:unit==='MONTH'?months:quarters;
  return count>1000?'Khoảng chọn có hơn 1.000 cột. Thu hẹp ngày hoặc chọn độ chi tiết lớn hơn.':'';
}
