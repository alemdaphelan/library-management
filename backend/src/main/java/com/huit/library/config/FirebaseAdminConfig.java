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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Configuration
public class FirebaseAdminConfig {
    @Bean
    @ConditionalOnProperty(prefix = "library.firebase", name = "enabled", havingValue = "true")
    FirebaseMessaging firebaseMessaging(
            @Value("${library.firebase.project-id:}") String projectId,
            @Value("${library.firebase.service-account-path:}") String serviceAccountPath,
            @Value("${library.firebase.service-account-base64:}") String serviceAccountBase64)
            throws IOException {
        GoogleCredentials credentials =
                resolveCredentials(serviceAccountPath, serviceAccountBase64);

        FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
        if (projectId != null && !projectId.isBlank()) {
            options.setProjectId(projectId.trim());
        }

        FirebaseApp app =
                FirebaseApp.getApps().stream()
                        .findFirst()
                        .orElseGet(() -> FirebaseApp.initializeApp(options.build()));
        return FirebaseMessaging.getInstance(app);
    }

    private GoogleCredentials resolveCredentials(
            String serviceAccountPath, String serviceAccountBase64) throws IOException {
        if (serviceAccountBase64 != null && !serviceAccountBase64.isBlank()) {
            return GoogleCredentials.fromStream(
                    new ByteArrayInputStream(
                            Base64.getDecoder().decode(serviceAccountBase64.trim())));
        }
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            Path path = Path.of(serviceAccountPath.trim());
            if (Files.isReadable(path)) {
                try (InputStream inputStream = Files.newInputStream(path)) {
                    return GoogleCredentials.fromStream(inputStream);
                }
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
