package com.ai.learningdetection.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.config-path:serviceAccountKey.json}")
    private String configPath;

    @Value("${FIREBASE_SERVICE_ACCOUNT_BASE64:}")
    private String firebaseServiceAccountBase64;

    @PostConstruct
    public void initialize() throws IOException {
        InputStream serviceAccountStream;

        // Priority 1: Environment variable (for production/cloud deployment)
        if (firebaseServiceAccountBase64 != null && !firebaseServiceAccountBase64.isEmpty()) {
            System.out.println("DEBUG: Loading Firebase credentials from FIREBASE_SERVICE_ACCOUNT_BASE64 env var");
            byte[] decoded = Base64.getDecoder().decode(firebaseServiceAccountBase64);
            serviceAccountStream = new ByteArrayInputStream(decoded);
        }
        // Priority 2: Local file (for development)
        else {
            java.io.File file = new java.io.File(configPath);
            if (!file.exists()) {
                throw new java.io.FileNotFoundException(
                    "\n\nCRITICAL ERROR: Firebase Service Account Key not found." +
                    "\nFor LOCAL development: Place 'serviceAccountKey.json' in the project root." +
                    "\nFor PRODUCTION: Set the FIREBASE_SERVICE_ACCOUNT_BASE64 environment variable." +
                    "\n  Generate it with: base64 -w 0 serviceAccountKey.json" +
                    "\n  (On Windows PowerShell: [Convert]::ToBase64String([IO.File]::ReadAllBytes('serviceAccountKey.json')))\n\n"
                );
            }
            System.out.println("DEBUG: Loading Firebase credentials from file: " + file.getAbsolutePath());
            serviceAccountStream = new FileInputStream(file);
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                .setProjectId("neurascan-8ada2")
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
            System.out.println("DEBUG: FirebaseApp initialized with project ID: " + FirebaseApp.getInstance().getOptions().getProjectId());
        }

        // Verify Firestore connectivity
        try {
            FirestoreClient.getFirestore().listCollections().iterator().hasNext();
            System.out.println("DEBUG: [SUCCESS] Firestore connectivity verified.");
        } catch (Exception e) {
            System.err.println("DEBUG: [FAILURE] Firestore startup check failed: " + e.getMessage());
        }
    }

    @Bean
    public Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }
}
