package com.huit.library.modules.recommendation.dto;

public record RealHistoryAuditDTO(
        String dataSource,
        int interactions,
        int distinctUsers,
        int eligibleUsers,
        int trainingInteractions,
        int testInteractions,
        boolean sufficientForPublication,
        String splitRule,
        String note
) {
}
