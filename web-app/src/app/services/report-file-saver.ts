import { Injectable } from '@angular/core';
import { ObligationMode } from './student-obligation-report.models';

export function obligationFilename(header: string | null, mode: ObligationMode, snapshotAt: string): string {
  const stamp = snapshotAt.replace(/\D/g, '').slice(0, 14);
  return xlsxFilename(header, `thu-thu-${mode.toLowerCase()}-${stamp.slice(0, 8)}-${stamp.slice(8)}.xlsx`);
}

export function xlsxFilename(header: string | null, fallback: string): string {
  const extended = header?.match(/filename\*\s*=\s*UTF-8''([^;]+)/i)?.[1];
  let supplied = header?.match(/filename\s*=\s*(?:"([^"]*)"|([^;]+))/i)?.slice(1).find(Boolean) || '';
  if (extended) { try { supplied = decodeURIComponent(extended.trim()); } catch { /* use plain filename or fallback */ } }
  const safe = supplied.replace(/[\x00-\x1f\x7f<>:"/\\|?*]/g, '_').replace(/^\.+/, '').trim();
  if (safe && safe.length <= 180 && /\.xlsx$/i.test(safe)) return safe;
  return fallback;
}

@Injectable({ providedIn: 'root' })
export class ReportFileSaver {
  save(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = filename;
    try {
      document.body.appendChild(anchor);
      anchor.click();
    } catch (error) { URL.revokeObjectURL(url); throw error; }
    finally { anchor.remove(); }
    // Let the browser consume the download before releasing its URL.
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
