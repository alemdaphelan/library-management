import { Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { DigitalAssetsService } from '../../../services/digital-assets.service';

@Component({
  selector: 'app-digital-assets',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './digital-assets.html',
  styleUrls: ['./digital-assets.css']
})
export class DigitalAssets implements OnInit {
  isDragging = false;
  uploadProgress = 0;
  isUploading = false;
  assets: any[] = [];

  constructor(private digitalAssetsService: DigitalAssetsService) {}

  ngOnInit() {
    this.fetchAssets();
  }

  fetchAssets() {
    this.digitalAssetsService.getAssets().subscribe({
      next: (data) => this.assets = data,
      error: (err) => {
        console.error('Error fetching assets', err);
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
    this.isUploading = true;
    this.uploadProgress = 0;
    
    this.digitalAssetsService.uploadAsset(file).subscribe({
      next: (event: any) => {
        if (event.type === HttpEventType.UploadProgress) {
          this.uploadProgress = Math.round(100 * event.loaded / event.total);
        } else if (event.type === HttpEventType.Response) {
          this.isUploading = false;
          this.fetchAssets();
        }
      },
      error: (err) => {
        console.error('Error uploading file', err);
        this.isUploading = false;
      }
    });
  }

  deleteAsset(id: string) {
    this.digitalAssetsService.deleteAsset(id).subscribe({
      next: () => this.fetchAssets(),
      error: (err) => console.error('Error deleting asset', err)
    });
  }
}
