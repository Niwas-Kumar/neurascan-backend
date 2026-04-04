package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.ParentStudentDTOs.*;
import com.ai.learningdetection.entity.ParentStudentRelationship;
import com.ai.learningdetection.entity.Student;
import com.ai.learningdetection.entity.Teacher;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing parent-student relationships.
 * Provides persistent storage of connections in Firestore.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParentStudentService {

    private final Firestore firestore;
    private final OTPService otpService;
    private final EmailService emailService;

    private static final String RELATIONSHIPS_COLLECTION = "parent_student_relationships";
    private static final String STUDENTS_COLLECTION = "students";
    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String PARENTS_COLLECTION = "parents";
    private static final int MAX_CONNECTIONS_PER_PARENT = 5;
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;

    // ════════════════════════════════════════════════════════════════
    // VALIDATE STUDENT
    // ════════════════════════════════════════════════════════════════

    /**
     * Validate a student ID before initiating connection.
     */
    public ValidateStudentResponse validateStudent(String studentId, String parentId) {
        try {
            // Check if student exists
            DocumentSnapshot studentSnap = firestore.collection(STUDENTS_COLLECTION)
                    .document(studentId).get().get();

            if (!studentSnap.exists()) {
                return ValidateStudentResponse.builder()
                        .valid(false)
                        .message("Student ID not found. Please check the ID and try again.")
                        .build();
            }

            Student student = studentSnap.toObject(Student.class);

            // Check if already connected
            boolean alreadyConnected = isAlreadyConnected(parentId, studentId);
            if (alreadyConnected) {
                return ValidateStudentResponse.builder()
                        .valid(false)
                        .studentId(studentId)
                        .studentName(student.getName())
                        .className(student.getClassName())
                        .alreadyConnected(true)
                        .message("You are already connected to this student.")
                        .build();
            }

            // Get teacher name
            String teacherName = "Unknown";
            if (student.getTeacherId() != null) {
                DocumentSnapshot teacherSnap = firestore.collection(TEACHERS_COLLECTION)
                        .document(student.getTeacherId()).get().get();
                if (teacherSnap.exists()) {
                    Teacher teacher = teacherSnap.toObject(Teacher.class);
                    teacherName = teacher.getName();
                }
            }

            return ValidateStudentResponse.builder()
                    .valid(true)
                    .studentId(studentId)
                    .studentName(student.getName())
                    .className(student.getClassName())
                    .teacherName(teacherName)
                    .schoolName(student.getSchoolId())
                    .alreadyConnected(false)
                    .message("Student found. Ready to connect.")
                    .build();

        } catch (Exception e) {
            log.error("Error validating student {}: {}", studentId, e.getMessage());
            return ValidateStudentResponse.builder()
                    .valid(false)
                    .message("Error validating student. Please try again.")
                    .build();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // INITIATE CONNECTION
    // ════════════════════════════════════════════════════════════════

    /**
     * Initiate a connection request with a student.
     */
    public ConnectionInitiatedResponse initiateConnection(
            String parentId,
            String parentEmail,
            ConnectStudentRequest request,
            String ipAddress
    ) {
        try {
            String studentId = request.getStudentId();

            // Validate student exists
            ValidateStudentResponse validation = validateStudent(studentId, parentId);
            if (!validation.isValid()) {
                throw new IllegalArgumentException(validation.getMessage());
            }

            // Check connection limit
            long activeConnections = getActiveConnectionCount(parentId);
            if (activeConnections >= MAX_CONNECTIONS_PER_PARENT) {
                throw new IllegalArgumentException(
                    "Maximum connection limit reached. Please disconnect from a student before adding a new one."
                );
            }

            // Create relationship record
            DocumentReference docRef = firestore.collection(RELATIONSHIPS_COLLECTION).document();
            String now = Instant.now().toString();

            ParentStudentRelationship relationship = ParentStudentRelationship.builder()
                    .id(docRef.getId())
                    .parentId(parentId)
                    .studentId(studentId)
                    .verificationStatus("PENDING")
                    .createdAt(now)
                    .createdFromIp(ipAddress)
                    .verificationAttempts(0)
                    .isPrimary(activeConnections == 0) // First connection is primary
                    .studentName(validation.getStudentName())
                    .studentClassName(validation.getClassName())
                    .studentRollNumber(studentId)
                    .build();

            docRef.set(relationship).get();
            log.info("Created pending relationship {} for parent {} -> student {}",
                    relationship.getId(), parentId, studentId);

            // Generate and send OTP
            String otp = otpService.generateOTP(parentEmail);

            // Send verification email
            try {
                emailService.sendStudentConnectionOTP(
                        parentEmail,
                        otp,
                        validation.getStudentName(),
                        validation.getClassName()
                );
            } catch (Exception e) {
                log.warn("Failed to send OTP email: {}", e.getMessage());
                // Continue - OTP is generated, user can request resend
            }

            return ConnectionInitiatedResponse.builder()
                    .relationshipId(relationship.getId())
                    .studentId(studentId)
                    .studentName(validation.getStudentName())
                    .verificationMethod("OTP")
                    .requiresVerification(true)
                    .message("Verification code sent to your email. Please enter the code to complete the connection.")
                    .build();

        } catch (Exception e) {
            log.error("Error initiating connection: {}", e.getMessage());
            throw new RuntimeException("Failed to initiate connection: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // VERIFY CONNECTION
    // ════════════════════════════════════════════════════════════════

    /**
     * Verify a pending connection with OTP.
     */
    public ConnectionResultResponse verifyConnection(
            String parentId,
            String parentEmail,
            VerifyConnectionRequest request
    ) {
        try {
            // Get relationship
            DocumentSnapshot relSnap = firestore.collection(RELATIONSHIPS_COLLECTION)
                    .document(request.getRelationshipId()).get().get();

            if (!relSnap.exists()) {
                return ConnectionResultResponse.builder()
                        .success(false)
                        .message("Connection request not found or has expired.")
                        .build();
            }

            ParentStudentRelationship relationship = relSnap.toObject(ParentStudentRelationship.class);

            // Verify ownership
            if (!relationship.getParentId().equals(parentId)) {
                return ConnectionResultResponse.builder()
                        .success(false)
                        .message("Unauthorized access to this connection request.")
                        .build();
            }

            // Check if already verified
            if ("VERIFIED".equals(relationship.getVerificationStatus())) {
                return ConnectionResultResponse.builder()
                        .success(true)
                        .message("This connection is already verified.")
                        .student(mapToConnectedStudent(relationship))
                        .build();
            }

            // Check attempt limit
            if (relationship.getVerificationAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
                // Mark as rejected
                firestore.collection(RELATIONSHIPS_COLLECTION).document(request.getRelationshipId())
                        .update("verificationStatus", "REJECTED").get();

                return ConnectionResultResponse.builder()
                        .success(false)
                        .message("Too many failed attempts. Please start a new connection request.")
                        .build();
            }

            // Verify OTP
            boolean otpValid = otpService.verifyOTP(parentEmail, request.getOtp());

            if (!otpValid) {
                // Increment attempt counter
                firestore.collection(RELATIONSHIPS_COLLECTION).document(request.getRelationshipId())
                        .update("verificationAttempts", relationship.getVerificationAttempts() + 1).get();

                int attemptsLeft = MAX_VERIFICATION_ATTEMPTS - relationship.getVerificationAttempts() - 1;
                return ConnectionResultResponse.builder()
                        .success(false)
                        .message("Invalid verification code. " + attemptsLeft + " attempts remaining.")
                        .build();
            }

            // OTP valid - mark as verified
            String now = Instant.now().toString();
            Map<String, Object> updates = new HashMap<>();
            updates.put("verificationStatus", "VERIFIED");
            updates.put("verifiedAt", now);

            firestore.collection(RELATIONSHIPS_COLLECTION).document(request.getRelationshipId())
                    .update(updates).get();

            // Consume OTP
            otpService.consumeOTP(parentEmail, request.getOtp());

            // Update relationship object for response
            relationship.setVerificationStatus("VERIFIED");
            relationship.setVerifiedAt(now);

            // Also update student's parentUid for quiz distribution
            updateStudentParentLink(relationship.getStudentId(), parentId);

            log.info("Verified connection {} for parent {} -> student {}",
                    request.getRelationshipId(), parentId, relationship.getStudentId());

            return ConnectionResultResponse.builder()
                    .success(true)
                    .message("Successfully connected to " + relationship.getStudentName() + "!")
                    .student(mapToConnectedStudent(relationship))
                    .build();

        } catch (Exception e) {
            log.error("Error verifying connection: {}", e.getMessage());
            return ConnectionResultResponse.builder()
                    .success(false)
                    .message("Verification failed. Please try again.")
                    .build();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // GET CONNECTED STUDENTS
    // ════════════════════════════════════════════════════════════════

    /**
     * Get all connected students for a parent.
     */
    public ConnectedStudentsListResponse getConnectedStudents(String parentId) {
        try {
            QuerySnapshot query = firestore.collection(RELATIONSHIPS_COLLECTION)
                    .whereEqualTo("parentId", parentId)
                    .get().get();

            List<ConnectedStudentResponse> students = new ArrayList<>();
            int activeCount = 0;
            int pendingCount = 0;

            for (DocumentSnapshot doc : query.getDocuments()) {
                ParentStudentRelationship rel = doc.toObject(ParentStudentRelationship.class);

                // Skip null or disconnected relationships
                if (rel == null || rel.getDisconnectedAt() != null) continue;

                ConnectedStudentResponse student = mapToConnectedStudent(rel);
                students.add(student);

                if ("VERIFIED".equals(rel.getVerificationStatus())) {
                    activeCount++;
                } else if ("PENDING".equals(rel.getVerificationStatus())) {
                    pendingCount++;
                }
            }

            // Sort: primary first, then by connection date (null-safe)
            students.sort((a, b) -> {
                if (a.isPrimary() != b.isPrimary()) return a.isPrimary() ? -1 : 1;
                String aTime = a.getConnectedAt();
                String bTime = b.getConnectedAt();
                if (bTime == null && aTime == null) return 0;
                if (bTime == null) return -1;
                if (aTime == null) return 1;
                return bTime.compareTo(aTime);
            });

            return ConnectedStudentsListResponse.builder()
                    .students(students)
                    .totalConnections(students.size())
                    .activeConnections(activeCount)
                    .pendingConnections(pendingCount)
                    .build();

        } catch (Exception e) {
            log.error("Error getting connected students for parent {}: {}", parentId, e.getMessage());
            throw new RuntimeException("Failed to load connected students");
        }
    }

    /**
     * Get the primary connected student for a parent.
     * Returns null if no verified connections exist.
     */
    public ConnectedStudentResponse getPrimaryStudent(String parentId) {
        try {
            // First try to find explicit primary
            QuerySnapshot query = firestore.collection(RELATIONSHIPS_COLLECTION)
                    .whereEqualTo("parentId", parentId)
                    .whereEqualTo("verificationStatus", "VERIFIED")
                    .whereEqualTo("isPrimary", true)
                    .limit(1)
                    .get().get();

            if (!query.isEmpty()) {
                ParentStudentRelationship rel = query.getDocuments().get(0)
                        .toObject(ParentStudentRelationship.class);
                if (rel.getDisconnectedAt() == null) {
                    return mapToConnectedStudent(rel);
                }
            }

            // Fallback to first verified connection
            query = firestore.collection(RELATIONSHIPS_COLLECTION)
                    .whereEqualTo("parentId", parentId)
                    .whereEqualTo("verificationStatus", "VERIFIED")
                    .limit(1)
                    .get().get();

            if (!query.isEmpty()) {
                ParentStudentRelationship rel = query.getDocuments().get(0)
                        .toObject(ParentStudentRelationship.class);
                if (rel.getDisconnectedAt() == null) {
                    return mapToConnectedStudent(rel);
                }
            }

            return null;

        } catch (Exception e) {
            log.error("Error getting primary student for parent {}: {}", parentId, e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════
    // DISCONNECT
    // ════════════════════════════════════════════════════════════════

    /**
     * Disconnect from a student (soft delete).
     */
    public ConnectionResultResponse disconnectStudent(String parentId, DisconnectRequest request) {
        try {
            // Find the relationship
            QuerySnapshot query = firestore.collection(RELATIONSHIPS_COLLECTION)
                    .whereEqualTo("parentId", parentId)
                    .whereEqualTo("studentId", request.getStudentId())
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                return ConnectionResultResponse.builder()
                        .success(false)
                        .message("Connection not found.")
                        .build();
            }

            DocumentSnapshot doc = query.getDocuments().get(0);
            ParentStudentRelationship rel = doc.toObject(ParentStudentRelationship.class);

            // Soft delete
            Map<String, Object> updates = new HashMap<>();
            updates.put("disconnectedAt", Instant.now().toString());
            updates.put("disconnectedReason", request.getReason() != null ? request.getReason() : "User requested");

            doc.getReference().update(updates).get();

            // If this was the primary student, set another as primary
            if (rel.isPrimary()) {
                promotNextPrimaryStudent(parentId);
            }

            log.info("Disconnected parent {} from student {}", parentId, request.getStudentId());

            return ConnectionResultResponse.builder()
                    .success(true)
                    .message("Successfully disconnected from " + rel.getStudentName())
                    .build();

        } catch (Exception e) {
            log.error("Error disconnecting: {}", e.getMessage());
            return ConnectionResultResponse.builder()
                    .success(false)
                    .message("Failed to disconnect. Please try again.")
                    .build();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SET PRIMARY STUDENT
    // ════════════════════════════════════════════════════════════════

    /**
     * Set a connected student as the primary student.
     */
    public ConnectionResultResponse setPrimaryStudent(String parentId, SetPrimaryStudentRequest request) {
        try {
            // Get all connections for parent
            QuerySnapshot query = firestore.collection(RELATIONSHIPS_COLLECTION)
                    .whereEqualTo("parentId", parentId)
                    .get().get();

            boolean found = false;
            WriteBatch batch = firestore.batch();

            for (DocumentSnapshot doc : query.getDocuments()) {
                ParentStudentRelationship rel = doc.toObject(ParentStudentRelationship.class);
                if (rel.getDisconnectedAt() != null) continue;

                boolean shouldBePrimary = rel.getStudentId().equals(request.getStudentId());

                if (shouldBePrimary) {
                    if (!"VERIFIED".equals(rel.getVerificationStatus())) {
                        return ConnectionResultResponse.builder()
                                .success(false)
                                .message("Cannot set pending connection as primary.")
                                .build();
                    }
                    found = true;
                }

                if (rel.isPrimary() != shouldBePrimary) {
                    batch.update(doc.getReference(), "isPrimary", shouldBePrimary);
                }
            }

            if (!found) {
                return ConnectionResultResponse.builder()
                        .success(false)
                        .message("Student connection not found.")
                        .build();
            }

            batch.commit().get();

            log.info("Set student {} as primary for parent {}", request.getStudentId(), parentId);

            return ConnectionResultResponse.builder()
                    .success(true)
                    .message("Primary student updated successfully.")
                    .build();

        } catch (Exception e) {
            log.error("Error setting primary student: {}", e.getMessage());
            return ConnectionResultResponse.builder()
                    .success(false)
                    .message("Failed to update primary student.")
                    .build();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // RESEND OTP
    // ════════════════════════════════════════════════════════════════

    /**
     * Resend verification OTP for a pending connection.
     */
    public ConnectionInitiatedResponse resendVerificationOTP(
            String parentId,
            String parentEmail,
            String relationshipId
    ) {
        try {
            DocumentSnapshot relSnap = firestore.collection(RELATIONSHIPS_COLLECTION)
                    .document(relationshipId).get().get();

            if (!relSnap.exists()) {
                throw new ResourceNotFoundException("Connection request not found");
            }

            ParentStudentRelationship rel = relSnap.toObject(ParentStudentRelationship.class);

            if (!rel.getParentId().equals(parentId)) {
                throw new IllegalArgumentException("Unauthorized");
            }

            if (!"PENDING".equals(rel.getVerificationStatus())) {
                throw new IllegalArgumentException("Connection is not pending verification");
            }

            // Generate new OTP
            String otp = otpService.generateOTP(parentEmail);

            // Send email
            emailService.sendStudentConnectionOTP(
                    parentEmail,
                    otp,
                    rel.getStudentName(),
                    rel.getStudentClassName()
            );

            return ConnectionInitiatedResponse.builder()
                    .relationshipId(relationshipId)
                    .studentId(rel.getStudentId())
                    .studentName(rel.getStudentName())
                    .verificationMethod("OTP")
                    .requiresVerification(true)
                    .message("New verification code sent to your email.")
                    .build();

        } catch (Exception e) {
            log.error("Error resending OTP: {}", e.getMessage());
            throw new RuntimeException("Failed to resend verification code");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════

    private boolean isAlreadyConnected(String parentId, String studentId)
            throws ExecutionException, InterruptedException {
        QuerySnapshot query = firestore.collection(RELATIONSHIPS_COLLECTION)
                .whereEqualTo("parentId", parentId)
                .whereEqualTo("studentId", studentId)
                .limit(1)
                .get().get();

        if (query.isEmpty()) return false;

        ParentStudentRelationship rel = query.getDocuments().get(0)
                .toObject(ParentStudentRelationship.class);

        // Connected if not disconnected and not rejected
        return rel.getDisconnectedAt() == null && !"REJECTED".equals(rel.getVerificationStatus());
    }

    private long getActiveConnectionCount(String parentId) throws ExecutionException, InterruptedException {
        QuerySnapshot query = firestore.collection(RELATIONSHIPS_COLLECTION)
                .whereEqualTo("parentId", parentId)
                .whereEqualTo("verificationStatus", "VERIFIED")
                .get().get();

        return query.getDocuments().stream()
                .map(d -> d.toObject(ParentStudentRelationship.class))
                .filter(r -> r.getDisconnectedAt() == null)
                .count();
    }

    private ConnectedStudentResponse mapToConnectedStudent(ParentStudentRelationship rel) {
        return ConnectedStudentResponse.builder()
                .relationshipId(rel.getId())
                .studentId(rel.getStudentId())
                .studentName(rel.getStudentName())
                .studentClassName(rel.getStudentClassName())
                .studentRollNumber(rel.getStudentRollNumber())
                .verificationStatus(rel.getVerificationStatus())
                .connectedAt(rel.getVerifiedAt() != null ? rel.getVerifiedAt() : rel.getCreatedAt())
                .isPrimary(rel.isPrimary())
                .canAccessData("VERIFIED".equals(rel.getVerificationStatus()))
                .build();
    }

    private void promotNextPrimaryStudent(String parentId) {
        try {
            QuerySnapshot query = firestore.collection(RELATIONSHIPS_COLLECTION)
                    .whereEqualTo("parentId", parentId)
                    .whereEqualTo("verificationStatus", "VERIFIED")
                    .limit(1)
                    .get().get();

            for (DocumentSnapshot doc : query.getDocuments()) {
                ParentStudentRelationship rel = doc.toObject(ParentStudentRelationship.class);
                if (rel.getDisconnectedAt() == null) {
                    doc.getReference().update("isPrimary", true).get();
                    log.info("Promoted student {} as new primary for parent {}",
                            rel.getStudentId(), parentId);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Error promoting primary student: {}", e.getMessage());
        }
    }

    private void updateStudentParentLink(String studentId, String parentId) {
        try {
            firestore.collection(STUDENTS_COLLECTION).document(studentId)
                    .update("parentUid", parentId).get();
            log.info("Updated student {} with parentUid {}", studentId, parentId);
        } catch (Exception e) {
            log.warn("Failed to update student parentUid: {}", e.getMessage());
        }
    }
}
