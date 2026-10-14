import { Injectable, signal } from '@angular/core';

export interface DialogOptions {
  title?: string;
  message: string;
  type?: 'success' | 'error' | 'warning' | 'info';
  confirmText?: string;
  onConfirm?: () => void;
}

@Injectable({
  providedIn: 'root'
})
export class DialogService {
  isOpen = signal<boolean>(false);
  title = signal<string>('Thông báo');
  message = signal<string>('');
  type = signal<'success' | 'error' | 'warning' | 'info'>('info');
  confirmText = signal<string>('Đồng ý');
  private onConfirmCallback?: () => void;

  constructor() {
    // Intercept native browser alert() to use our beautiful centered modal
    if (typeof window !== 'undefined') {
      window.alert = (message?: any) => {
        const msgString = typeof message === 'string' ? message : JSON.stringify(message);
        
        // Determine type based on keywords
        let detectedType: 'success' | 'error' | 'warning' | 'info' = 'info';
        const lower = msgString.toLowerCase();
        if (lower.includes('thành công') || lower.includes('success')) {
          detectedType = 'success';
        } else if (lower.includes('lỗi') || lower.includes('thất bại') || lower.includes('không tìm thấy') || lower.includes('sai') || lower.includes('từ chối')) {
          detectedType = 'error';
        } else if (lower.includes('cảnh báo') || lower.includes('quá hạn') || lower.includes('hạn chế') || lower.includes('không có quyền')) {
          detectedType = 'warning';
        }

        this.show({
          title: 'Thông báo Thư viện UniLib',
          message: msgString,
          type: detectedType,
          confirmText: 'Đã hiểu'
        });
      };
    }
  }

  show(options: DialogOptions) {
    this.title.set(options.title || 'Thông báo');
    this.message.set(options.message);
    this.type.set(options.type || 'info');
    this.confirmText.set(options.confirmText || 'Đồng ý');
    this.onConfirmCallback = options.onConfirm;
    this.isOpen.set(true);
  }

  close() {
    this.isOpen.set(false);
    if (this.onConfirmCallback) {
      this.onConfirmCallback();
      this.onConfirmCallback = undefined;
    }
  }
}
