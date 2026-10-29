import { Injectable, signal } from '@angular/core';

export type DialogType = 'success' | 'error' | 'warning' | 'info';
export type DialogMode = 'alert' | 'confirm' | 'prompt';

export interface DialogOptions {
  title?: string;
  message: string;
  type?: DialogType;
  confirmText?: string;
  cancelText?: string;
  inputLabel?: string;
  inputValue?: string;
  onConfirm?: () => void;
}

@Injectable({ providedIn: 'root' })
export class DialogService {
  readonly isOpen = signal(false);
  readonly title = signal('Thông báo');
  readonly message = signal('');
  readonly type = signal<DialogType>('info');
  readonly mode = signal<DialogMode>('alert');
  readonly confirmText = signal('Đồng ý');
  readonly cancelText = signal('Hủy');
  readonly inputLabel = signal('Nội dung');
  readonly promptValue = signal('');

  private onConfirmCallback?: () => void;
  private resolveConfirm?: (accepted: boolean) => void;
  private resolvePrompt?: (value: string | null) => void;

  constructor() {
    // Safety net for legacy code. New code should call show()/confirm()/prompt().
    if (typeof window !== 'undefined') {
      window.alert = (message?: unknown) => {
        const text = this.stringify(message);
        this.show({
          title: 'Thông báo Thư viện UniLib',
          message: text,
          type: this.detectType(text),
          confirmText: 'Đã hiểu',
        });
      };
    }
  }

  show(options: DialogOptions): void {
    this.prepare(options, 'alert');
    this.onConfirmCallback = options.onConfirm;
  }

  confirm(options: DialogOptions): Promise<boolean> {
    this.prepare(options, 'confirm');
    return new Promise(resolve => {
      this.resolveConfirm = resolve;
    });
  }

  prompt(options: DialogOptions): Promise<string | null> {
    this.prepare(options, 'prompt');
    this.inputLabel.set(options.inputLabel || 'Nội dung');
    this.promptValue.set(options.inputValue || '');
    return new Promise(resolve => {
      this.resolvePrompt = resolve;
    });
  }

  accept(): void {
    const callback = this.onConfirmCallback;
    const confirmResolver = this.resolveConfirm;
    const promptResolver = this.resolvePrompt;
    const promptResult = this.promptValue();
    this.resetAndClose();
    callback?.();
    confirmResolver?.(true);
    promptResolver?.(promptResult);
  }

  cancel(): void {
    const confirmResolver = this.resolveConfirm;
    const promptResolver = this.resolvePrompt;
    this.resetAndClose();
    confirmResolver?.(false);
    promptResolver?.(null);
  }

  dismiss(): void {
    if (this.mode() === 'alert') {
      this.accept();
    } else {
      this.cancel();
    }
  }

  close(): void {
    this.dismiss();
  }

  private prepare(options: DialogOptions, mode: DialogMode): void {
    if (this.isOpen()) this.cancel();
    this.mode.set(mode);
    this.title.set(options.title || (mode === 'confirm' ? 'Xác nhận thao tác' : 'Thông báo'));
    this.message.set(options.message);
    this.type.set(options.type || 'info');
    this.confirmText.set(options.confirmText || (mode === 'confirm' ? 'Xác nhận' : 'Đồng ý'));
    this.cancelText.set(options.cancelText || 'Hủy');
    this.isOpen.set(true);
  }

  private resetAndClose(): void {
    this.isOpen.set(false);
    this.onConfirmCallback = undefined;
    this.resolveConfirm = undefined;
    this.resolvePrompt = undefined;
  }

  private stringify(message: unknown): string {
    if (typeof message === 'string') return message;
    try {
      return JSON.stringify(message);
    } catch {
      return String(message);
    }
  }

  private detectType(message: string): DialogType {
    const lower = message.toLowerCase();
    if (lower.includes('thành công') || lower.includes('success')) return 'success';
    if (lower.includes('lỗi') || lower.includes('thất bại') || lower.includes('không thể') || lower.includes('từ chối')) return 'error';
    if (lower.includes('cảnh báo') || lower.includes('quá hạn') || lower.includes('hạn chế')) return 'warning';
    return 'info';
  }
}
