package com.huit.library.modules.recommendation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationEvaluationAuthorizationContractTest {
    @Test
    void evaluationEndpointIsRestrictedToAdmin() throws NoSuchMethodException {
        Method method = RecommendationEvaluationController.class.getMethod(
                "evaluate", int.class, int.class);

        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('ADMIN')");
    }
}
