package com.huit.library.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class FirebaseAdminConfig {
    @Bean
    @ConditionalOnProperty(prefix = "library.firebase", name = "enabled", havingValue = "true")
    FirebaseMessaging firebaseMessaging(
            @Value("${library.firebase.project-id:}") String projectId,
            @Value("${library.firebase.service-account-base64:}") String serviceAccountBase64)
            throws IOException {
        GoogleCredentials credentials = serviceAccountBase64 == null || serviceAccountBase64.isBlank()
                ? GoogleCredentials.getApplicationDefault()
                : GoogleCredentials.fromStream(new ByteArrayInputStream(
                        Base64.getDecoder().decode(serviceAccountBase64.trim())));

        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(credentials);
        if (projectId != null && !projectId.isBlank()) {
            options.setProjectId(projectId.trim());
        }

        FirebaseApp app = FirebaseApp.getApps().stream()
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options.build()));
        return FirebaseMessaging.getInstance(app);
    }
}
