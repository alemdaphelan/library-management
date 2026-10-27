import { ChangeDetectorRef, Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DigitalAsset, DigitalAssetsService } from '../../../services/digital-assets.service';
import { BookService } from '../../../services/book.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'app-digital-assets',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './digital-assets.html',
  styleUrls: ['./digital-assets.css']
})
export class DigitalAssets implements OnInit {
  isDragging = false;
  uploadProgress = 0;
  isUploading = false;
  assets: DigitalAsset[] = [];
  searchTerm = '';
  typeFilter = '';
  uploadBookId: number | null = null;
  uploadBookIsbn = '';
  linkedBookLabel = '';
  isResolvingBook = false;
  uploadAccessLevel: 'AUTHENTICATED' | 'STAFF_ONLY' = 'AUTHENTICATED';
  editingAsset: DigitalAsset | null = null;
  editForm = {
    displayName: '',
    bookId: null as number | null,
    bookIsbn: '',
    bookLabel: '',
    accessLevel: 'AUTHENTICATED' as 'AUTHENTICATED' | 'STAFF_ONLY'
  };
  isResolvingEditBook = false;
  errorMessage = '';
  successMessage = '';

  constructor(
    private digitalAssetsService: DigitalAssetsService,
    private bookService: BookService,
    private dialog: DialogService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.fetchAssets();
  }

  fetchAssets() {
    this.digitalAssetsService.getAssets().subscribe({
      next: (data) => { this.assets = data; this.cdr.detectChanges(); },
      error: (err) => {
        this.errorMessage = this.errorText(err, 'Không thể tải danh sách tài liệu số.');
        this.assets = [];
        this.cdr.detectChanges();
      }
    });
  }

  @HostListener('dragover', ['$event']) onDragOver(evt: DragEvent) {
    evt.preventDefault();
    evt.stopPropagation();
    this.isDragging = true;
  }

  @HostListener('dragleave', ['$event']) onDragLeave(evt: DragEvent) {
    evt.preventDefault();
    evt.stopPropagation();
    this.isDragging = false;
  }

  @HostListener('drop', ['$event']) onDrop(evt: DragEvent) {
    evt.preventDefault();
    evt.stopPropagation();
    this.isDragging = false;
    
    const files = evt.dataTransfer?.files;
    if (files && files.length > 0) {
      this.uploadFile(files[0]);
    }
  }

  onFileSelected(event: any) {
    const files = event.target.files;
    if (files && files.length > 0) {
      this.uploadFile(files[0]);
    }
  }

  uploadFile(file: File) {
    this.clearMessages();
    const isbn = this.uploadBookIsbn.trim();
    if (isbn && !this.uploadBookId) {
      this.isResolvingBook = true;
      this.bookService.getBookByIsbn(isbn).subscribe({
        next: book => {
          this.isResolvingBook = false;
          this.uploadBookId = Number(book.bookId);
          this.linkedBookLabel = `${book.title || 'Sách'} · ISBN ${book.isbn || isbn}`;
          this.performUpload(file);
        },
        error: err => {
          this.isResolvingBook = false;
          this.errorMessage = this.errorText(
            err, 'Không tìm thấy sách theo ISBN. Hãy kiểm tra ISBN hoặc thêm sách vào danh mục trước.');
        }
      });
      return;
    }
    this.performUpload(file);
  }

  resolveBook(): void {
    const isbn = this.uploadBookIsbn.trim();
    this.uploadBookId = null;
    this.linkedBookLabel = '';
    if (!isbn) return;
    this.isResolvingBook = true;
    this.bookService.getBookByIsbn(isbn).subscribe({
      next: book => {
        this.isResolvingBook = false;
        this.uploadBookId = Number(book.bookId);
        this.linkedBookLabel = `${book.title || 'Sách'} · ISBN ${book.isbn || isbn}`;
      },
      error: err => {
        this.isResolvingBook = false;
        this.errorMessage = this.errorText(
          err, 'Không tìm thấy sách theo ISBN. Hãy kiểm tra ISBN hoặc thêm sách vào danh mục trước.');
      }
    });
  }

  onUploadIsbnChange(): void {
    this.uploadBookId = null;
    this.linkedBookLabel = '';
  }

