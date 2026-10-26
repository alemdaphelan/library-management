import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LocationNode, LocationService, ShelfBook } from '../../../services/location.service';

@Component({
  selector: 'app-shelf-map',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './shelf-map.html',
  styleUrls: ['./shelf-map.css']
})
export class ShelfMap implements OnInit {
  floors: LocationNode[] = [];
  bookshelves: LocationNode[] = [];
  shelves: LocationNode[] = [];

  selectedFloor?: LocationNode;
  selectedBookshelf?: LocationNode;
  selectedShelf?: LocationNode;
  shelfBooks: ShelfBook[] = [];
  isLoadingShelfBooks = false;

  newFloorName = '';
  newBookshelfName = '';
  newShelfName = '';

  isLoading = false;
  errorMessage = '';
  successMessage = '';

  constructor(private locationService: LocationService) {}

  ngOnInit(): void {
    this.loadFloors();
  }

  loadFloors(preferredFloorId?: number): void {
    this.startLoading();
    this.locationService.getFloors().subscribe({
      next: floors => {
        this.floors = floors;
        this.selectedFloor = floors.find(floor => floor.id === preferredFloorId) ?? floors[0];
        if (this.selectedFloor) {
          this.loadBookshelves(this.selectedFloor.id);
        } else {
          this.bookshelves = [];
          this.shelves = [];
          this.selectedBookshelf = undefined;
          this.isLoading = false;
        }
      },
      error: error => this.handleError('Không thể tải danh sách tầng.', error)
    });
  }

  selectFloor(floor: LocationNode): void {
    if (this.selectedFloor?.id === floor.id) return;
    this.selectedFloor = floor;
    this.selectedBookshelf = undefined;
    this.selectedShelf = undefined;
    this.shelfBooks = [];
    this.shelves = [];
    this.loadBookshelves(floor.id);
  }

  loadBookshelves(floorId: number, preferredBookshelfId?: number): void {
    this.startLoading();
    this.locationService.getBookshelves(floorId).subscribe({
      next: bookshelves => {
        this.bookshelves = bookshelves;
        this.selectedBookshelf = bookshelves.find(item => item.id === preferredBookshelfId) ?? bookshelves[0];
        if (this.selectedBookshelf) {
          this.loadShelves(this.selectedBookshelf.id);
        } else {
          this.shelves = [];
          this.isLoading = false;
        }
      },
      error: error => this.handleError('Không thể tải danh sách dãy kệ.', error)
    });
  }

  selectBookshelf(bookshelf: LocationNode): void {
    if (this.selectedBookshelf?.id === bookshelf.id) return;
    this.selectedBookshelf = bookshelf;
    this.loadShelves(bookshelf.id);
  }

  loadShelves(bookshelfId: number): void {
    this.startLoading();
    this.locationService.getShelves(bookshelfId).subscribe({
      next: shelves => {
        this.shelves = shelves;
        this.selectedShelf = undefined;
        this.shelfBooks = [];
        this.isLoading = false;
      },
      error: error => this.handleError('Không thể tải danh sách ngăn kệ.', error)
    });
  }

  selectShelf(shelf: LocationNode): void {
    this.selectedShelf = shelf;
    this.isLoadingShelfBooks = true;
    this.locationService.getShelfBooks(shelf.id).subscribe({
      next: books => {
        this.shelfBooks = books || [];
        this.isLoadingShelfBooks = false;
      },
      error: error => {
        this.isLoadingShelfBooks = false;
        this.shelfBooks = [];
        this.handleError('Không thể tải danh sách tài liệu trong ngăn kệ.', error);
      }
    });
  }

  copyStatusLabel(status: string): string {
    return ({ AVAILABLE: 'Có sẵn', RESERVED: 'Đang giữ chỗ', BORROWING: 'Đang mượn',
      OVERDUE: 'Quá hạn', LOST: 'Bị mất', DAMAGED: 'Hư hỏng', MAINTENANCE: 'Đang xử lý' } as Record<string, string>)[status] || status;
  }

