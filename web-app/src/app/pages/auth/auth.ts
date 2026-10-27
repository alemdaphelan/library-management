import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auth.html',
  styleUrls: ['./auth.css']
})
export class Auth implements OnInit, OnDestroy {
  mode: 'login' | 'forgot' = 'login';

  loginData = { username: '', password: '' };
  forgotStep: 'email' | 'otp' | 'reset' = 'email';
  forgotData = { email: '', otp: '', newPassword: '', confirmPassword: '' };

  isSubmitting = false;
  successMessage = '';
  errorMessage = '';
  showPassword = false;
  resendCountdown = 0;
  private resendTimer?: ReturnType<typeof setInterval>;

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  constructor(private router: Router, private authService: AuthService, private cdr: ChangeDetectorRef) { }

  ngOnInit() {
    if (window.location.pathname.includes('forgot-password')) {
      this.mode = 'forgot';
      const savedStep = sessionStorage.getItem('forgotStep');
      const savedEmail = sessionStorage.getItem('forgotEmail');
      if (savedStep && savedEmail) {
        this.forgotStep = savedStep as 'email' | 'otp' | 'reset';
        this.forgotData.email = savedEmail;
        if (this.forgotStep === 'otp') {
          this.startResendCountdown();
        }
      }
    }
  }

  ngOnDestroy(): void {
    this.clearResendTimer();
  }

  toggleMode(newMode: 'login' | 'forgot', event: Event) {
    event.preventDefault();
    this.mode = newMode;
    this.successMessage = '';
    this.errorMessage = '';
    this.forgotStep = 'email';
    this.forgotData = { email: '', otp: '', newPassword: '', confirmPassword: '' };
    sessionStorage.removeItem('forgotStep');
    sessionStorage.removeItem('forgotEmail');
    this.clearResendTimer();

    const newUrl = newMode === 'login' ? '/login' : '/forgot-password';
    window.history.pushState({}, '', newUrl);
  }

  private extractErrorMessage(err: any, fallback: string): string {
    if (!err) return fallback;
    if (err.status === 0) {
      return 'Không thể kết nối đến hệ thống thư viện. Vui lòng thử lại hoặc liên hệ người quản trị.';
    }
    if (typeof err.error === 'string' && err.error.trim()) {
      return err.error;
    }
    if (err.error && typeof err.error === 'object') {
      if (err.error.message && typeof err.error.message === 'string') {
        return err.error.message;
      }
      if (err.error.error && typeof err.error.error === 'string') {
        return err.error.error;
      }
    }
    if (err.message && typeof err.message === 'string' && !err.message.includes('Http failure response')) {
      return err.message;
    }
    return fallback;
  }

  onLogin() {
    this.isSubmitting = true;
    this.errorMessage = '';

    this.authService.login(this.loginData.username, this.loginData.password).subscribe({
      next: (response) => {
        this.isSubmitting = false;
        if (response.role === 'ADMIN' || response.role === 'LIBRARIAN' || response.role === 'ACCOUNTANT' || response.role === 'TREASURER') {
          this.router.navigate(['/admin']);
        } else {
          this.router.navigate(['/']); 
        }
      },
      error: (err) => {
        console.error('Login error:', err);
        this.isSubmitting = false;
        if (err.status === 0) {
          this.errorMessage = 'Không thể kết nối đến hệ thống thư viện. Vui lòng thử lại hoặc liên hệ người quản trị.';
        } else {
          this.errorMessage = this.extractErrorMessage(err, 'Sai tên đăng nhập hoặc mật khẩu. Vui lòng thử lại!');
        }
        this.cdr.detectChanges();
      }
    });
  }

  onForgotPassword() {
    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.cdr.detectChanges();

    this.authService.forgotPassword(this.forgotData.email).subscribe({
      next: (res: any) => {
        try {
          this.isSubmitting = false;
          this.successMessage = 'Mã OTP đã được gửi đến email của bạn.';
          this.forgotStep = 'otp';
          sessionStorage.setItem('forgotStep', 'otp');
          sessionStorage.setItem('forgotEmail', this.forgotData.email);
          this.startResendCountdown();
          this.cdr.detectChanges();
          
          setTimeout(() => {
            this.successMessage = '';
            this.cdr.detectChanges();
          }, 3000);
        } catch(e) {
          console.error(e);
        }
      },
      error: (err: any) => {
        console.error('Forgot password error:', err);
        this.isSubmitting = false;
        this.errorMessage = this.extractErrorMessage(err, 'Đã có lỗi xảy ra khi gửi yêu cầu khôi phục.');
        this.cdr.detectChanges();
      }
    });
  }

  onResendOtp(): void {
    if (this.isSubmitting || this.resendCountdown > 0) return;

    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.authService.forgotPassword(this.forgotData.email).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.successMessage = 'Mã OTP mới đã được gửi đến email của bạn.';
        this.startResendCountdown();
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        this.isSubmitting = false;
        this.errorMessage = this.extractErrorMessage(err, 'Không thể gửi lại mã OTP. Vui lòng thử lại.');
        this.cdr.detectChanges();
      }
    });
  }

  private startResendCountdown(): void {
    this.clearResendTimer();
    this.resendCountdown = 10;
    this.resendTimer = setInterval(() => {
      this.resendCountdown = Math.max(0, this.resendCountdown - 1);
      if (this.resendCountdown === 0) this.clearResendTimer();
      this.cdr.detectChanges();
    }, 1000);
  }

  private clearResendTimer(): void {
    if (this.resendTimer) {
      clearInterval(this.resendTimer);
      this.resendTimer = undefined;
    }
  }

  onVerifyOtp() {
    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.cdr.detectChanges();

    this.authService.verifyOtp(this.forgotData.email, this.forgotData.otp).subscribe({
      next: (res: any) => {
        this.isSubmitting = false;
        this.successMessage = 'Xác thực thành công. Vui lòng nhập mật khẩu mới.';
        this.forgotStep = 'reset';
        sessionStorage.setItem('forgotStep', 'reset');
        this.cdr.detectChanges();

        setTimeout(() => {
          this.successMessage = '';
          this.cdr.detectChanges();
        }, 3000);
      },
      error: (err: any) => {
        console.error('Verify OTP error:', err);
        this.isSubmitting = false;
        this.errorMessage = this.extractErrorMessage(err, 'Mã OTP không hợp lệ hoặc đã hết hạn.');
        this.cdr.detectChanges();
      }
    });
  }

  onResetPassword() {
    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.cdr.detectChanges();

    if (this.forgotData.newPassword !== this.forgotData.confirmPassword) {
      this.errorMessage = 'Mật khẩu xác nhận không khớp!';
      this.isSubmitting = false;
      this.cdr.detectChanges();
      return;
    }

    this.authService.resetPassword(this.forgotData.email, this.forgotData.otp, this.forgotData.newPassword).subscribe({
      next: (res: any) => {
        this.isSubmitting = false;
        this.successMessage = 'Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại.';
        this.cdr.detectChanges();
        setTimeout(() => {
          this.toggleMode('login', new Event('click'));
        }, 3000);
      },
      error: (err: any) => {
        console.error('Reset password error:', err);
        this.isSubmitting = false;
        this.errorMessage = this.extractErrorMessage(err, 'Đặt lại mật khẩu thất bại.');
        this.cdr.detectChanges();
      }
    });
  }
}
