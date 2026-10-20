import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Student, StudentUpsert, UserService } from '../../../services/user.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './accounts.html',
  styleUrls: ['./accounts.css']
})
export class Accounts implements OnInit {
  students: Student[] = [];
  searchTerm = '';
  statusFilter = '';
  loading = false;
  saving = false;
  errorMessage = '';
  successMessage = '';
  editingMssv: string | null = null;
  showForm = false;
  form: StudentUpsert = this.emptyForm();

  constructor(private userService: UserService, public authService: AuthService) {}

  ngOnInit() {
    this.loadStudents();
  }

  loadStudents() {
    this.loading = true;
    this.userService.getStudents().subscribe({
      next: (data) => {
        this.students = data;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = this.errorText(err, 'Không thể tải danh sách sinh viên.');
      }
    });
  }

  get isReadOnly(): boolean {
    return this.authService.getRole() !== 'ADMIN';
  }

  get filteredStudents(): Student[] {
    const keyword = this.searchTerm.trim().toLocaleLowerCase('vi');
    return this.students.filter(student => {
      const matchesStatus = !this.statusFilter || student.cardStatus === this.statusFilter;
      const text = `${student.fullName} ${student.mssv} ${student.email} ${student.department || ''}`.toLocaleLowerCase('vi');
      return matchesStatus && (!keyword || text.includes(keyword));
    });
  }

  openCreate() {
    if (this.isReadOnly) return;
    this.editingMssv = null;
    this.form = this.emptyForm();
    this.showForm = true;
    this.clearMessages();
  }

  openEdit(student: Student) {
    if (this.isReadOnly) return;
    this.editingMssv = student.mssv;
    this.form = {
      mssv: student.mssv,
      fullName: student.fullName,
      email: student.email,
      department: student.department || '',
      phone: student.phone || '',
      cardStatus: (student.cardStatus as StudentUpsert['cardStatus']) || 'ACTIVE',
      cardExpiryDate: student.cardExpiryDate ? student.cardExpiryDate.slice(0, 16) : null
    };
    this.showForm = true;
    this.clearMessages();
  }

  saveStudent() {
    if (this.isReadOnly || !this.form.mssv.trim() || !this.form.fullName.trim() || !this.form.email.trim()) return;
    this.saving = true;
    this.clearMessages();
    const request = { ...this.form, cardExpiryDate: this.form.cardExpiryDate || null };
    const operation = this.editingMssv
      ? this.userService.updateStudent(this.editingMssv, request)
      : this.userService.createStudent(request);
    operation.subscribe({
      next: () => {
        this.saving = false;
        this.showForm = false;
        this.successMessage = this.editingMssv ? 'Đã cập nhật hồ sơ bạn đọc.' : 'Đã tạo tài khoản sinh viên.';
        this.loadStudents();
      },
      error: err => {
        this.saving = false;
        this.errorMessage = this.errorText(err, 'Không thể lưu hồ sơ bạn đọc.');
      }
    });
  }

  deleteStudent(student: Student) {
    if (this.isReadOnly || !window.confirm(`Xóa tài khoản ${student.mssv}?`)) return;
    this.clearMessages();
    this.userService.deleteStudent(student.mssv).subscribe({
      next: () => {
        this.successMessage = 'Đã xóa tài khoản sinh viên.';
        this.loadStudents();
      },
      error: err => this.errorMessage = this.errorText(err,
        'Không thể xóa tài khoản đã có lịch sử; hãy chuyển trạng thái thẻ sang Bị khóa.')
    });
  }

  private emptyForm(): StudentUpsert {
    return { mssv: '', fullName: '', email: '', department: '', phone: '', cardStatus: 'ACTIVE', cardExpiryDate: null };
  }

  private clearMessages() {
    this.errorMessage = '';
    this.successMessage = '';
  }

  private errorText(error: any, fallback: string): string {
    return error?.error?.detail || error?.error?.message || (typeof error?.error === 'string' ? error.error : fallback);
  }
}
