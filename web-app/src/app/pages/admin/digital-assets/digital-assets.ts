import { Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DigitalAsset, DigitalAssetsService } from '../../../services/digital-assets.service';

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
  uploadAccessLevel: 'AUTHENTICATED' | 'STAFF_ONLY' = 'AUTHENTICATED';
  editingAsset: DigitalAsset | null = null;
  editForm = { displayName: '', bookId: null as number | null, accessLevel: 'AUTHENTICATED' as 'AUTHENTICATED' | 'STAFF_ONLY' };
  errorMessage = '';
  successMessage = '';

  constructor(private digitalAssetsService: DigitalAssetsService) {}

  ngOnInit() {
    this.fetchAssets();
  }

  fetchAssets() {
    this.digitalAssetsService.getAssets().subscribe({
      next: (data) => this.assets = data,
      error: (err) => {
        this.errorMessage = this.errorText(err, 'Không thể tải danh sách tài liệu số.');
        this.assets = [];
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
    this.isUploading = true;
    this.uploadProgress = 0;
    
    this.digitalAssetsService.uploadAsset(file, this.uploadBookId, this.uploadAccessLevel).subscribe({
      next: (event: any) => {
        if (event.type === HttpEventType.UploadProgress) {
          this.uploadProgress = Math.round(100 * event.loaded / event.total);
        } else if (event.type === HttpEventType.Response) {
          this.isUploading = false;
          this.successMessage = `Đã tải lên ${event.body?.name || file.name}.`;
          this.fetchAssets();
        }
      },
      error: (err) => {
        this.errorMessage = this.errorText(err, 'Tải tệp thất bại.');
        this.isUploading = false;
      }
    });
  }

  deleteAsset(id: number) {
    if (!window.confirm('Xóa tài liệu số này? Thao tác sẽ xóa cả tệp đã lưu.')) return;
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
      accessLevel: asset.accessLevel
    };
  }

  saveEdit() {
    if (!this.editingAsset || !this.editForm.displayName.trim()) return;
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
