import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DigitalAsset, DigitalAssetsService } from '../../../services/digital-assets.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-digital',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './digital.html',
  styleUrls: ['./digital.css']
})
export class Digital implements OnInit {
  assets: DigitalAsset[] = [];
  loading = false;
  errorMessage = '';

  constructor(private assetService: DigitalAssetsService, public authService: AuthService) {}

  ngOnInit() {
    if (this.authService.isLoggedIn()) this.loadAssets();
  }

  loadAssets() {
    this.loading = true;
    this.errorMessage = '';
    this.assetService.getAssets().subscribe({
      next: assets => {
        this.assets = assets;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Không thể tải kho tài liệu số lúc này.';
        this.loading = false;
      }
    });
  }

  download(asset: DigitalAsset) {
    this.assetService.downloadAsset(asset.id).subscribe({
      next: response => {
        if (!response.body) return;
        const url = URL.createObjectURL(response.body);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = asset.name;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.errorMessage = 'Không thể tải tài liệu. Vui lòng thử lại.'
    });
  }

  formatSize(bytes: number): string {
    if (!bytes) return '0 B';
    const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), 3);
    return `${(bytes / Math.pow(1024, index)).toFixed(index ? 1 : 0)} ${['B', 'KB', 'MB', 'GB'][index]}`;
  }
}
