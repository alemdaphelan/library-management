import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, timeout } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  LocationNode,
  LocationService,
  ShelfBook,
  ShelfSubject,
} from '../../../services/location.service';
import { DialogService } from '../../../services/dialog.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-shelf-map',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './shelf-map.html',
  styleUrls: ['./shelf-map.css'],
})
export class ShelfMap implements OnInit {
  floors: LocationNode[] = [];
  bookshelves: LocationNode[] = [];
  shelves: LocationNode[] = [];

  selectedFloor?: LocationNode;
  selectedBookshelf?: LocationNode;
  selectedShelf?: LocationNode;
  shelfBooks: ShelfBook[] = [];
  shelfTotal = 0;
  shelfPage = 0;
  unplaced: ShelfBook[] = [];
  unplacedTotal = 0;
  unplacedPage = 0;
  unplacedLoading = false;
  unplacedError = '';
  placingBarcode = '';
  private booksVersion = 0;
  private unplacedVersion = 0;
  private readonly destroyRef = inject(DestroyRef);
  private booksRequest?: Subscription;
  private unplacedRequest?: Subscription;
  shelfBooksError = '';
  isLoadingShelfBooks = false;
  availableSubjects: ShelfSubject[] = [];
  selectedSubjectIds: number[] = [];
  savedSubjectCount?: number;
  isLoadingSubjects = false;
  isSavingSubjects = false;

  newFloorName = '';
  newBookshelfName = '';
  newShelfName = '';

  isLoading = false;
  errorMessage = '';
  successMessage = '';

  constructor(
    private locationService: LocationService,
    private dialog: DialogService,
    private cdr: ChangeDetectorRef,
    private auth: AuthService,
  ) {}

  get isAdmin(): boolean {
    return this.auth.getRole() === 'ADMIN';
  }

  ngOnInit(): void {
    this.loadAvailableSubjects();
    this.loadFloors();
  }

