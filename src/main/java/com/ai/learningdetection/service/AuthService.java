package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.AuthDTOs;
import com.ai.learningdetection.entity.Parent;
import com.ai.learningdetection.entity.School;
import com.ai.learningdetection.entity.Teacher;
import com.ai.learningdetection.exception.EmailAlreadyExistsException;
import com.ai.learningdetection.util.JwtUtil;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final Firestore firestore;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OTPService otpService;
    private final SchoolService schoolService;

    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String PARENTS_COLLECTION = "parents";

    // ============================================================
    // TEACHER AUTH
    // ============================================================

    public AuthDTOs.AuthResponse registerTeacher(AuthDTOs.TeacherRegisterRequest request) {
        try {
            log.info("📝 Registering new teacher: {}", request.getEmail());
            
            if (emailExists(request.getEmail())) {
                log.warn("❌ Registration failed - email already exists: {}", request.getEmail());
                throw new EmailAlreadyExistsException(request.getEmail());
            }

            // Verify OTP before creating account
            log.info("🔐 Verifying OTP for teacher registration: {}", request.getEmail());
            if (!otpService.verifyOTP(request.getEmail(), request.getOtp())) {
                log.error("❌ OTP verification failed for teacher registration: {}", request.getEmail());
                throw new BadCredentialsException("Invalid or expired OTP verification code. Please request a new code.");
            }
            otpService.consumeOTP(request.getEmail(), request.getOtp());

            // Validate school code if provided
            String verificationStatus = "PENDING";
            String schoolId = null;
            String schoolName = request.getSchool();
            String schoolCode = request.getSchoolCode();

            if (schoolCode != null && !schoolCode.trim().isEmpty()) {
                School school = schoolService.validateSchoolCode(schoolCode.trim());
                if (school != null) {
                    schoolId = school.getId();
                    schoolName = school.getName();
                    verificationStatus = "APPROVED";
                    log.info("✓ Valid school code provided. Teacher auto-approved for school: {} (id={})", school.getName(), school.getId());
                } else {
                    log.warn("⚠️ Invalid school code provided: {}", schoolCode);
                    // Still allow registration but with PENDING status
                }
            }

            DocumentReference docRef = firestore.collection(TEACHERS_COLLECTION).document();
            Teacher teacher = Teacher.builder()
                    .id(docRef.getId())
                    .name(request.getName())
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .schoolId(schoolId)
                    .emailVerified(true)
                    .verificationStatus(verificationStatus)
                    .createdAt(Instant.now().toString())
                    .updatedAt(Instant.now().toString())
                    .build();

            docRef.set(teacher).get();
            log.info("✓ Registered teacher: {} ({}) — status: {}", teacher.getName(), teacher.getEmail(), verificationStatus);

            String message = verificationStatus.equals("APPROVED")
                    ? "Registration successful! Your account is verified."
                    : "Registration successful! Your account is pending admin approval.";

            return AuthDTOs.AuthResponse.builder()
                    .jwtToken(null)
                    .userRole("ROLE_TEACHER")
                    .userId(teacher.getId())
                    .userName(teacher.getName())
                    .picture(teacher.getPicture())
                    .message(message)
                    .schoolId(schoolId)
                    .schoolName(schoolName)
                    .build();
        } catch (EmailAlreadyExistsException | BadCredentialsException e) {
            throw e;
        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Firestore error during teacher registration: {}", e.getMessage(), e);
            throw new RuntimeException("Firestore error during registration", e);
        }
    }

    public AuthDTOs.AuthResponse loginTeacher(AuthDTOs.LoginRequest request) {
        try {
            QuerySnapshot query = firestore.collection(TEACHERS_COLLECTION)
                    .whereEqualTo("email", request.getEmail())
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                if (existsInCollection(PARENTS_COLLECTION, request.getEmail())) {
                    throw new BadCredentialsException("This email is registered as a Parent. Please switch to the Parent tab to log in.");
                }
                throw new BadCredentialsException("Invalid email or password.");
            }

            Teacher teacher = query.getDocuments().get(0).toObject(Teacher.class);

            if (!passwordEncoder.matches(request.getPassword(), teacher.getPassword())) {
                throw new BadCredentialsException("Invalid email or password.");
            }

            // Check teacher verification status
            String status = teacher.getVerificationStatus() != null ? teacher.getVerificationStatus() : "APPROVED";
            if ("REJECTED".equals(status)) {
                throw new BadCredentialsException("Your teacher account has been rejected. Please contact support.");
            }

            String token = jwtUtil.generateToken(teacher.getEmail(), "ROLE_TEACHER", teacher.getId(), teacher.getName(), teacher.getPicture(), teacher.getSchoolId());
            log.info("Teacher logged in from Firestore: {} (status: {})", teacher.getEmail(), status);

            String message = "PENDING".equals(status)
                    ? "ACCOUNT_PENDING_APPROVAL"
                    : "Login successful";

            return AuthDTOs.AuthResponse.builder()
                    .jwtToken(token)
                    .userRole("ROLE_TEACHER")
                    .userId(teacher.getId())
                    .userName(teacher.getName())
                    .userEmail(teacher.getEmail())
                    .picture(teacher.getPicture())
                    .message(message)
                    .schoolId(teacher.getSchoolId())
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during login", e);
        }
    }

    // ============================================================
    // PARENT AUTH
    // ============================================================

    public AuthDTOs.AuthResponse registerParent(AuthDTOs.ParentRegisterRequest request) {
        try {
            log.info("📝 Registering new parent: {}", request.getEmail());

            if (emailExists(request.getEmail())) {
                log.warn("❌ Registration failed - email already exists: {}", request.getEmail());
                throw new EmailAlreadyExistsException(request.getEmail());
            }

            // Verify OTP before creating account
            log.info("🔐 Verifying OTP for parent registration: {}", request.getEmail());
            if (!otpService.verifyOTP(request.getEmail(), request.getOtp())) {
                log.error("❌ OTP verification failed for parent registration: {}", request.getEmail());
                throw new BadCredentialsException("Invalid or expired OTP verification code. Please request a new code.");
            }
            otpService.consumeOTP(request.getEmail(), request.getOtp());

            DocumentReference docRef = firestore.collection(PARENTS_COLLECTION).document();
            Parent parent = Parent.builder()
                    .id(docRef.getId())
                    .name(request.getName())
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .emailVerified(true)
                    .build();

            docRef.set(parent).get();
            log.info("✓ Successfully registered new parent in Firestore: {} ({})", parent.getName(), parent.getEmail());

            if (request.getStudentId() != null && !request.getStudentId().isEmpty()) {
                log.info("Parent {} provided studentId at registration; skipping direct linkage until verified connection is completed", parent.getEmail());
            }

            return AuthDTOs.AuthResponse.builder()
                    .jwtToken(null) // Can generate token if auto-login is desired
                    .userRole("ROLE_PARENT")
                    .userId(parent.getId())
                    .userName(parent.getName())
                    .userEmail(parent.getEmail())
                    .picture(parent.getPicture())
                    .message("Registration successful!")
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during registration", e);
        }
    }

    public AuthDTOs.AuthResponse loginParent(AuthDTOs.LoginRequest request) {
        try {
            QuerySnapshot query = firestore.collection(PARENTS_COLLECTION)
                    .whereEqualTo("email", request.getEmail())
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                if (existsInCollection(TEACHERS_COLLECTION, request.getEmail())) {
                    throw new BadCredentialsException("This email is registered as a Teacher. Please switch to the Teacher tab to log in.");
                }
                throw new BadCredentialsException("Invalid email or password.");
            }

            Parent parent = query.getDocuments().get(0).toObject(Parent.class);

            if (!passwordEncoder.matches(request.getPassword(), parent.getPassword())) {
                throw new BadCredentialsException("Invalid email or password.");
            }

            String token = jwtUtil.generateToken(parent.getEmail(), "ROLE_PARENT", parent.getId(), parent.getName(), parent.getPicture());
            log.info("Parent logged in from Firestore: {}", parent.getEmail());

            return AuthDTOs.AuthResponse.builder()
                    .jwtToken(token)
                    .userRole("ROLE_PARENT")
                    .userId(parent.getId())
                    .userName(parent.getName())
                    .userEmail(parent.getEmail())
                    .picture(parent.getPicture())
                    .message("Login successful")
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during login", e);
        }
    }

    // Helper methods
    private boolean emailExists(String email) throws InterruptedException, ExecutionException {
        return existsInCollection(TEACHERS_COLLECTION, email) || existsInCollection(PARENTS_COLLECTION, email);
    }

    private boolean existsInCollection(String collection, String email) throws InterruptedException, ExecutionException {
        return !firestore.collection(collection).whereEqualTo("email", email).limit(1).get().get().isEmpty();
    }

}

