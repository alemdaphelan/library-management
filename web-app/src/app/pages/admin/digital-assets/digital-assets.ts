import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-digital-assets',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './digital-assets.html',
  styleUrls: ['./digital-assets.css']
})
export class DigitalAssets {
  isDragging = false;
  uploadProgress = 0;
  isUploading = false;

  // Mock data for assets
  assets = [
    { id: 'DA-001', name: 'Cau-truc-du-lieu.pdf', size: '15.4 MB', type: 'PDF', date: '16/09/2026', bookRef: 'Cấu trúc Dữ liệu & Giải thuật' },
    { id: 'DA-002', name: 'Nhap-mon-lap-trinh.pdf', size: '8.2 MB', type: 'PDF', date: '15/09/2026', bookRef: 'Nhập môn lập trình' },
    { id: 'DA-003', name: 'Kinh-te-vi-mo-slide.pptx', size: '5.1 MB', type: 'PPTX', date: '12/09/2026', bookRef: 'Kinh tế Vi mô' },
    { id: 'DA-004', name: 'Triet-hoc-Mac-Lenin-Full.epub', size: '2.3 MB', type: 'EPUB', date: '10/09/2026', bookRef: 'Triết học Mác - Lênin' },
    { id: 'DA-005', name: 'He-dieu-hanh-chuong-1.pdf', size: '4.8 MB', type: 'PDF', date: '05/09/2026', bookRef: 'Hệ điều hành' },
    { id: 'DA-006', name: 'Tieng-Anh-Giao-Tiep-Audio.mp3', size: '45.0 MB', type: 'AUDIO', date: '01/09/2026', bookRef: 'Tiếng Anh Giao Tiếp' }
  ];

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
      this.simulateUpload(files[0]);
    }
  }

  onFileSelected(event: any) {
    const files = event.target.files;
    if (files && files.length > 0) {
      this.simulateUpload(files[0]);
    }
  }

  simulateUpload(file: File) {
    this.isUploading = true;
    this.uploadProgress = 0;
    
    // Giả lập tiến trình upload
    const interval = setInterval(() => {
      this.uploadProgress += 15;
      if (this.uploadProgress >= 100) {
        clearInterval(interval);
        this.uploadProgress = 100;
        
        setTimeout(() => {
          this.isUploading = false;
          // Thêm file vào danh sách giả lập
          this.assets.unshift({
            id: 'DA-' + Math.floor(Math.random() * 1000),
            name: file.name,
            size: (file.size / (1024 * 1024)).toFixed(1) + ' MB',
            type: file.name.split('.').pop()?.toUpperCase() || 'UNKNOWN',
            date: 'Hôm nay',
            bookRef: 'Chưa liên kết'
          });
        }, 500);
      }
    }, 200);
  }

  deleteAsset(id: string) {
    this.assets = this.assets.filter(a => a.id !== id);
  }
}
