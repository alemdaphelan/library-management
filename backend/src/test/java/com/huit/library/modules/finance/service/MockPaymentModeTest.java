package com.huit.library.modules.finance.service;

import com.huit.library.modules.finance.controller.MockPaymentController;
import com.huit.library.modules.finance.controller.PaymentCapabilitiesController;
import com.huit.library.modules.finance.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MockPaymentModeTest {
    @Configuration(proxyBeanMethods = false)
    @Import({
        MockPaymentService.class,
        MockPaymentController.class,
        PaymentCapabilitiesController.class
    })
    static class Dependencies {
        @Bean
        OnlinePaymentSessionRepository sessions() {
            return mock(OnlinePaymentSessionRepository.class);
        }

        @Bean
        WalletRepository wallets() {
            return mock(WalletRepository.class);
        }

        @Bean
        DebtLedgerService ledger() {
            return mock(DebtLedgerService.class);
        }

        @Bean
        JdbcTemplate jdbc() {
            return mock(JdbcTemplate.class);
        }
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "default;false;false",
                "default;true;false",
                "prod;true;false",
                "production;true;false",
                "dev,prod;true;false",
                "test,production;true;false",
                "dev;false;false",
                "dev;true;true",
                "test;true;true"
            },
            delimiter = ';')
    void productionAndUnconfiguredModesDoNotRegisterAnyMockPaymentRoute(
            String profiles, boolean flag, boolean enabled) {
        new ApplicationContextRunner()
                .withUserConfiguration(Dependencies.class)
                .withPropertyValues(
                        "spring.profiles.active=" + profiles,
                        "library.payment.mock.enabled=" + flag)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBeansOfType(MockPaymentController.class))
                                    .hasSize(enabled ? 1 : 0);
                            assertThat(context.getBeansOfType(MockPaymentService.class))
                                    .hasSize(enabled ? 1 : 0);
                            assertThat(
                                            context.getBean(PaymentCapabilitiesController.class)
                                                    .get()
                                                    .get("mockEnabled"))
                                    .isEqualTo(enabled);
                        });
    }
}
