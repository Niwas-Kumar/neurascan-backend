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
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.config-path}")
    private String configPath;

    @PostConstruct
    public void initialize() throws IOException {
        java.io.File file = new java.io.File(configPath);
        if (!file.exists()) {
            throw new java.io.FileNotFoundException(
                "\n\nCRITICAL ERROR: Firebase Service Account Key not found at: " + file.getAbsolutePath() + 
                "\n1. Please go to Firebase Console > Project Settings > Service Accounts." +
                "\n2. Click 'Generate new private key' and download the JSON." +
                "\n3. Rename it to '" + configPath + "' and place it in the project root: " + 
                new java.io.File(".").getAbsolutePath() + "\n\n"
            );
        }

        FileInputStream serviceAccount = new FileInputStream(file);

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setProjectId("neurascan-8ada2") // Explicitly set to ensure it's never null
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
            System.out.println("DEBUG: FirebaseApp initialized with project ID: " + FirebaseApp.getInstance().getOptions().getProjectId());
        }

        // Verify Firestore database existence at startup
        try {
            FirestoreClient.getFirestore().listCollections().iterator().hasNext();
            System.out.println("DEBUG: [SUCCESS] Firestore connectivity verified for project 'neurascan-8ada2' on database '(default)'.");
        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("DEBUG: [FAILURE] Firestore startup check failed.");
            System.err.println("ERROR MESSAGE: " + msg);
            
            if (msg != null && msg.contains("NOT_FOUND")) {
                System.err.println("\n\n" + "=".repeat(60));
                System.err.println("CRITICAL ERROR: Firestore database '(default)' not found.");
                System.err.println("This happens for two reasons:");
                System.err.println("1. The database hasn't been created yet.");
                System.err.println("2. The database was created in 'DATASTORE MODE' instead of 'NATIVE MODE'.");
                System.err.println("\nACTION REQUIRED:");
                System.err.println("A. Go to: https://console.firebase.google.com/project/neurascan-8ada2/firestore");
                System.err.println("B. If you see 'Datastore Mode' at the top, you MUST delete/re-create or contact Google to switch.");
                System.err.println("C. Ensure you chose 'FIREBASE NATIVE MODE'.");
                System.err.println("=".repeat(60) + "\n\n");
            } else {
                e.printStackTrace();
            }
        }
    }


    @Bean
    public Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }
}
