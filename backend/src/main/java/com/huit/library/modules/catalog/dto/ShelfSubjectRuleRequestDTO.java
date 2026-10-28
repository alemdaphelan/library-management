package com.huit.library.modules.catalog.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ShelfSubjectRuleRequestDTO(@NotNull Set<Long> subjectIds) {
}
