package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.School;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchoolService {

    private final Firestore firestore;
    private static final String SCHOOLS_COLLECTION = "schools";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Create a new school with an auto-generated unique code.
     */
    public School createSchool(String name, String address) {
        try {
            String code = generateUniqueCode();
            DocumentReference docRef = firestore.collection(SCHOOLS_COLLECTION).document();

            School school = School.builder()
                    .id(docRef.getId())
                    .name(name)
                    .code(code)
                    .address(address)
                    .active(true)
                    .createdAt(Instant.now().toString())
                    .updatedAt(Instant.now().toString())
                    .build();

            docRef.set(school).get();
            log.info("✓ Created school: {} with code: {}", name, code);
            return school;
        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Failed to create school: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create school", e);
        }
    }

    /**
     * Validate a school code and return the school if valid.
     */
    public School validateSchoolCode(String code) {
        try {
            QuerySnapshot query = firestore.collection(SCHOOLS_COLLECTION)
                    .whereEqualTo("code", code.toUpperCase().trim())
                    .whereEqualTo("active", true)
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                return null;
            }
            return query.getDocuments().get(0).toObject(School.class);
        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Failed to validate school code: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to validate school code", e);
        }
    }

    /**
     * Get all schools.
     */
    public List<School> getAllSchools() {
        try {
            QuerySnapshot query = firestore.collection(SCHOOLS_COLLECTION)
                    .orderBy("name")
                    .get().get();

            return query.getDocuments().stream()
                    .map(doc -> doc.toObject(School.class))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Failed to fetch schools: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch schools", e);
        }
    }

    /**
     * Get school by ID.
     */
    public School getSchoolById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(SCHOOLS_COLLECTION).document(id).get().get();
            if (!doc.exists()) return null;
            return doc.toObject(School.class);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch school", e);
        }
    }

    /**
     * Toggle school active status.
     */
    public void toggleSchoolStatus(String id, boolean active) {
        try {
            firestore.collection(SCHOOLS_COLLECTION).document(id)
                    .update("active", active, "updatedAt", Instant.now().toString()).get();
            log.info("School {} status changed to active={}", id, active);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to update school status", e);
        }
    }

    /**
     * Regenerate the school code.
     */
    public String regenerateSchoolCode(String id) {
        try {
            String newCode = generateUniqueCode();
            firestore.collection(SCHOOLS_COLLECTION).document(id)
                    .update("code", newCode, "updatedAt", Instant.now().toString()).get();
            log.info("Regenerated code for school {}: {}", id, newCode);
            return newCode;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to regenerate school code", e);
        }
    }

    /**
     * Generate a unique 8-character alphanumeric school code.
     */
    private String generateUniqueCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // No 0/O/1/I to avoid confusion
        StringBuilder code = new StringBuilder("SCH-");
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return code.toString();
    }
}