  loadAvailableSubjects(): void {
    this.locationService
      .getAvailableSubjects()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (subjects) => {
          this.availableSubjects = subjects || [];
          this.cdr.detectChanges();
        },
        error: (error) => this.handleError('Không thể tải danh mục chủ đề.', error),
      });
  }

  loadFloors(preferredFloorId?: number): void {
    this.startLoading();
    this.locationService
      .getFloors()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (floors) => {
          this.floors = floors;
          this.selectedFloor = floors.find((floor) => floor.id === preferredFloorId) ?? floors[0];
          if (this.selectedFloor) {
            this.loadBookshelves(this.selectedFloor.id);
          } else {
            this.bookshelves = [];
            this.shelves = [];
            this.selectedBookshelf = undefined;
            this.isLoading = false;
            this.cdr.detectChanges();
          }
        },
        error: (error) => this.handleError('Không thể tải danh sách tầng.', error),
      });
  }

  selectFloor(floor: LocationNode): void {
    if (this.selectedFloor?.id === floor.id) return;
    this.selectedFloor = floor;
    this.bookshelves = [];
    this.selectedBookshelf = undefined;
    this.selectedShelf = undefined;
    this.shelfBooks = [];
    this.selectedSubjectIds = [];
    this.shelves = [];
    this.loadBookshelves(floor.id);
  }

  loadBookshelves(floorId: number, preferredBookshelfId?: number): void {
    this.startLoading();
    this.locationService
      .getBookshelves(floorId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (bookshelves) => {
          if (this.selectedFloor?.id !== floorId) return;
          this.bookshelves = bookshelves;
          this.selectedBookshelf =
            bookshelves.find((item) => item.id === preferredBookshelfId) ?? bookshelves[0];
          if (this.selectedBookshelf) {
            this.loadShelves(this.selectedBookshelf.id);
          } else {
            this.shelves = [];
            this.isLoading = false;
            this.cdr.detectChanges();
          }
        },
        error: (error) => {
          if (this.selectedFloor?.id === floorId)
            this.handleError('Không thể tải danh sách dãy kệ.', error);
        },
      });
  }

  selectBookshelf(bookshelf: LocationNode): void {
    if (this.selectedBookshelf?.id === bookshelf.id) return;
    this.selectedBookshelf = bookshelf;
    this.shelves = [];
    this.selectedShelf = undefined;
    this.shelfBooks = [];
    this.selectedSubjectIds = [];
    this.loadShelves(bookshelf.id);
  }

  loadShelves(bookshelfId: number): void {
    this.startLoading();
    this.locationService
      .getShelves(bookshelfId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (shelves) => {
          if (this.selectedBookshelf?.id !== bookshelfId) return;
          this.shelves = shelves;
          this.selectedShelf = undefined;
          this.shelfBooks = [];
          this.selectedSubjectIds = [];
          this.isLoading = false;
          this.cdr.detectChanges();
        },
        error: (error) => {
          if (this.selectedBookshelf?.id === bookshelfId)
            this.handleError('Không thể tải danh sách ngăn kệ.', error);
        },
      });
  }

  selectShelf(shelf: LocationNode): void {
    this.selectedShelf = shelf;
    this.selectedSubjectIds = [];
    this.savedSubjectCount = undefined;
    this.loadShelfBooks(0);
    this.loadUnplaced(0);
    this.isLoadingSubjects = true;
    this.locationService
      .getShelfSubjects(shelf.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (subjects) => {
          if (this.selectedShelf?.id !== shelf.id) return;
          this.selectedSubjectIds = (subjects || []).map((subject) => subject.subjectId);
          this.savedSubjectCount = this.selectedSubjectIds.length;
          this.isLoadingSubjects = false;
          this.cdr.detectChanges();
        },
        error: (error) => {
          if (this.selectedShelf?.id !== shelf.id) return;
          this.isLoadingSubjects = false;
          this.selectedSubjectIds = [];
          this.handleError('Không thể tải quy tắc chủ đề của ngăn kệ.', error);
          this.cdr.detectChanges();
        },
      });
  }

  loadShelfBooks(page = 0): void {
    const shelf = this.selectedShelf;
    if (!shelf || !Number.isInteger(page) || page < 0) return;
    const version = ++this.booksVersion;
    this.booksRequest?.unsubscribe();
    this.isLoadingShelfBooks = true;
    this.shelfBooksError = '';
    this.shelfBooks = [];
    this.shelfTotal = 0;
    this.shelfPage = page;
    this.booksRequest = this.locationService
      .getShelfBooksPage(shelf.id, page)
      .pipe(timeout(15000), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (version !== this.booksVersion || this.selectedShelf?.id !== shelf.id) return;
          if (result.total > 0 && page * 50 >= result.total) {
            this.loadShelfBooks(Math.floor((result.total - 1) / 50));
            return;
          }
          this.shelfBooks = result.items;
          this.shelfTotal = result.total;
          this.isLoadingShelfBooks = false;
          this.cdr.markForCheck();
          if (result.total === 0) this.shelfPage = 0;
        },
        error: () => {
          if (version !== this.booksVersion || this.selectedShelf?.id !== shelf.id) return;
          this.isLoadingShelfBooks = false;
          this.shelfBooksError =
            'Không thể tải tài liệu trong ngăn. Bấm Thử lại để tải đúng ngăn đang chọn.';
          this.cdr.markForCheck();
        },
      });
  }
  loadUnplaced(page = 0): void {
    if (!this.selectedShelf || !Number.isInteger(page) || page < 0) return;
    const version = ++this.unplacedVersion;
    this.unplacedRequest?.unsubscribe();
    this.unplacedLoading = true;
    this.unplacedError = '';
    this.unplaced = [];
    this.unplacedTotal = 0;
    this.unplacedPage = page;
    this.unplacedRequest = this.locationService
      .getUnplacedCopies(page, this.selectedShelf?.id)
      .pipe(timeout(15000), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => {
          if (version !== this.unplacedVersion) return;
          if (r.total > 0 && page * 50 >= r.total) {
            this.loadUnplaced(Math.floor((r.total - 1) / 50));
            return;
          }
          this.unplaced = r.items;
          this.unplacedTotal = r.total;
          this.unplacedLoading = false;
          this.cdr.markForCheck();
          if (r.total === 0) this.unplacedPage = 0;
        },
        error: () => {
          if (version !== this.unplacedVersion) return;
          this.unplacedLoading = false;
          this.unplacedError = 'Không tải được bản sao chưa xếp ngăn.';
          this.cdr.markForCheck();
        },
      });
  }
  placeUnlocated(book: ShelfBook): void {
    const shelf = this.selectedShelf;
    if (!shelf || this.placingBarcode || !this.savedSubjectCount) return;
    this.placingBarcode = book.barcode;
    this.locationService.placeCopy(book.barcode, shelf.id).subscribe({
      next: () => {
        this.placingBarcode = '';
        this.showSuccess(`Đã xếp ${book.barcode} vào ${shelf.name}.`);
        this.loadUnplaced(0);
        if (this.selectedShelf?.id === shelf.id) this.loadShelfBooks(0);
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.placingBarcode = '';
        this.handleError(
          error?.error?.detail ||
            error?.error?.message ||
            'Không thể xếp bản sao vào ngăn đã chọn.',
          error,
        );
        this.cdr.markForCheck();
      },
    });
  }

  isSubjectSelected(subjectId: number): boolean {
    return this.selectedSubjectIds.includes(subjectId);
  }

  toggleSubject(subjectId: number, checked: boolean): void {
    if (!this.isAdmin) return;
    this.selectedSubjectIds = checked
      ? Array.from(new Set([...this.selectedSubjectIds, subjectId]))
      : this.selectedSubjectIds.filter((id) => id !== subjectId);
  }

  saveSubjectRules(): void {
    if (
      !this.selectedShelf ||
      !this.isAdmin ||
      this.isSavingSubjects ||
      !this.selectedSubjectIds.length
    )
      return;
    this.isSavingSubjects = true;
    const shelf = this.selectedShelf;
    this.errorMessage = '';
    this.locationService
      .updateShelfSubjects(this.selectedShelf.id, this.selectedSubjectIds)
      .subscribe({
        next: (subjects) => {
          this.isSavingSubjects = false;
          if (this.selectedShelf?.id !== shelf.id) return;
          this.selectedSubjectIds = (subjects || []).map((subject) => subject.subjectId);
          this.savedSubjectCount = this.selectedSubjectIds.length;
          this.isSavingSubjects = false;
          this.loadUnplaced(0);
          this.showSuccess(`Đã lưu ${this.selectedSubjectIds.length} chủ đề cho ${shelf.name}.`);
          this.cdr.detectChanges();
        },
        error: (error) => {
          this.isSavingSubjects = false;
          this.handleError('Không thể lưu quy tắc chủ đề.', error);
          this.cdr.detectChanges();
        },
      });
  }

  copyStatusLabel(status: string): string {
    return (
      (
        {
          AVAILABLE: 'Có sẵn',
          RESERVED: 'Đang giữ chỗ',
          BORROWING: 'Đang mượn',
          OVERDUE: 'Quá hạn',
          LOST: 'Bị mất',
          DAMAGED: 'Hư hỏng',
          MAINTENANCE: 'Đang xử lý',
        } as Record<string, string>
      )[status] || status
    );
  }

  createFloor(): void {
    const name = this.newFloorName.trim();
    if (!name) return;
    this.locationService.createFloor(name).subscribe({
      next: (floor) => {
        this.newFloorName = '';
        this.showSuccess('Đã thêm tầng mới.');
        this.loadFloors(floor.id);
      },
      error: (error) => this.handleError('Không thể thêm tầng.', error),
    });
  }

  createBookshelf(): void {
    const name = this.newBookshelfName.trim();
    if (!name || !this.selectedFloor) return;
    this.locationService.createBookshelf(name, this.selectedFloor.id).subscribe({
      next: (bookshelf) => {
        this.newBookshelfName = '';
        this.showSuccess('Đã thêm dãy kệ mới.');
        this.loadBookshelves(this.selectedFloor!.id, bookshelf.id);
      },
      error: (error) => this.handleError('Không thể thêm dãy kệ.', error),
    });
  }

  createShelf(): void {
    const name = this.newShelfName.trim();
    if (!name || !this.selectedBookshelf) return;
    this.locationService.createShelf(name, this.selectedBookshelf.id).subscribe({
      next: () => {
        this.newShelfName = '';
        this.showSuccess('Đã thêm ngăn kệ mới.');
        this.loadShelves(this.selectedBookshelf!.id);
      },
      error: (error) => this.handleError('Không thể thêm ngăn kệ.', error),
    });
  }

  async renameFloor(floor: LocationNode): Promise<void> {
    const name = (
      await this.dialog.prompt({
        title: 'Đổi tên tầng',
        message: `Nhập tên mới cho “${floor.name}”.`,
        inputLabel: 'Tên tầng mới',
        inputValue: floor.name,
        confirmText: 'Lưu thay đổi',
      })
    )?.trim();
    if (!name || name === floor.name) return;
    this.locationService.updateFloor(floor.id, name).subscribe({
      next: () => {
        this.showSuccess('Đã cập nhật tên tầng.');
        this.loadFloors(floor.id);
      },
      error: (error) => this.handleError('Không thể cập nhật tầng.', error),
    });
  }

  async renameBookshelf(bookshelf: LocationNode): Promise<void> {
    if (!this.selectedFloor) return;
    const name = (
      await this.dialog.prompt({
        title: 'Đổi tên dãy kệ',
        message: `Nhập tên mới cho “${bookshelf.name}”.`,
        inputLabel: 'Tên dãy kệ mới',
        inputValue: bookshelf.name,
        confirmText: 'Lưu thay đổi',
      })
    )?.trim();
    if (!name || name === bookshelf.name) return;
    this.locationService.updateBookshelf(bookshelf.id, name, this.selectedFloor.id).subscribe({
      next: () => {
        this.showSuccess('Đã cập nhật tên dãy kệ.');
        this.loadBookshelves(this.selectedFloor!.id, bookshelf.id);
      },
      error: (error) => this.handleError('Không thể cập nhật dãy kệ.', error),
    });
  }

  async renameShelf(shelf: LocationNode): Promise<void> {
    if (!this.selectedBookshelf) return;
    const name = (
      await this.dialog.prompt({
        title: 'Đổi tên ngăn kệ',
        message: `Nhập tên mới cho “${shelf.name}”.`,
        inputLabel: 'Tên ngăn kệ mới',
        inputValue: shelf.name,
        confirmText: 'Lưu thay đổi',
      })
    )?.trim();
    if (!name || name === shelf.name) return;
    this.locationService.updateShelf(shelf.id, name, this.selectedBookshelf.id).subscribe({
      next: () => {
        this.showSuccess('Đã cập nhật tên ngăn kệ.');
        this.loadShelves(this.selectedBookshelf!.id);
      },
      error: (error) => this.handleError('Không thể cập nhật ngăn kệ.', error),
    });
  }

  async editCapacity(shelf: LocationNode): Promise<void> {
    if (!shelf.parentId) return;
    const value = await this.dialog.prompt({
      title: 'Sức chứa ngăn kệ',
      message: `Nhập số bản sao tối đa cho ${shelf.name}. Nhập 0 để bỏ giới hạn.`,
      inputLabel: 'Số bản sao tối đa',
      inputValue: String(shelf.capacity ?? 0),
      confirmText: 'Lưu sức chứa',
    });
    if (value === null) return;
    const capacity = Number(value.trim());
    if (!value.trim() || !Number.isSafeInteger(capacity) || capacity < 0 || capacity > 2147483647) {
      this.dialog.show({ type: 'error', message: 'Nhập một số nguyên từ 0 đến 2.147.483.647.' });
      return;
    }
    this.locationService.updateShelf(shelf.id, shelf.name, shelf.parentId, capacity).subscribe({
      next: (updated) => {
        this.shelves = this.shelves.map((item) => (item.id === updated.id ? updated : item));
        if (this.selectedShelf?.id === updated.id) this.selectedShelf = updated;
        this.showSuccess('Đã cập nhật sức chứa ngăn.');
        this.cdr.markForCheck();
      },
      error: (error) =>
        this.dialog.show({
          type: 'error',
          message:
            error?.status === 409
              ? 'Sức chứa không được thấp hơn số bản sao hiện có trong ngăn.'
              : 'Không lưu được sức chứa ngăn.',
        }),
    });
  }

  async deleteFloor(floor: LocationNode): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: 'Xóa tầng',
      message: `Xóa tầng “${floor.name}”? Tầng chỉ có thể xóa khi không còn dãy kệ.`,
      type: 'warning',
      confirmText: 'Xóa tầng',
    });
    if (!confirmed) return;
    this.locationService.deleteFloor(floor.id).subscribe({
      next: () => {
        this.showSuccess('Đã xóa tầng.');
        this.loadFloors();
      },
      error: (error) => this.handleError('Không thể xóa tầng đang có dữ liệu liên kết.', error),
    });
  }

  async deleteBookshelf(bookshelf: LocationNode): Promise<void> {
    if (!this.selectedFloor) return;
    const confirmed = await this.dialog.confirm({
      title: 'Xóa dãy kệ',
      message: `Xóa dãy kệ “${bookshelf.name}”? Dãy kệ chỉ có thể xóa khi không còn ngăn.`,
      type: 'warning',
      confirmText: 'Xóa dãy kệ',
    });
    if (!confirmed) return;
    this.locationService.deleteBookshelf(bookshelf.id).subscribe({
      next: () => {
        this.showSuccess('Đã xóa dãy kệ.');
        this.loadBookshelves(this.selectedFloor!.id);
      },
      error: (error) => this.handleError('Không thể xóa dãy kệ đang có dữ liệu liên kết.', error),
    });
  }

  async deleteShelf(shelf: LocationNode): Promise<void> {
    if (!this.selectedBookshelf) return;
    const confirmed = await this.dialog.confirm({
      title: 'Xóa ngăn kệ',
      message: `Xóa ngăn kệ “${shelf.name}”?`,
      type: 'warning',
      confirmText: 'Xóa ngăn kệ',
    });
    if (!confirmed) return;
    this.locationService.deleteShelf(shelf.id).subscribe({
      next: () => {
        this.showSuccess('Đã xóa ngăn kệ.');
        this.loadShelves(this.selectedBookshelf!.id);
      },
      error: (error) =>
        this.handleError('Không thể xóa ngăn kệ đang được bản sao sách sử dụng.', error),
    });
  }

  private startLoading(): void {
    this.isLoading = true;
    this.errorMessage = '';
  }

  private showSuccess(message: string): void {
    this.successMessage = message;
    this.errorMessage = '';
  }

  private handleError(message: string, error: unknown): void {
    console.error(message, error);
    this.errorMessage = message;
    this.successMessage = '';
    this.isLoading = false;
  }
}
