package com.huit.library.modules.reporting.service;

import com.huit.library.modules.reporting.dto.ProposalSummaryReport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class ProposalSummaryExcelExporter {
    public byte[] export(ProposalSummaryReport report) throws IOException {
        try (var workbook = new SXSSFWorkbook(100); var output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            var header = workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); header.setFont(font); header.setWrapText(true);
            var date = workbook.createCellStyle(); date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
            var summary = new Writer(workbook, "Tong hop", report, header, date, "Mã nhóm", "Cách gộp", "ISBN chuẩn", "Tên sách", "Tác giả",
                    "NXB", "Năm", "Ấn bản", "Số lượt", "Số người", "Cần kiểm tra");
            var faculties = new Writer(workbook, "Theo khoa", report, header, date, "Mã nhóm", "Tên sách", "Mã khoa", "Khoa", "Số lượt", "Số người");
            var originals = new Writer(workbook, "De xuat goc", report, header, date, "Mã nhóm", "Mã đề xuất", "User ID", "MSSV", "Người gửi", "Department gốc",
                    "Mã khoa", "Khoa", "Tên sách gốc", "Tác giả gốc", "NXB gốc", "ISBN gốc", "Năm", "Ấn bản", "Trạng thái", "Ngày gửi");
            for (var g : report.groups()) {
                summary.row(g.groupKey(), g.identityType(), g.canonicalIsbn(), g.title(), g.authorName(), g.publisher(), g.publicationYear(),
                        g.edition(), g.proposalCount(), g.peopleCount(), String.join("; ", g.warnings()));
                for (var f : g.faculties()) faculties.row(g.groupKey(), g.title(), f.facultyId(), f.facultyName(), f.proposalCount(), f.peopleCount());
                for (var s : g.sources()) originals.row(g.groupKey(), s.proposalId().toString(), s.userId(), s.mssv(), s.userName(), s.rawDepartment(),
                        s.facultyId(), s.facultyName(), s.title(), s.authorName(), s.publisher(), s.isbn(), s.publicationYear(), s.edition(), s.status(), s.createdAt());
            }
            summary.finish(); faculties.finish(); originals.finish();
            try { workbook.write(output); return output.toByteArray(); }
            finally { workbook.dispose(); }
        }
    }
    private static class Writer {
        private final SXSSFWorkbook book;
        private final String name;
        private final ProposalSummaryReport report;
        private final CellStyle header, date;
        private final String[] labels;
        private Sheet sheet;
        private int row, part;
        Writer(SXSSFWorkbook book, String name, ProposalSummaryReport report, CellStyle header, CellStyle date, String... labels) {
            this.book = book; this.name = name; this.report = report; this.header = header; this.date = date; this.labels = labels; open();
        }
        private void open() {
            sheet = book.createSheet(name + (++part == 1 ? "" : " " + part)); row = 0;
            row("TỔNG HỢP ĐỀ XUẤT MUA SÁCH — " + name);
            row("Chốt: " + report.snapshotAt() + " (" + report.timezone() + "); khoa=" + report.filter().facultyId()
                    + "; từ=" + report.filter().from() + "; đến=" + report.filter().to() + "; trạng thái=" + report.filter().status());
            row("Nhóm=" + report.totalGroups() + "; lượt=" + report.totalProposals() + "; người=" + report.totalPeople()
                    + ". Khoa hiện tại từ mapping; không phải khoa lịch sử. Lượt lặp không đồng nghĩa cần mua thêm bản.");
            row("Tra sheet De xuat goc theo mã nhóm; số người tổng đếm riêng, không cộng số người các nhóm. Không tự duyệt/gửi cho khoa.");
            Row titles = sheet.createRow(row++); titles.setHeightInPoints(32);
            for (int i = 0; i < labels.length; i++) {
                var cell = titles.createCell(i); cell.setCellValue(labels[i]); cell.setCellStyle(header);
                sheet.setColumnWidth(i, (labels[i].contains("Tên") || labels[i].equals("Khoa") || labels[i].equals("Cần kiểm tra") ? 38 : 22) * 256);
            }
            sheet.createFreezePane(0, 5); sheet.getPrintSetup().setLandscape(true);
            sheet.setRepeatingRows(new CellRangeAddress(4, 4, -1, -1));
        }
        void row(Object... values) {
            if (row >= 1_048_576) { finish(); open(); }
            Row target = sheet.createRow(row++);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i]; if (value == null) continue;
                var cell = target.createCell(i);
                if (value instanceof Number n) cell.setCellValue(n.doubleValue());
                else if (value instanceof LocalDateTime t) { cell.setCellValue(t); cell.setCellStyle(date); }
                // Source text always stays STRING; never evaluate =, +, -, @ or control-prefixed input.
                else cell.setCellValue(value.toString());
            }
        }
        void finish() { sheet.setAutoFilter(new CellRangeAddress(4, Math.max(4, row - 1), 0, labels.length - 1)); }
    }
}
