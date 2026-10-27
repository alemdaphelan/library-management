import { TestBed } from '@angular/core/testing';
import { DialogService } from './dialog.service';

describe('DialogService', () => {
  let service: DialogService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DialogService);
  });

  it('resolves a confirmation after the user accepts', async () => {
    const result = service.confirm({ message: 'Xác nhận thao tác?' });

    expect(service.isOpen()).toBe(true);
    expect(service.mode()).toBe('confirm');

    service.accept();

    await expect(result).resolves.toBe(true);
    expect(service.isOpen()).toBe(false);
  });

  it('resolves a confirmation as false after the user cancels', async () => {
    const result = service.confirm({ message: 'Xác nhận thao tác?' });

    service.cancel();

    await expect(result).resolves.toBe(false);
  });

  it('returns the edited prompt value after the user accepts', async () => {
    const result = service.prompt({
      message: 'Nhập tên mới',
      inputValue: 'Tầng 1',
    });
    service.promptValue.set('Tầng 2');

    service.accept();

    await expect(result).resolves.toBe('Tầng 2');
  });

  it('returns null when a prompt is dismissed', async () => {
    const result = service.prompt({ message: 'Nhập tên mới' });

    service.dismiss();

    await expect(result).resolves.toBe(null);
  });
});
