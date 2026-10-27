import { Component, OnInit, Optional } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReaderProfile, Student, StudentUpsert, UserService } from '../../../services/user.service';
import { AuthService } from '../../../services/auth.service';
import { ActivatedRoute, Router } from '@angular/router';
import { DialogService } from '../../../services/dialog.service';

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
  showProfile = false;
  profileLoading = false;
  selectedProfile: ReaderProfile | null = null;
  private requestedUserId?: string;

  constructor(
    private userService: UserService,
    public authService: AuthService,
    private router: Router,
    private dialog: DialogService,
    @Optional() private route?: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.requestedUserId = this.route?.snapshot.queryParamMap.get('referenceId') || undefined;
    this.loadStudents();
  }

  loadStudents() {
    this.loading = true;
    this.userService.getStudents().subscribe({
      next: (data) => {
        this.students = data;
        this.loading = false;
        if (this.requestedUserId) {
          const requested = this.students.find(student => student.userId === this.requestedUserId);
          if (requested) this.openProfile(requested);
          this.requestedUserId = undefined;
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = this.errorText(err, 'Không thể tải danh sách sinh viên.');
      }
    });
  }

  openProfile(student: Student) {
    this.showProfile = true;
    this.profileLoading = true;
    this.selectedProfile = null;
    this.clearMessages();
    this.userService.getReaderProfile(student.mssv).subscribe({
      next: profile => {
        this.selectedProfile = profile;
        this.profileLoading = false;
      },
      error: err => {
        this.profileLoading = false;
        this.showProfile = false;
        this.errorMessage = this.errorText(err, 'Không thể tải hồ sơ nghiệp vụ của bạn đọc.');
      },
    });
  }

  closeProfile() {
    this.showProfile = false;
    this.selectedProfile = null;
  }

  get activeLoanCount(): number {
    return this.selectedProfile?.loans.filter(loan => ['PENDING', 'RESERVED', 'BORROWING', 'OVERDUE'].includes(loan.status)).length || 0;
  }

  get overdueLoanCount(): number {
    return this.selectedProfile?.loans.filter(loan => loan.status === 'OVERDUE' || loan.overdueItemCount > 0).length || 0;
  }

  get outstandingLedgerCount(): number {
    return this.selectedProfile?.ledger.filter(entry =>
      Number(entry.amount) > 0 && Number(entry.paidAmount || 0) < Number(entry.amount)
    ).length || 0;
  }

  openWorkflow(path: string, referenceType: string, referenceId: string | number) {
    this.closeProfile();
    void this.router.navigate([path], { queryParams: { referenceType, referenceId } });
  }

  cardStatusLabel(status?: string): string {
    return ({ ACTIVE: 'Hoạt động', LOCKED: 'Bị khóa', EXPIRED: 'Hết hạn' } as Record<string, string>)[status || ''] || 'Chưa cấp thẻ';
  }

  loanStatusLabel(status: string): string {
    return ({ PENDING: 'Chờ duyệt', RESERVED: 'Chờ nhận', BORROWING: 'Đang mượn', OVERDUE: 'Quá hạn', RETURNED: 'Đã trả', CANCELLED: 'Đã hủy', REJECTED: 'Từ chối' } as Record<string, string>)[status] || status;
  }

  get isReadOnly(): boolean {
    return this.authService.getRole() !== 'ADMIN';
  }

  get filteredStudents(): Student[] {
    const keyword = this.searchTerm.trim().toLocaleLowerCase('vi');
    return this.students.filter(student => {
      const matchesStatus = !this.statusFilter
        || (this.statusFilter === 'UNISSUED' ? !student.cardStatus : student.cardStatus === this.statusFilter);
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

  async deleteStudent(student: Student) {
    if (this.isReadOnly) return;
    const accepted = await this.dialog.confirm({
      title: 'Xóa tài khoản sinh viên',
      message: `Bạn có chắc muốn xóa tài khoản ${student.mssv}? Tài khoản đã có lịch sử nghiệp vụ có thể không xóa được.`,
      type: 'warning',
      confirmText: 'Xóa tài khoản',
    });
    if (!accepted) return;
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
