package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.AuthDTOs;
import com.ai.learningdetection.entity.Parent;
import com.ai.learningdetection.entity.Teacher;
import com.ai.learningdetection.util.JwtUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseLoginService {

    private final Firestore firestore;
    private final JwtUtil jwtUtil;

    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String PARENTS_COLLECTION = "parents";
    private static final String USERS_COLLECTION = "users";

    public AuthDTOs.AuthResponse loginWithGoogle(String idTokenString, String requestedRole, String fallbackPicture) {
        if (idTokenString == null || idTokenString.trim().isEmpty()) {
            log.error("Google Login Failed: Received null or empty ID token");
            throw new BadCredentialsException("Google ID token is required");
        }

        try {
            long loginStartTime = System.currentTimeMillis();
            log.info("Google Login: Starting Firebase ID token verification via Admin SDK...");
            
            // Verify the ID token using Firebase Admin SDK
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idTokenString);
            
            if (decodedToken == null) {
                log.error("Google Login Failed: verifyIdToken returned null");
                throw new BadCredentialsException("Firebase verification failed: Result is null");
            }

            String email = decodedToken.getEmail();
            String name = decodedToken.getName();
            String picture = decodedToken.getPicture();
            
            log.info("Google Login: Verified token for email: {}. Role requested: {}", email, requestedRole);

            // Priority: Token Claim > Frontend Fallback
            if (picture == null || picture.trim().isEmpty()) {
                picture = fallbackPicture;
            }
            
            if (email == null || email.isEmpty()) {
                log.error("Google Login Failed: Firebase ID Token is missing email.");
                throw new BadCredentialsException("Firebase account is missing an email address");
            }
            
            if (name == null || name.strip().isEmpty()) {
                name = email.split("@")[0];
            }

            // OPTIMIZATION: Parallelize teacher and parent lookups instead of sequential
            long lookupStartTime = System.currentTimeMillis();
            CompletableFuture<QuerySnapshot> teacherQueryFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return firestore.collection(TEACHERS_COLLECTION).whereEqualTo("email", email).limit(1).get().get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Teacher query error: {}", e.getMessage());
                    return null;
                }
            });

            CompletableFuture<QuerySnapshot> parentQueryFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return firestore.collection(PARENTS_COLLECTION).whereEqualTo("email", email).limit(1).get().get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Parent query error: {}", e.getMessage());
                    return null;
                }
            });

            // Wait for both queries to complete
            QuerySnapshot teacherQuery = teacherQueryFuture.get();
            QuerySnapshot parentQuery = parentQueryFuture.get();
            long lookupTime = System.currentTimeMillis() - lookupStartTime;

            // Check teacher
            if (teacherQuery != null && !teacherQuery.isEmpty()) {
                log.info("Google Login: Existing teacher found for email: {} (lookup: {}ms)", email, lookupTime);
                DocumentSnapshot doc = teacherQuery.getDocuments().get(0);
                Teacher t = doc.toObject(Teacher.class);
                if (t == null) {
                    log.error("Google Login Data Error: Failed to map document to Teacher object for ID: {}", doc.getId());
                    throw new RuntimeException("Data mapping error: Teacher record corrupted");
                }
                
                // Update verified status and picture if needed (async, don't wait)
                if (!t.isEmailVerified() || (picture != null && !picture.equals(t.getPicture()))) {
                    doc.getReference().update("emailVerified", true, "picture", picture);
                    t.setPicture(picture);
                }
                long totalTime = System.currentTimeMillis() - loginStartTime;
                log.info("Google Login: Teacher login completed in {}ms", totalTime);
                return createResponse(t.getEmail(), "ROLE_TEACHER", t.getId(), t.getName(), t.getPicture());
            }

            // Check parent
            if (parentQuery != null && !parentQuery.isEmpty()) {
                log.info("Google Login: Existing parent found for email: {} (lookup: {}ms)", email, lookupTime);
                DocumentSnapshot doc = parentQuery.getDocuments().get(0);
                Parent p = doc.toObject(Parent.class);
                if (p == null) {
                    log.error("Google Login Data Error: Failed to map document to Parent object for ID: {}", doc.getId());
                    throw new RuntimeException("Data mapping error: Parent record corrupted");
                }
                
                // Update verified status and picture if needed (async, don't wait)
                if (!p.isEmailVerified() || (picture != null && !picture.equals(p.getPicture()))) {
                    doc.getReference().update("emailVerified", true, "picture", picture);
                    p.setPicture(picture);
                }
                long totalTime = System.currentTimeMillis() - loginStartTime;
                log.info("Google Login: Parent login completed in {}ms", totalTime);
                return createResponse(p.getEmail(), "ROLE_PARENT", p.getId(), p.getName(), p.getPicture());
            }

            // 2. Create new account
            long creationStartTime = System.currentTimeMillis();
            log.info("Google Login: Creating new account for email: {} with role: {}", email, requestedRole);
            if ("parent".equalsIgnoreCase(requestedRole)) {
                DocumentReference docRef = firestore.collection(PARENTS_COLLECTION).document();
                Parent p = Parent.builder()
                        .id(docRef.getId())
                        .email(email)
                        .name(name)
                        .password("")
                        .studentId("")
                        .schoolId("")
                        .picture(picture)
                        .emailVerified(true)
                        .build();
                docRef.set(p).get();
                upsertUserProfile(decodedToken.getUid(), email, name, "ROLE_PARENT", "");
                long totalTime = System.currentTimeMillis() - loginStartTime;
                log.info("Google Login: New parent created with ID: {} (total: {}ms)", p.getId(), totalTime);
                return createResponse(p.getEmail(), "ROLE_PARENT", p.getId(), p.getName(), p.getPicture());
            } else {
                DocumentReference docRef = firestore.collection(TEACHERS_COLLECTION).document();
                Teacher t = Teacher.builder()
                        .id(docRef.getId())
                        .email(email)
                        .name(name)
                        .password("")
                        .school("Pending Selection")
                        .schoolId("")
                        .picture(picture)
                        .emailVerified(true)
                        .createdAt(java.time.Instant.now().toString())
                        .updatedAt(java.time.Instant.now().toString())
                        .build();
                docRef.set(t).get();
                upsertUserProfile(decodedToken.getUid(), email, name, "ROLE_TEACHER", t.getSchoolId());
                long totalTime = System.currentTimeMillis() - loginStartTime;
                log.info("Google Login: New teacher created with ID: {} (total: {}ms)", t.getId(), totalTime);
                return createResponse(t.getEmail(), "ROLE_TEACHER", t.getId(), t.getName(), t.getPicture());
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error("Google Login Firestore Error: {}", e.getMessage(), e);
            throw new RuntimeException("Internal database error during Google login", e);
        } catch (com.google.firebase.auth.FirebaseAuthException e) {
            log.warn("Google Login Verification Error: {}", e.getMessage());
            throw new BadCredentialsException("Google account verification failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Google Login Unexpected Error: {}", e.getMessage(), e);
            throw new RuntimeException("Google authentication failed due to an unexpected system error", e);
        }
    }

    private AuthDTOs.AuthResponse createResponse(String email, String role, String userId, String name, String picture) {
        String token = jwtUtil.generateToken(email, role, userId, name, picture);
        return AuthDTOs.AuthResponse.builder()
                .jwtToken(token)
                .userRole(role)
                .userId(userId)
                .userName(name)
                .userEmail(email)
                .picture(picture)
                .message("Login successful via Google")
                .build();
    }

    private void upsertUserProfile(String firebaseUid, String email, String name, String role, String schoolId) {
        try {
            DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(firebaseUid);
            userRef.set(java.util.Map.of(
                    "uid", firebaseUid,
                    "email", email,
                    "name", name,
                    "role", role,
                    "schoolId", schoolId == null ? "" : schoolId,
                    "createdAt", java.time.Instant.now().toString(),
                    "updatedAt", java.time.Instant.now().toString()
            ));
        } catch (Exception e) {
            log.warn("Failed to upsert user profile in users collection: {}", e.getMessage());
        }
    }

}
