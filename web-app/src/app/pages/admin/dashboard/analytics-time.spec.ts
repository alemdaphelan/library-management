import { axisLabel, bucketUnit, calendarRange, intervalText, rangeError } from './analytics-time';
import { AnalyticsChart } from './analytics-chart';
import { Period } from '../../../services/dashboard-analytics.models';

describe('Dashboard time contracts F07/F08',()=>{
  it.each([
    ['DAY','2026-01-01','2026-01-01','2026-01-02','HOUR'],
    ['WEEK','2026-01-04','2025-12-29','2026-01-05','DAY'],
    ['WEEK','2026-01-05','2026-01-05','2026-01-12','DAY'],
    ['MONTH','2026-02-17','2026-02-01','2026-03-01','DAY'],
    ['MONTH','2024-02-29','2024-02-01','2024-03-01','DAY'],
    ['MONTH','2026-04-30','2026-04-01','2026-05-01','DAY'],
    ['MONTH','2026-01-31','2026-01-01','2026-02-01','DAY'],
    ['YEAR','2026-11-04','2026-01-01','2027-01-01','MONTH'],
    ['QUARTER','2026-05-01','2026-04-01','2026-07-01','QUARTER'],
    ['DAY','0001-01-01','0001-01-01','0001-01-02','HOUR']
  ])('selects %s containing %s', (p,date,from,until,unit)=>{
    expect(calendarRange(date,p as Period)).toEqual({from,until,period:p});expect(bucketUnit(p as Period)).toBe(unit);
  });
  it('rejects impossible dates, empty dates and calendar periods past the supported year',()=>{
    for(const date of ['','2026-02-29','2026-02-31','0000-01-01','10000-01-01'])expect(calendarRange(date,'MONTH')).toBeUndefined();
    expect(calendarRange('9999-12-31','YEAR')).toBeUndefined();
  });
  it('limits actual chart intervals and preserves clipped month/quarter counts',()=>{
    expect(rangeError({from:'2026-01-01',until:'2026-02-11',period:'DAY'})).toBe('');
    expect(rangeError({from:'2026-01-01',until:'2026-02-12',period:'DAY'})).toContain('1.000');
    expect(rangeError({from:'2000-01-01',until:'2026-01-01',period:'MONTH'})).toContain('1.000');
    expect(rangeError({from:'2000-01-15',until:'2026-01-01',period:'YEAR'})).toBe('');
    expect(rangeError({from:'2026-01-01',until:'2026-01-01',period:'MONTH'})).toBeTruthy();
    expect(rangeError({from:'2026-02-31',until:'2026-04-01',period:'MONTH'})).toBeTruthy();
    expect(rangeError({from:'1900-01-01',until:'2026-01-01',period:'YEAR'})).toContain('50');
  });
  it('labels hours, days and months using local timestamp fields, including the 24h endpoint',()=>{
    expect(axisLabel('2026-01-01T00:00:00','HOUR')).toBe('00:00');
    expect(axisLabel('2026-01-01T23:00:00','HOUR')).toBe('23:00');
    expect(axisLabel('2024-02-29T00:00:00','DAY')).toBe('29/02');
    expect(axisLabel('2026-12-01T00:00:00','MONTH')).toBe('12/2026');
    expect(intervalText('2026-01-01T23:00:00','2026-01-02T00:00:00','HOUR')).toBe('01/01/2026 · 23:00–24:00');
    expect(intervalText('2026-01-15T00:00:00','2026-02-01T00:00:00','MONTH')).toContain('15/01/2026 → trước 01/02/2026');
  });
  it('keeps negative adjustments below zero and empty responses empty',()=>{
    const chart=new AnalyticsChart();chart.series=['Thu','Điều chỉnh'];chart.data=[{label:'2026-01-01T00:00:00',values:[100,-5]}];chart.ngOnChanges();
    expect(chart.min).toBe(-5);expect(chart.y(-5)).toBeGreaterThan(chart.y(0));expect(chart.height(-5)).toBeGreaterThan(0);
    chart.data=[];chart.ngOnChanges();expect(chart.data).toEqual([]);expect(Number.isFinite(chart.y(0))).toBe(true);
  });
});
