import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../services/auth.service';
import { OrientationClass, OrientationPerson, OrientationService } from '../../../services/orientation.service';
import { DialogService } from '../../../services/dialog.service';

@Component({ selector: 'app-orientation', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './orientation.html', styleUrls: ['./orientation.css'] })
export class Orientation implements OnInit {
  classes: OrientationClass[] = []; selected: OrientationClass | null = null;
  students: OrientationPerson[] = []; facilitators: OrientationPerson[] = [];
  loading = false; error = ''; success = ''; showForm = false; editingId: number | null = null; selectedStudentIds: string[] = []; randomCount = 0;
  weekStart = this.monday(new Date());
  form = { title: 'Hướng dẫn sử dụng thư viện', location: '', startAt: '', endAt: '', capacity: 30, facilitatorId: '' };
  constructor(private service: OrientationService, public auth: AuthService, private dialog: DialogService) {}
  ngOnInit() { this.load(); }
  get isAdmin() { return this.auth.getRole() === 'ADMIN'; }
  get weekDays() { return Array.from({length: 7}, (_, i) => { const d = new Date(this.weekStart); d.setDate(d.getDate() + i); return d; }); }
  classesFor(day: Date) { return this.classes.filter(item => new Date(item.startAt).toDateString() === day.toDateString()); }
  dayLabel(day: Date) { return ['Chủ nhật','Thứ Hai','Thứ Ba','Thứ Tư','Thứ Năm','Thứ Sáu','Thứ Bảy'][day.getDay()]; }
  previousWeek() { this.weekStart = new Date(this.weekStart.getTime() - 7 * 86400000); }
  nextWeek() { this.weekStart = new Date(this.weekStart.getTime() + 7 * 86400000); }
  load() { this.loading = true; this.service.list().subscribe({ next: value => { this.classes = value || []; this.loading = false; if (this.selected) this.selected = this.classes.find(c => c.classId === this.selected!.classId) || null; }, error: e => this.fail(e, 'Không thể tải lịch lớp hướng dẫn.') }); }
  openCreate() { this.editingId = null; this.showForm = true; this.facilitators = []; this.form = { title: 'Hướng dẫn sử dụng thư viện', location: '', startAt: '', endAt: '', capacity: 30, facilitatorId: '' }; }
  openEdit(item: OrientationClass) { if (!this.isAdmin) return; this.editingId = item.classId; this.showForm = true; this.facilitators = item.facilitatorId ? [{ userId: item.facilitatorId, fullName: item.facilitatorName || 'Người phụ trách hiện tại' }] : []; this.form = { title: item.title, location: item.location, startAt: this.localDateTime(item.startAt), endAt: this.localDateTime(item.endAt), capacity: item.capacity, facilitatorId: item.facilitatorId || '' }; }
  loadFacilitators() { if (!this.form.startAt || !this.form.endAt) return; this.service.facilitators(this.form.startAt, this.form.endAt).subscribe({ next: value => this.facilitators = value || [], error: e => this.fail(e, 'Không thể tải người phụ trách phù hợp.') }); }
  save() { if (!this.form.title || !this.form.location || !this.form.startAt || !this.form.endAt) return; const request = { ...this.form, facilitatorId: this.form.facilitatorId || null }; const operation = this.editingId ? this.service.update(this.editingId, request) : this.service.create(request); operation.subscribe({ next: () => { this.showForm = false; this.success = this.editingId ? 'Đã cập nhật lớp hướng dẫn.' : 'Đã tạo lớp hướng dẫn.'; this.editingId = null; this.load(); }, error: e => this.fail(e, this.editingId ? 'Không thể cập nhật lớp.' : 'Không thể tạo lớp.') }); }
  openClass(item: OrientationClass) { this.selected = item; this.selectedStudentIds = []; this.randomCount = 0; if (this.isAdmin) this.service.availableStudents(item.classId).subscribe({ next: value => this.students = value || [] }); }
  toggleStudent(id: string, checked: boolean) { this.selectedStudentIds = checked ? [...this.selectedStudentIds, id] : this.selectedStudentIds.filter(x => x !== id); }
  assign() { if (!this.selected) return; this.service.assign(this.selected.classId, this.selectedStudentIds, Number(this.randomCount) || 0).subscribe({ next: value => { this.selected = value; this.success = 'Đã cập nhật danh sách lớp.'; this.load(); }, error: e => this.fail(e, 'Không thể xếp lớp.') }); }
  setAttendance(enrollment: any, status: string) { if (!this.selected) return; this.service.attendance(this.selected.classId, [{ userId: enrollment.student.userId, status, note: enrollment.note || '' }]).subscribe({ next: value => { this.selected = value; this.success = 'Đã lưu điểm danh.'; this.load(); }, error: e => this.fail(e, 'Không thể lưu điểm danh.') }); }
  downloadTemplate() { if (!this.selected) return; this.service.template(this.selected.classId).subscribe(response => { if (!response.body) return; const url = URL.createObjectURL(response.body); const a = document.createElement('a'); a.href = url; a.download = `diem-danh-${this.selected!.classCode}.xlsx`; a.click(); URL.revokeObjectURL(url); }); }
  importExcel(event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (!file || !this.selected) return; this.service.importAttendance(this.selected.classId, file).subscribe({ next: value => { this.selected = value; this.success = 'Đã nhập điểm danh từ Excel.'; this.load(); }, error: e => this.fail(e, 'Không thể nhập tệp Excel.') }); }
  async deleteClass(item: OrientationClass) { if (!this.isAdmin) return; const confirmed = await this.dialog.confirm({ title: 'Xóa lớp hướng dẫn', message: `Xóa lớp ${item.classCode}?`, type: 'warning', confirmText: 'Xóa lớp' }); if (!confirmed) return; this.service.delete(item.classId).subscribe({ next: () => { this.selected = null; this.load(); }, error: e => this.fail(e, 'Không thể xóa lớp.') }); }
  statusLabel(status: string) { return ({ SCHEDULED: 'Đã lên lịch', COMPLETED: 'Đã hoàn tất', ASSIGNED: 'Chưa điểm danh', ATTENDED: 'Có mặt', ABSENT: 'Vắng', EXCUSED: 'Vắng có phép' } as any)[status] || status; }
  private monday(date: Date) { const d = new Date(date); const day = d.getDay() || 7; d.setDate(d.getDate() - day + 1); d.setHours(0,0,0,0); return d; }
  private localDateTime(value: string) { const d = new Date(value); const offset = d.getTimezoneOffset() * 60000; return new Date(d.getTime() - offset).toISOString().slice(0,16); }
  private fail(error: any, fallback: string) { this.loading = false; this.error = error?.error?.message || error?.error?.detail || fallback; }
}
