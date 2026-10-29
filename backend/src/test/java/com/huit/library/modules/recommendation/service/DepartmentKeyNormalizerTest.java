package com.huit.library.modules.recommendation.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentKeyNormalizerTest {
    @Test
    void normalizesVietnameseDepartmentNamesToStableLookupKeys() {
        assertThat(DepartmentKeyNormalizer.normalize("  Khoa Công nghệ Thông tin "))
                .isEqualTo("khoa cong nghe thong tin");
        assertThat(DepartmentKeyNormalizer.normalize("Tài chính - Ngân hàng"))
                .isEqualTo("tai chinh ngan hang");
    }

    @Test
    void returnsEmptyKeyForMissingDepartment() {
        assertThat(DepartmentKeyNormalizer.normalize(null)).isEmpty();
        assertThat(DepartmentKeyNormalizer.normalize("   ")).isEmpty();
    }
}
