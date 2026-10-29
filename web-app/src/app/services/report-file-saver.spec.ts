import { ReportFileSaver, obligationFilename } from './report-file-saver';

describe('Report Excel download', () => {
  afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); vi.unstubAllGlobals(); });
  it('uses server attachment names including UTF-8 and falls back to report mode and snapshot time', () => {
    expect(obligationFilename('attachment; filename="thu-thu-debt-20261029.xlsx"', 'DEBT', '2026-10-29T10:00:00')).toBe('thu-thu-debt-20261029.xlsx');
    expect(obligationFilename("attachment; filename*=UTF-8''b%C3%A1o-c%C3%A1o.xlsx", 'DEBT', '')).toBe('báo-cáo.xlsx');
    expect(obligationFilename(null, 'OVERDUE', '2026-10-29T10:15:30')).toBe('thu-thu-overdue-20261029-101530.xlsx');
    expect(obligationFilename('filename="error.html"', 'EITHER', '2026-10-29T10:15:30')).toBe('thu-thu-either-20261029-101530.xlsx');
  });
  it('strips path/control characters and tolerates malformed UTF-8 filename headers', () => {
    expect(obligationFilename('filename="../bad\\name.xlsx"', 'DEBT', '')).toBe('_bad_name.xlsx');
    expect(obligationFilename("filename*=UTF-8''%ZZ; filename=good.xlsx", 'DEBT', '')).toBe('good.xlsx');
  });
  it('downloads the exact blob/name through a temporary anchor and releases the object URL', () => {
    vi.useFakeTimers();
    const create = vi.fn(() => 'blob:test-report'), revoke = vi.fn();
    vi.stubGlobal('URL', { createObjectURL: create, revokeObjectURL: revoke });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function(this: HTMLAnchorElement) {
      expect(this.download).toBe('report.xlsx'); expect(this.href).toBe('blob:test-report'); expect(this.isConnected).toBe(true);
    });
    const blob = new Blob(['fixture']); new ReportFileSaver().save(blob, 'report.xlsx');
    expect(create).toHaveBeenCalledWith(blob); expect(click).toHaveBeenCalledOnce();
    expect(document.querySelector('a[download]')).toBeNull();
    vi.advanceTimersByTime(1000); expect(revoke).toHaveBeenCalledWith('blob:test-report');
  });
  it('cleans up if the browser rejects the download', () => {
    const revoke = vi.fn(); vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:failed'), revokeObjectURL: revoke });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => { throw new Error('Blocked'); });
    expect(() => new ReportFileSaver().save(new Blob(['fixture']), 'report.xlsx')).toThrow('Blocked');
    expect(revoke).toHaveBeenCalledWith('blob:failed'); expect(document.querySelector('a[download]')).toBeNull();
  });
});
