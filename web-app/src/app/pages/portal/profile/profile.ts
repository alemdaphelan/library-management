import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrls: ['./profile.css']
})
export class Profile {
  activeTab: 'info' | 'password' | 'settings' = 'info';

  userInfo = {
    fullName: 'Đang tải...',
    studentId: 'Đang tải...',
    email: 'Đang tải...',
    phone: 'Đang tải...',
    faculty: 'Đang tải...',
    avatarUrl: ''
  };

  passwordData = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  };

  settings = {
    emailAlerts: true,
    smsAlerts: false
  };

  isSaving = false;
  isUploadingAvatar = false;
  successMsg = '';
  errMsg = '';

  constructor(private route: ActivatedRoute, private authService: AuthService) {
    this.route.queryParams.subscribe(params => {
      if (params['tab']) {
        this.activeTab = params['tab'];
      }
    });

    this.loadProfile();
  }

  loadProfile() {
    this.authService.fetchMyProfile().subscribe({
      next: (user: any) => {
        this.userInfo = {
          fullName: user.fullName || '',
          studentId: user.mssv || user.studentId || 'Chưa cập nhật',
          email: user.email || 'Chưa cập nhật',
          phone: user.phone || '',
          faculty: user.department || user.faculty || 'Khoa Công nghệ thông tin',
          avatarUrl: user.avatarUrl || ''
        };
      },
      error: (err: any) => {
        console.error('Failed to fetch profile', err);
      }
    });
  }

  onFileSelected(event: any) {
    const file: File = event.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      this.errMsg = 'Vui lòng chọn tệp hình ảnh hợp lệ (PNG, JPG, WEBP).';
      setTimeout(() => this.errMsg = '', 4000);
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      this.errMsg = 'Kích thước ảnh tối đa là 5MB!';
      setTimeout(() => this.errMsg = '', 4000);
      return;
    }

    this.isUploadingAvatar = true;
    this.errMsg = '';
    this.successMsg = '';

    const reader = new FileReader();
    reader.onload = (e: any) => {
      const dataUrl = e.target.result;
      this.userInfo.avatarUrl = dataUrl;

      this.authService.uploadAvatar(file).subscribe({
        next: (res: any) => {
          this.isUploadingAvatar = false;
          if (res && res.avatarUrl) {
            this.userInfo.avatarUrl = res.avatarUrl;
          }
          this.successMsg = 'Cập nhật ảnh đại diện thành công!';
          setTimeout(() => this.successMsg = '', 4000);
        },
        error: (err: any) => {
          // Fallback to updateProfile with dataUrl
          this.authService.updateProfile({ avatarUrl: dataUrl }).subscribe({
            next: () => {
              this.isUploadingAvatar = false;
              this.successMsg = 'Cập nhật ảnh đại diện thành công!';
              setTimeout(() => this.successMsg = '', 4000);
            },
            error: (fallbackErr: any) => {
              this.isUploadingAvatar = false;
              this.errMsg = 'Tải ảnh đại diện thất bại!';
              setTimeout(() => this.errMsg = '', 4000);
            }
          });
        }
      });
    };
    reader.readAsDataURL(file);
  }

  saveChanges() {
    if (this.activeTab === 'password') {
      const np = this.passwordData.newPassword || '';
      const cp = this.passwordData.confirmPassword || '';
      
      if (np !== cp) {
        this.errMsg = `Mật khẩu mới và xác nhận mật khẩu không khớp!`;
        setTimeout(() => this.errMsg = '', 6000);
        return;
      }
      this.isSaving = true;
      this.successMsg = '';
      this.errMsg = '';
      this.authService.changePassword(this.passwordData.currentPassword, this.passwordData.newPassword).subscribe({
        next: (res: any) => {
          this.isSaving = false;
          this.successMsg = 'Đổi mật khẩu thành công! Bạn sẽ được đăng xuất.';
          this.passwordData = { currentPassword: '', newPassword: '', confirmPassword: '' };
          setTimeout(() => {
            this.authService.logout();
          }, 2000);
        },
        error: (err: any) => {
          this.isSaving = false;
          this.errMsg = err.error || 'Đổi mật khẩu thất bại. Vui lòng kiểm tra lại mật khẩu cũ!';
          setTimeout(() => this.errMsg = '', 4000);
        }
      });
    } else if (this.activeTab === 'info') {
      if (!this.userInfo.fullName.trim()) {
        this.errMsg = 'Họ và tên không được để trống!';
        setTimeout(() => this.errMsg = '', 4000);
        return;
      }

      this.isSaving = true;
      this.successMsg = '';
      this.errMsg = '';

      this.authService.updateProfile({
        fullName: this.userInfo.fullName,
        phone: this.userInfo.phone,
        avatarUrl: this.userInfo.avatarUrl
      }).subscribe({
        next: (updatedUser: any) => {
          this.isSaving = false;
          this.successMsg = 'Cập nhật thông tin cá nhân thành công!';
          if (updatedUser) {
            this.userInfo.fullName = updatedUser.fullName || this.userInfo.fullName;
            this.userInfo.phone = updatedUser.phone || this.userInfo.phone;
          }
          setTimeout(() => this.successMsg = '', 4000);
        },
        error: (err: any) => {
          this.isSaving = false;
          this.errMsg = err.error || 'Cập nhật thông tin thất bại. Vui lòng thử lại!';
          setTimeout(() => this.errMsg = '', 4000);
        }
      });
    } else {
      this.isSaving = true;
      this.successMsg = '';
      setTimeout(() => {
        this.isSaving = false;
        this.successMsg = 'Lưu cài đặt thông báo thành công!';
        setTimeout(() => this.successMsg = '', 3000);
      }, 500);
    }
  }
}
