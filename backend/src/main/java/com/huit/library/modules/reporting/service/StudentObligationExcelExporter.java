package com.huit.library.modules.reporting.service;

import com.huit.library.modules.reporting.dto.StudentObligationReport;
import com.huit.library.modules.reporting.dto.StudentObligationReport.Student;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StudentObligationExcelExporter {
    public byte[] export(StudentObligationReport report) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); var output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            CellStyle header = workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);
            Font font = workbook.createFont();
            font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); header.setFont(font);
            CellStyle money = workbook.createCellStyle(); money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            CellStyle date = workbook.createCellStyle(); date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));

            Writer summary = new Writer(workbook, "Tong hop", report, header, money, date,
                    "MSSV", "Họ tên", "Department gốc", "Mã khoa", "Khoa", "Ngành", "Mapping", "Số tài liệu quá hạn",
                    "Hạn trả sớm nhất", "Ngày quá hạn (lịch)", "Nghĩa vụ đã ghi sổ (VNĐ)", "Đã thanh toán/cấn cọc (VNĐ)",
                    "Miễn giảm (VNĐ)", "Điều chỉnh giảm (VNĐ)", "Dư nợ ledger (VNĐ)", "Dư nợ ví (VNĐ)",
                    "Chênh ví - ledger (VNĐ)", "Chênh đối soát ledger (VNĐ)", "User ID");
            for (Student student : report.students()) summary.row(student.mssv(), student.fullName(), student.rawDepartment(),
                    student.facultyId(), student.facultyName(), student.majorName(), student.mappingStatus(), student.overdueItemCount(),
                    student.earliestDueDate(), student.overdueDays(), student.chargedAmount(), student.paidOrDepositAmount(),
                    student.waivedAmount(), student.adjustedAmount(), student.outstandingAmount(), student.walletDebt(),
                    student.walletDifference(), student.reconciliationDifference(), student.userId().toString());
            summary.finish();

            Map<java.util.UUID, Student> byUser = report.students().stream().collect(Collectors.toMap(Student::userId, Function.identity()));
            Writer overdue = new Writer(workbook, "Qua han", report, header, money, date,
                    "MSSV", "Họ tên", "Mã phiếu", "Mã chi tiết", "Barcode", "Tên tài liệu", "Loại phiếu", "Hạn trả", "Ngày quá hạn (lịch)", "User ID");
            for (var item : report.overdueItems()) {
                Student student = byUser.get(item.userId());
                overdue.row(student.mssv(), student.fullName(), item.ticketId(), item.loanDetailId(), item.barcode(),
                        item.bookTitle(), item.ticketType(), item.dueDate(), item.overdueDays(), item.userId().toString());
            }
            overdue.finish();

            Writer ledger = new Writer(workbook, "So no", report, header, money, date,
                    "MSSV", "Họ tên", "Giao dịch", "Mã phiếu", "Mã chi tiết", "Hồ sơ phạt", "Loại", "Lý do", "Trạng thái",
                    "Giá trị ghi sổ (VNĐ)", "Đã đáp ứng gồm miễn/điều chỉnh (VNĐ)", "Còn nợ khoản (VNĐ)", "Phương thức gần nhất", "Biên lai", "Ngày ghi sổ", "User ID");
            for (var entry : report.ledgerEntries()) {
                Student student = byUser.get(entry.userId());
                ledger.row(student.mssv(), student.fullName(), entry.transactionId(), entry.ticketId(), entry.loanDetailId(), entry.penaltyCaseId(),
                        entry.type(), entry.reason(), entry.status(), entry.amount(), entry.satisfiedAmount(), entry.outstandingAmount(),
                        entry.paymentMethod(), entry.receiptNumber(), entry.createdAt(), entry.userId().toString());
            }
            ledger.finish();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static class Writer {
        private final SXSSFWorkbook workbook;
        private final String name;
        private final StudentObligationReport report;
        private final CellStyle header, money, date;
        private final String[] columns;
        private Sheet sheet;
        private int nextRow, part;

        Writer(SXSSFWorkbook workbook, String name, StudentObligationReport report, CellStyle header, CellStyle money, CellStyle date, String... columns) {
            this.workbook = workbook; this.name = name; this.report = report; this.header = header;
            this.money = money; this.date = date; this.columns = columns;
            newSheet();
        }
        private void newSheet() {
            sheet = workbook.createSheet(name + (++part > 1 ? " " + part : ""));
            nextRow = 0;
            row("BÁO CÁO QUÁ HẠN / CÔNG NỢ — " + name);
            row("Chốt: " + report.snapshotAt() + " (" + report.timezone() + "); tiền: VND; chế độ: " + report.filter().mode()
                    + "; facultyId: " + report.filter().facultyId() + "; từ khóa: " + report.filter().query() + "; sinh viên: " + report.totalStudents());
            row("Chỉ tiền đã ghi sổ; không ước tính phí chưa ghi. Tiền thanh toán/cấn cọc không gồm miễn/điều chỉnh. Số ngày theo lịch; quá hạn trong ngày có thể là 0.");
            row("So no: PAYMENT/miễn/điều chỉnh là dòng đối chiếu, không cộng lại vào nghĩa vụ. Ví trống = chưa có ví; chênh khác 0 cần đối soát. Số quá 15 chữ số lưu dạng text để giữ chính xác.");
            Row labels = sheet.createRow(nextRow++);
            labels.setHeightInPoints(45);
            for (int i = 0; i < columns.length; i++) {
                labels.createCell(i).setCellValue(columns[i]); labels.getCell(i).setCellStyle(header);
                sheet.setColumnWidth(i, (i == 1 || columns[i].contains("Lý do") || columns[i].contains("Tên tài liệu") ? 32 : 23) * 256);
            }
            sheet.createFreezePane(2, 5);
            sheet.getPrintSetup().setLandscape(true);
            sheet.setRepeatingRows(new CellRangeAddress(4, 4, -1, -1));
        }
        void row(Object... values) {
            if (nextRow >= 1_048_576) { finish(); newSheet(); }
            Row row = sheet.createRow(nextRow++);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value == null) continue;
                Cell cell = row.createCell(i);
                if (value instanceof BigDecimal number) {
                    if (number.precision() > 15) cell.setCellValue(number.toPlainString());
                    else { cell.setCellValue(number.doubleValue()); cell.setCellStyle(money); }
                } else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                else if (value instanceof LocalDateTime time) { cell.setCellValue(time); cell.setCellStyle(date); }
                // Always a STRING cell for source text, even when it starts with =, +, -, @, tab or CR.
                else cell.setCellValue(value.toString());
            }
        }
        void finish() { sheet.setAutoFilter(new CellRangeAddress(4, Math.max(4, nextRow - 1), 0, columns.length - 1)); }
    }
}
