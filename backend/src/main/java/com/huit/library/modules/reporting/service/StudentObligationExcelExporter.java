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
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                var output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            CellStyle header = workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));

            Writer summary =
                    new Writer(
                            workbook,
                            "Tong hop",
                            report,
                            header,
                            money,
                            date,
                            "MSSV",
                            "Họ tên",
                            "Khoa/ngành nguồn",
                            "Mã khoa",
                            "Khoa",
                            "Ngành",
                            "Phân loại",
                            "Số tài liệu quá hạn",
                            "Hạn trả sớm nhất",
                            "Ngày quá hạn (lịch)",
                            "Phạt/phí phát sinh (VNĐ)",
                            "Đã thanh toán/cấn cọc (VNĐ)",
                            "Miễn giảm (VNĐ)",
                            "Điều chỉnh giảm (VNĐ)",
                            "Nợ theo các khoản phí (VNĐ)",
                            "Nợ lưu trong tài khoản (VNĐ)",
                            "Chênh giữa hai nguồn nợ (VNĐ)",
                            "Chênh trong các khoản phí (VNĐ)",
                            "ID người dùng",
                            "Đối chiếu hai nguồn nợ",
                            "Đối chiếu các khoản phí");
            for (Student student : report.students())
                summary.row(
                        student.mssv(),
                        student.fullName(),
                        student.rawDepartment(),
                        student.facultyId(),
                        student.facultyName(),
                        student.majorName(),
                        StudentObligationLabels.label(student.mappingStatus()),
                        student.overdueItemCount(),
                        student.earliestDueDate(),
                        student.overdueDays(),
                        student.chargedAmount(),
                        student.paidOrDepositAmount(),
                        student.waivedAmount(),
                        student.adjustedAmount(),
                        student.outstandingAmount(),
                        student.walletDebt(),
                        student.walletDifference(),
                        student.reconciliationDifference(),
                        student.userId().toString(),
                        StudentObligationLabels.walletComparison(student),
                        StudentObligationLabels.feeComparison(student));
            summary.finish();

            Map<java.util.UUID, Student> byUser =
                    report.students().stream()
                            .collect(Collectors.toMap(Student::userId, Function.identity()));
            Writer overdue =
                    new Writer(
                            workbook,
                            "Qua han",
                            report,
                            header,
                            money,
                            date,
                            "MSSV",
                            "Họ tên",
                            "Mã phiếu",
                            "Mã chi tiết",
                            "Mã vạch",
                            "Tên tài liệu",
                            "Loại phiếu",
                            "Hạn trả",
                            "Ngày quá hạn (lịch)",
                            "ID người dùng");
            for (var item : report.overdueItems()) {
                Student student = byUser.get(item.userId());
                overdue.row(
                        student.mssv(),
                        student.fullName(),
                        item.ticketId(),
                        item.loanDetailId(),
                        item.barcode(),
                        item.bookTitle(),
                        item.ticketType(),
                        item.dueDate(),
                        item.overdueDays(),
                        item.userId().toString());
            }
            overdue.finish();

            Writer ledger =
                    new Writer(
                            workbook,
                            "So no",
                            report,
                            header,
                            money,
                            date,
                            "MSSV",
                            "Họ tên",
                            "Giao dịch",
                            "Mã phiếu",
                            "Mã chi tiết",
                            "Hồ sơ phạt",
                            "Loại",
                            "Lý do",
                            "Trạng thái",
                            "Giá trị ghi nhận (VNĐ)",
                            "Đã xử lý gồm miễn/điều chỉnh (VNĐ)",
                            "Còn nợ khoản (VNĐ)",
                            "Phương thức gần nhất",
                            "Biên lai",
                            "Ngày ghi nhận",
                            "ID người dùng");
            for (var entry : report.ledgerEntries()) {
                Student student = byUser.get(entry.userId());
                ledger.row(
                        student.mssv(),
                        student.fullName(),
                        entry.transactionId(),
                        entry.ticketId(),
                        entry.loanDetailId(),
                        entry.penaltyCaseId(),
                        StudentObligationLabels.label(entry.type()),
                        entry.reason(),
                        StudentObligationLabels.label(entry.status()),
                        entry.amount(),
                        entry.satisfiedAmount(),
                        entry.outstandingAmount(),
                        StudentObligationLabels.label(entry.paymentMethod()),
                        entry.receiptNumber(),
                        entry.createdAt(),
                        entry.userId().toString());
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
        private final CellStyle header, money, date, text;
        private final String[] columns;
        private Sheet sheet;
        private int nextRow, part;

        Writer(
                SXSSFWorkbook workbook,
                String name,
                StudentObligationReport report,
                CellStyle header,
                CellStyle money,
                CellStyle date,
                String... columns) {
            this.workbook = workbook;
            this.name = name;
            this.report = report;
            this.header = header;
            this.money = money;
            this.date = date;
            this.columns = columns;
            this.text = workbook.createCellStyle();
            this.text.setWrapText(true);
            this.text.setVerticalAlignment(VerticalAlignment.TOP);
            newSheet();
        }

        private void newSheet() {
            sheet = workbook.createSheet(name + (++part > 1 ? " " + part : ""));
            nextRow = 0;
            row("BÁO CÁO QUÁ HẠN / CÔNG NỢ — " + name);
            row(
                    "Chốt dữ liệu: "
                            + report.snapshotAt()
                            + " (Giờ Việt Nam); tiền: VNĐ; loại báo cáo: "
                            + StudentObligationLabels.label(report.filter().mode().name())
                            + "; mã khoa: "
                            + (report.filter().facultyId() == null
                                    ? "Mọi khoa"
                                    : report.filter().facultyId())
                            + "; từ khóa: "
                            + report.filter().query()
                            + "; sinh viên: "
                            + report.totalStudents());
            row(
                    "Nợ theo các khoản phí là phần còn phải trả của các khoản đang mở. Nợ lưu trong tài khoản là số dư nợ lưu riêng. Chênh giữa hai nguồn = nợ tài khoản trừ nợ theo phí; dương là tài khoản cao hơn, âm là thấp hơn. Ô trống ở nợ tài khoản/chênh hai nguồn là chưa có dữ liệu, không phải 0.");
            row(
                    "Chênh trong các khoản phí = phạt/phí phát sinh - thanh toán/cấn cọc - miễn giảm - điều chỉnh - nợ theo các khoản phí. Khác 0 cần kiểm tra chứng từ, chưa kết luận nguyên nhân. Chênh bằng 0 không có nghĩa đã hết nợ. Thanh toán/miễn/điều chỉnh là dòng đối chiếu, không cộng lại thành phí; đã xử lý gồm cả miễn/điều chỉnh, không phải tiền thu. Số hơn 15 chữ số lưu dạng văn bản.");
            CellStyle explanation = workbook.createCellStyle();
            explanation.setWrapText(true);
            explanation.setVerticalAlignment(VerticalAlignment.TOP);
            for (int i = 0; i < 4; i++) {
                sheet.addMergedRegion(
                        new CellRangeAddress(i, i, 0, Math.min(7, columns.length - 1)));
                sheet.getRow(i).getCell(0).setCellStyle(explanation);
                sheet.getRow(i).setHeightInPoints(i == 0 ? 28 : i == 1 ? 44 : i == 2 ? 64 : 90);
            }
            Row labels = sheet.createRow(nextRow++);
            labels.setHeightInPoints(60);
            for (int i = 0; i < columns.length; i++) {
                labels.createCell(i).setCellValue(columns[i]);
                labels.getCell(i).setCellStyle(header);
                sheet.setColumnWidth(
                        i,
                        (i >= 19
                                        ? 48
                                        : i == 1
                                                        || columns[i].contains("Lý do")
                                                        || columns[i].contains("Tên tài liệu")
                                                ? 32
                                                : 23)
                                * 256);
            }
            sheet.createFreezePane(2, 5);
            sheet.getPrintSetup().setLandscape(true);
            sheet.setRepeatingRows(new CellRangeAddress(4, 4, -1, -1));
        }

        void row(Object... values) {
            if (nextRow >= 1_048_576) {
                finish();
                newSheet();
            }
            Row row = sheet.createRow(nextRow++);
            if (values.length > 19) row.setHeightInPoints(60);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value == null) continue;
                Cell cell = row.createCell(i);
                if (value instanceof BigDecimal number) {
                    if (number.precision() > 15) cell.setCellValue(number.toPlainString());
                    else {
                        cell.setCellValue(number.doubleValue());
                        cell.setCellStyle(money);
                    }
                } else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                else if (value instanceof LocalDateTime time) {
                    cell.setCellValue(time);
                    cell.setCellStyle(date);
                }
                // Always a STRING cell for source text, even when it starts with =, +, -, @, tab or
                // CR.
                else {
                    cell.setCellValue(value.toString());
                    if (i >= 19) cell.setCellStyle(text);
                }
            }
        }

        void finish() {
            sheet.setAutoFilter(
                    new CellRangeAddress(4, Math.max(4, nextRow - 1), 0, columns.length - 1));
        }
    }
}
