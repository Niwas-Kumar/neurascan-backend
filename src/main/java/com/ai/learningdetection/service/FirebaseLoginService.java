package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.AuthDTOs;
import com.ai.learningdetection.entity.Parent;
import com.ai.learningdetection.entity.Teacher;
import com.ai.learningdetection.util.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseLoginService {

    private final Firestore firestore;
    private final JwtUtil jwtUtil;

    @Value("${google.client-id}")
    private String googleClientId;

    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String PARENTS_COLLECTION = "parents";

    public AuthDTOs.AuthResponse loginWithGoogle(String idTokenString, String requestedRole, String fallbackPicture) {
        try {
            log.info("Starting Google ID token verification via Google API Client...");
            
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            
            if (idToken == null) {
                throw new BadCredentialsException("Invalid Google ID Token");
            }
            
            GoogleIdToken.Payload payload = idToken.getPayload();

            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");
            
            // Priority: Token Claim > Frontend Fallback
            if (picture == null || picture.trim().isEmpty()) {
                picture = fallbackPicture;
            }
            
            if (email == null || email.isEmpty()) {
                log.error("Google ID Token payload is missing email. Payload: {}", payload);
                throw new BadCredentialsException("Google account is missing an email address after ID token verification");
            }
            
            if (name == null || name.strip().isEmpty()) {
                String givenName = (String) payload.get("given_name");
                String familyName = (String) payload.get("family_name");
                if (givenName != null || familyName != null) {
                    name = (givenName != null ? givenName : "") + " " + (familyName != null ? familyName : "");
                    name = name.trim();
                }
            }
            if (name == null || name.strip().isEmpty()) {
                name = email.split("@")[0];
            }

            log.info("Starting Google authentication for email: {}", email);

            // 1. Check existing
            log.debug("Checking teachers collection...");
            QuerySnapshot teacherQuery = firestore.collection(TEACHERS_COLLECTION).whereEqualTo("email", email).limit(1).get().get();
            if (!teacherQuery.isEmpty()) {
                log.info("Existing teacher found for email: {}", email);
                DocumentSnapshot doc = teacherQuery.getDocuments().get(0);
                Teacher t = doc.toObject(Teacher.class);
                if (t == null) {
                    log.error("Failed to map document to Teacher object for ID: {}", doc.getId());
                    throw new RuntimeException("Data mapping error");
                }
                
                // Update verified status and picture if needed
                if (!t.isEmailVerified() || (picture != null && !picture.equals(t.getPicture()))) {
                    doc.getReference().update("emailVerified", true, "picture", picture).get();
                    t.setPicture(picture); // Update object for createResponse
                }
                return createResponse(t.getEmail(), "ROLE_TEACHER", t.getId(), t.getName(), t.getPicture());
            }

            log.debug("Checking parents collection...");
            QuerySnapshot parentQuery = firestore.collection(PARENTS_COLLECTION).whereEqualTo("email", email).limit(1).get().get();
            if (!parentQuery.isEmpty()) {
                log.info("Existing parent found for email: {}", email);
                DocumentSnapshot doc = parentQuery.getDocuments().get(0);
                Parent p = doc.toObject(Parent.class);
                if (p == null) {
                    log.error("Failed to map document to Parent object for ID: {}", doc.getId());
                    throw new RuntimeException("Data mapping error");
                }
                
                // Update verified status and picture if needed
                if (!p.isEmailVerified() || (picture != null && !picture.equals(p.getPicture()))) {
                    doc.getReference().update("emailVerified", true, "picture", picture).get();
                    p.setPicture(picture); // Update object for createResponse
                }
                return createResponse(p.getEmail(), "ROLE_PARENT", p.getId(), p.getName(), p.getPicture());
            }

            // 2. Create new
            log.info("Creating new account for email: {} with requested role: {}", email, requestedRole);
            if ("parent".equalsIgnoreCase(requestedRole)) {
                DocumentReference docRef = firestore.collection(PARENTS_COLLECTION).document();
                Parent p = Parent.builder()
                        .id(docRef.getId())
                        .email(email)
                        .name(name)
                        .password("")
                        .studentId("")
                        .picture(picture)
                        .emailVerified(true)
                        .build();
                docRef.set(p).get();
                log.info("New parent created with ID: {}", p.getId());
                return createResponse(p.getEmail(), "ROLE_PARENT", p.getId(), p.getName(), p.getPicture());
            } else {
                DocumentReference docRef = firestore.collection(TEACHERS_COLLECTION).document();
                Teacher t = Teacher.builder()
                        .id(docRef.getId())
                        .email(email)
                        .name(name)
                        .password("")
                        .school("Pending Selection")
                        .picture(picture)
                        .emailVerified(true)
                        .build();
                docRef.set(t).get();
                log.info("New teacher created with ID: {}", t.getId());
                return createResponse(t.getEmail(), "ROLE_TEACHER", t.getId(), t.getName(), t.getPicture());
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error("Firestore error during Google login: {}", e.getMessage(), e);
            throw new RuntimeException("Internal database error", e);
        } catch (BadCredentialsException e) {
            log.warn("Google authentication failed: {}", e.getMessage());
            throw e;
        } catch (Throwable e) {
            log.error("Unexpected error during Google login: {}", e.getMessage(), e);
            throw new RuntimeException("Google authentication failed due to an unexpected error", e);
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

}
