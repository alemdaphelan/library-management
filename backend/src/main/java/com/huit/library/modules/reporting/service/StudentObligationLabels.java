package com.huit.library.modules.reporting.service;

import com.huit.library.modules.reporting.dto.StudentObligationReport.Student;

/** Presentation only: report amounts and their reconciliation signs remain authoritative. */
final class StudentObligationLabels {
    private StudentObligationLabels() {}

    static String walletComparison(Student student) {
        if (student.walletDebt() == null || student.walletDifference() == null)
            return "Chưa có số dư nợ trong tài khoản để so sánh; không có nghĩa là đã hết nợ.";
        return student.walletDifference().signum() > 0
                ? "Nợ lưu trong tài khoản cao hơn nợ theo các khoản phí."
                : student.walletDifference().signum() < 0
                        ? "Nợ lưu trong tài khoản thấp hơn nợ theo các khoản phí."
                        : "Hai nguồn nợ đang khớp số.";
    }

    static String feeComparison(Student student) {
        String result =
                student.reconciliationDifference().signum() > 0
                        ? "Phí còn lại sau các khoản giảm lớn hơn nợ theo các khoản phí."
                        : student.reconciliationDifference().signum() < 0
                                ? "Phí còn lại sau các khoản giảm nhỏ hơn nợ theo các khoản phí."
                                : "Đối chiếu các khoản phí đang khớp.";
        return student.paidOrDepositAmount().signum() < 0
                ? result + " Số thanh toán/cấn cọc âm, cần kiểm tra các khoản đã giảm."
                : result;
    }

    static String label(String value) {
        if (value == null) return "Chưa ghi nhận";
        return switch (value) {
            case "EITHER" -> "Quá hạn hoặc còn nợ";
            case "OVERDUE" -> "Còn tài liệu quá hạn";
            case "DEBT" -> "Còn công nợ";
            case "MAPPED" -> "Đã phân loại";
            case "UNMAPPED" -> "Cần bổ sung phân loại";
            case "FINE" -> "Phí trễ";
            case "REPAIR_FEE" -> "Sửa chữa";
            case "COMPENSATION" -> "Đền bù";
            case "TECHNICAL_FEE" -> "Xử lý kỹ thuật";
            case "PAYMENT" -> "Thanh toán";
            case "ADJUSTMENT" -> "Điều chỉnh giảm";
            case "WAIVER_ADJUSTMENT" -> "Miễn giảm";
            case "OUTSTANDING" -> "Chưa thanh toán";
            case "PARTIALLY_PAID" -> "Thanh toán một phần";
            case "PARTIALLY_ADJUSTED" -> "Điều chỉnh một phần";
            case "PARTIALLY_WAIVED" -> "Miễn giảm một phần";
            case "PAID", "COMPLETED" -> "Đã hoàn tất";
            case "PENDING" -> "Chờ xử lý";
            case "CANCELLED" -> "Đã hủy";
            case "WAIVED" -> "Đã miễn";
            case "ADJUSTED" -> "Đã điều chỉnh";
            case "CASH" -> "Tiền mặt";
            case "BANK_TRANSFER" -> "Chuyển khoản";
            case "SECURITY_DEPOSIT" -> "Cấn cọc";
            case "MOCK" -> "Giả lập";
            default -> value;
        };
    }
}