  createFloor(): void {
    const name = this.newFloorName.trim();
    if (!name) return;
    this.locationService.createFloor(name).subscribe({
      next: floor => {
        this.newFloorName = '';
        this.showSuccess('Đã thêm tầng mới.');
        this.loadFloors(floor.id);
      },
      error: error => this.handleError('Không thể thêm tầng.', error)
    });
  }

  createBookshelf(): void {
    const name = this.newBookshelfName.trim();
    if (!name || !this.selectedFloor) return;
    this.locationService.createBookshelf(name, this.selectedFloor.id).subscribe({
      next: bookshelf => {
        this.newBookshelfName = '';
        this.showSuccess('Đã thêm dãy kệ mới.');
        this.loadBookshelves(this.selectedFloor!.id, bookshelf.id);
      },
      error: error => this.handleError('Không thể thêm dãy kệ.', error)
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
      error: error => this.handleError('Không thể thêm ngăn kệ.', error)
    });
  }

  renameFloor(floor: LocationNode): void {
    const name = window.prompt('Tên tầng mới:', floor.name)?.trim();
    if (!name || name === floor.name) return;
    this.locationService.updateFloor(floor.id, name).subscribe({
      next: () => {
        this.showSuccess('Đã cập nhật tên tầng.');
        this.loadFloors(floor.id);
      },
      error: error => this.handleError('Không thể cập nhật tầng.', error)
    });
  }

  renameBookshelf(bookshelf: LocationNode): void {
    if (!this.selectedFloor) return;
    const name = window.prompt('Tên dãy kệ mới:', bookshelf.name)?.trim();
    if (!name || name === bookshelf.name) return;
    this.locationService.updateBookshelf(bookshelf.id, name, this.selectedFloor.id).subscribe({
      next: () => {
        this.showSuccess('Đã cập nhật tên dãy kệ.');
        this.loadBookshelves(this.selectedFloor!.id, bookshelf.id);
      },
      error: error => this.handleError('Không thể cập nhật dãy kệ.', error)
    });
  }

  renameShelf(shelf: LocationNode): void {
    if (!this.selectedBookshelf) return;
    const name = window.prompt('Tên ngăn kệ mới:', shelf.name)?.trim();
    if (!name || name === shelf.name) return;
    this.locationService.updateShelf(shelf.id, name, this.selectedBookshelf.id).subscribe({
      next: () => {
        this.showSuccess('Đã cập nhật tên ngăn kệ.');
        this.loadShelves(this.selectedBookshelf!.id);
      },
      error: error => this.handleError('Không thể cập nhật ngăn kệ.', error)
    });
  }

  deleteFloor(floor: LocationNode): void {
    if (!window.confirm(`Xóa tầng “${floor.name}”? Tầng chỉ có thể xóa khi không còn dãy kệ.`)) return;
    this.locationService.deleteFloor(floor.id).subscribe({
      next: () => {
        this.showSuccess('Đã xóa tầng.');
        this.loadFloors();
      },
      error: error => this.handleError('Không thể xóa tầng đang có dữ liệu liên kết.', error)
    });
  }

  deleteBookshelf(bookshelf: LocationNode): void {
    if (!this.selectedFloor || !window.confirm(`Xóa dãy kệ “${bookshelf.name}”? Dãy kệ chỉ có thể xóa khi không còn ngăn.`)) return;
    this.locationService.deleteBookshelf(bookshelf.id).subscribe({
      next: () => {
        this.showSuccess('Đã xóa dãy kệ.');
        this.loadBookshelves(this.selectedFloor!.id);
      },
      error: error => this.handleError('Không thể xóa dãy kệ đang có dữ liệu liên kết.', error)
    });
  }

  deleteShelf(shelf: LocationNode): void {
    if (!this.selectedBookshelf || !window.confirm(`Xóa ngăn kệ “${shelf.name}”?`)) return;
    this.locationService.deleteShelf(shelf.id).subscribe({
      next: () => {
        this.showSuccess('Đã xóa ngăn kệ.');
        this.loadShelves(this.selectedBookshelf!.id);
      },
      error: error => this.handleError('Không thể xóa ngăn kệ đang được bản sao sách sử dụng.', error)
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