  private performUpload(file: File) {
    this.isUploading = true;
    this.uploadProgress = 0;
    
    this.digitalAssetsService.uploadAsset(file, this.uploadBookId, this.uploadAccessLevel).subscribe({
      next: (event: any) => {
        if (event.type === HttpEventType.UploadProgress) {
          this.uploadProgress = Math.round(100 * event.loaded / event.total);
        } else if (event.type === HttpEventType.Response) {
          this.isUploading = false;
          this.successMessage = `Đã tải lên ${event.body?.name || file.name}.`;
          this.uploadBookIsbn = '';
          this.uploadBookId = null;
          this.linkedBookLabel = '';
          this.fetchAssets();
        }
      },
      error: (err) => {
        this.errorMessage = this.errorText(err, 'Tải tệp thất bại.');
        this.isUploading = false;
      }
    });
  }

  async deleteAsset(id: number) {
    const confirmed = await this.dialog.confirm({
      title: 'Xóa tài liệu số',
      message: 'Xóa tài liệu số này? Thao tác sẽ xóa cả tệp đã lưu.',
      type: 'warning',
      confirmText: 'Xóa tài liệu',
    });
    if (!confirmed) return;
    this.clearMessages();
    this.digitalAssetsService.deleteAsset(id).subscribe({
      next: () => {
        this.successMessage = 'Đã xóa tài liệu số.';
        this.fetchAssets();
      },
      error: (err) => this.errorMessage = this.errorText(err, 'Không thể xóa tài liệu số.')
    });
  }

  get filteredAssets(): DigitalAsset[] {
    const keyword = this.searchTerm.trim().toLocaleLowerCase('vi');
    return this.assets.filter(asset => {
      const matchesType = !this.typeFilter || asset.type === this.typeFilter;
      const haystack = `${asset.name} ${asset.bookTitle || ''} ${asset.bookId || ''}`.toLocaleLowerCase('vi');
      return matchesType && (!keyword || haystack.includes(keyword));
    });
  }

  openEdit(asset: DigitalAsset) {
    this.editingAsset = asset;
    this.editForm = {
      displayName: asset.name,
      bookId: asset.bookId || null,
      bookIsbn: '',
      bookLabel: asset.bookId ? (asset.bookTitle || 'Sách đang liên kết') : '',
      accessLevel: asset.accessLevel
    };
  }

  onEditIsbnChange(): void {
    this.editForm.bookId = null;
    this.editForm.bookLabel = '';
  }

  resolveEditBook(): void {
    const isbn = this.editForm.bookIsbn.trim();
    if (!isbn) return;
    this.isResolvingEditBook = true;
    this.bookService.getBookByIsbn(isbn).subscribe({
      next: book => {
        this.isResolvingEditBook = false;
        this.editForm.bookId = Number(book.bookId);
        this.editForm.bookLabel = `${book.title || 'Sách'} · ISBN ${book.isbn || isbn}`;
      },
      error: err => {
        this.isResolvingEditBook = false;
        this.errorMessage = this.errorText(
          err, 'Không tìm thấy sách theo ISBN. Liên kết hiện tại chưa được thay đổi.');
      }
    });
  }

  saveEdit() {
    if (!this.editingAsset || !this.editForm.displayName.trim()) return;
    if (this.editForm.bookIsbn.trim() && !this.editForm.bookId) {
      this.errorMessage = 'Hãy tra cứu ISBN hợp lệ trước khi lưu liên kết.';
      return;
    }
    this.clearMessages();
    this.digitalAssetsService.updateAsset(this.editingAsset.id, {
      displayName: this.editForm.displayName.trim(),
      bookId: this.editForm.bookId || null,
      accessLevel: this.editForm.accessLevel
    }).subscribe({
      next: () => {
        this.successMessage = 'Đã cập nhật thông tin tài liệu.';
        this.editingAsset = null;
        this.fetchAssets();
      },
      error: err => this.errorMessage = this.errorText(err, 'Không thể cập nhật tài liệu.')
    });
  }

  download(asset: DigitalAsset) {
    this.clearMessages();
    this.digitalAssetsService.downloadAsset(asset.id).subscribe({
      next: response => {
        if (!response.body) return;
        const url = URL.createObjectURL(response.body);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = asset.name;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: err => this.errorMessage = this.errorText(err, 'Không thể tải tài liệu.')
    });
  }

  formatSize(bytes: number): string {
    if (!bytes) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    return `${(bytes / Math.pow(1024, index)).toFixed(index ? 1 : 0)} ${units[index]}`;
  }

  private clearMessages() {
    this.errorMessage = '';
    this.successMessage = '';
  }

  private errorText(error: any, fallback: string): string {
    return error?.error?.detail || error?.error?.message || (typeof error?.error === 'string' ? error.error : fallback);
  }
}
