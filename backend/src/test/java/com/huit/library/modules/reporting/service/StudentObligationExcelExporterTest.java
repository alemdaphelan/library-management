package com.huit.library.modules.reporting.service;

import com.huit.library.modules.reporting.dto.StudentObligationReport;
import com.huit.library.modules.reporting.dto.StudentObligationReport.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StudentObligationExcelExporterTest {
    private final StudentObligationExcelExporter exporter = new StudentObligationExcelExporter();

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    private Student student(Long gap, long reconciliation, long paid) {
        return new Student(
                UUID.randomUUID(),
                "F10",
                "Bạn đọc kiểm thử",
                "CNTT",
                "FIT",
                "Công nghệ thông tin",
                null,
                "MAPPED",
                0,
                null,
                0,
                money(200000),
                money(paid),
                money(20000),
                money(10000),
                money(100000),
                gap == null ? null : money(100000 + gap),
                gap == null ? null : money(gap),
                money(reconciliation));
    }

    private StudentObligationReport report(List<Student> students) {
        return new StudentObligationReport(
                LocalDateTime.of(2026, 11, 4, 10, 0),
                "Asia/Ho_Chi_Minh",
                "VND",
                new Filter(Mode.DEBT, null, ""),
                0,
                25,
                students.size(),
                students,
                List.of(),
                List.of(
                        new LedgerEntry(
                                students.get(0).userId(),
                                1L,
                                null,
                                null,
                                null,
                                "REPAIR_FEE",
                                "Sửa sách",
                                "PARTIALLY_WAIVED",
                                money(200000),
                                money(100000),
                                money(100000),
                                "CASH",
                                "F10-1",
                                LocalDateTime.of(2026, 11, 4, 9, 0))));
    }

    @Test
    void explainsBothSignedGapsAndMissingAccountWithoutChangingNumericSourceColumns()
            throws Exception {
        var rows =
                List.of(
                        student(50000L, 100, 70000),
                        student(-50000L, -100, 70000),
                        student(0L, 0, 70000),
                        student(null, 0, 70000),
                        student(0L, 0, -100));
        try (var workbook =
                new XSSFWorkbook(new ByteArrayInputStream(exporter.export(report(rows))))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            var sheet = workbook.getSheet("Tong hop");
            assertThat(sheet.getRow(4).getCell(14).getStringCellValue())
                    .isEqualTo("Nợ theo các khoản phí (VNĐ)");
            assertThat(sheet.getRow(4).getCell(15).getStringCellValue())
                    .isEqualTo("Nợ lưu trong tài khoản (VNĐ)");
            for (int i = 0; i < rows.size(); i++) {
                var source = rows.get(i);
                var row = sheet.getRow(i + 5);
                assertThat(BigDecimal.valueOf(row.getCell(11).getNumericCellValue()))
                        .isEqualByComparingTo(source.paidOrDepositAmount());
                assertThat(BigDecimal.valueOf(row.getCell(14).getNumericCellValue()))
                        .isEqualByComparingTo(source.outstandingAmount());
                assertThat(BigDecimal.valueOf(row.getCell(17).getNumericCellValue()))
                        .isEqualByComparingTo(source.reconciliationDifference());
                if (source.walletDebt() == null) {
                    assertThat(row.getCell(15)).isNull();
                    assertThat(row.getCell(16)).isNull();
                } else {
                    assertThat(BigDecimal.valueOf(row.getCell(15).getNumericCellValue()))
                            .isEqualByComparingTo(source.walletDebt());
                    assertThat(BigDecimal.valueOf(row.getCell(16).getNumericCellValue()))
                            .isEqualByComparingTo(source.walletDifference());
                }
            }
            assertThat(sheet.getRow(5).getCell(19).getStringCellValue()).contains("cao hơn");
            assertThat(sheet.getRow(6).getCell(19).getStringCellValue()).contains("thấp hơn");
            assertThat(sheet.getRow(7).getCell(19).getStringCellValue()).contains("khớp số");
            assertThat(sheet.getRow(8).getCell(19).getStringCellValue())
                    .contains("không có nghĩa là đã hết nợ");
            assertThat(sheet.getRow(9).getCell(20).getStringCellValue())
                    .contains("thanh toán/cấn cọc âm");
            assertThat(sheet.getRow(5).getCell(20).getStringCellValue()).contains("lớn hơn");
            assertThat(sheet.getRow(6).getCell(20).getStringCellValue()).contains("nhỏ hơn");
            assertThat(sheet.getRow(5).getCell(19).getCellStyle().getWrapText()).isTrue();
            assertThat(sheet.getRow(5).getCell(19).getCellType()).isEqualTo(CellType.STRING);
            for (var tab : workbook) {
                assertThat(tab.getRow(2).getCell(0).getStringCellValue())
                        .contains("dương là tài khoản cao hơn, âm là thấp hơn");
                assertThat(tab.getRow(3).getCell(0).getStringCellValue())
                        .contains("không có nghĩa đã hết nợ");
            }
            var entry = workbook.getSheet("So no").getRow(5);
            assertThat(entry.getCell(6).getStringCellValue()).isEqualTo("Sửa chữa");
            assertThat(entry.getCell(8).getStringCellValue()).isEqualTo("Miễn giảm một phần");
            assertThat(entry.getCell(12).getStringCellValue()).isEqualTo("Tiền mặt");
        }
    }
}
