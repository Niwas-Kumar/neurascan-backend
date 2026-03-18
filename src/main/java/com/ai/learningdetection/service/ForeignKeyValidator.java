package com.ai.learningdetection.service;

import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * ✅ ForeignKeyValidator
 * 
 * Validates that foreign key references exist before creating relationships.
 * Prevents orphan records and data integrity issues.
 * 
 * Usage:
 *   if (request.getParentUid() != null) {
 *       foreignKeyValidator.validateParentExists(request.getParentUid());
 *   }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ForeignKeyValidator {

    private final Firestore firestore;

    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String STUDENTS_COLLECTION = "students";
    private static final String PARENTS_COLLECTION = "parents";
    private static final String CLASSES_COLLECTION = "classes";
    private static final String QUIZZES_COLLECTION = "quizzes";
    private static final String TEST_PAPERS_COLLECTION = "test_papers";

    /**
     * Validate that a teacher exists
     * @param teacherId Teacher document ID
     * @throws ResourceNotFoundException if teacher doesn't exist
     */
    public void validateTeacherExists(String teacherId) throws ExecutionException, InterruptedException {
        if (teacherId == null || teacherId.isBlank()) {
            throw new IllegalArgumentException("Teacher ID cannot be null or empty");
        }
        
        DocumentSnapshot snap = firestore.collection(TEACHERS_COLLECTION)
                .document(teacherId).get().get();
        
        if (!snap.exists()) {
            log.warn("⚠️ FK Violation: Teacher not found: {}", teacherId);
            throw new ResourceNotFoundException("Teacher", "id", teacherId);
        }
        
        log.debug("✓ Teacher FK validation passed: {}", teacherId);
    }

    /**
     * Validate that a student exists
     * @param studentId Student document ID
     * @throws ResourceNotFoundException if student doesn't exist
     */
    public void validateStudentExists(String studentId) throws ExecutionException, InterruptedException {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty");
        }
        
        DocumentSnapshot snap = firestore.collection(STUDENTS_COLLECTION)
                .document(studentId).get().get();
        
        if (!snap.exists()) {
            log.warn("⚠️ FK Violation: Student not found: {}", studentId);
            throw new ResourceNotFoundException("Student", "id", studentId);
        }
        
        log.debug("✓ Student FK validation passed: {}", studentId);
    }

    /**
     * Validate that a parent exists
     * @param parentUid Parent document ID (from Firebase Auth UID)
     * @throws ResourceNotFoundException if parent doesn't exist
     */
    public void validateParentExists(String parentUid) throws ExecutionException, InterruptedException {
        if (parentUid == null || parentUid.isBlank()) {
            throw new IllegalArgumentException("Parent UID cannot be null or empty");
        }
        
        DocumentSnapshot snap = firestore.collection(PARENTS_COLLECTION)
                .document(parentUid).get().get();
        
        if (!snap.exists()) {
            log.warn("⚠️ FK Violation: Parent not found: {}", parentUid);
            throw new ResourceNotFoundException("Parent", "uid", parentUid);
        }
        
        log.debug("✓ Parent FK validation passed: {}", parentUid);
    }

    /**
     * Validate that a class exists
     * @param classId Class document ID
     * @throws ResourceNotFoundException if class doesn't exist
     */
    public void validateClassExists(String classId) throws ExecutionException, InterruptedException {
        if (classId == null || classId.isBlank()) {
            throw new IllegalArgumentException("Class ID cannot be null or empty");
        }
        
        DocumentSnapshot snap = firestore.collection(CLASSES_COLLECTION)
                .document(classId).get().get();
        
        if (!snap.exists()) {
            log.warn("⚠️ FK Violation: Class not found: {}", classId);
            throw new ResourceNotFoundException("Class", "id", classId);
        }
        
        log.debug("✓ Class FK validation passed: {}", classId);
    }

    /**
     * Validate that a quiz exists
     * @param quizId Quiz document ID
     * @throws ResourceNotFoundException if quiz doesn't exist
     */
    public void validateQuizExists(String quizId) throws ExecutionException, InterruptedException {
        if (quizId == null || quizId.isBlank()) {
            throw new IllegalArgumentException("Quiz ID cannot be null or empty");
        }
        
        DocumentSnapshot snap = firestore.collection(QUIZZES_COLLECTION)
                .document(quizId).get().get();
        
        if (!snap.exists()) {
            log.warn("⚠️ FK Violation: Quiz not found: {}", quizId);
            throw new ResourceNotFoundException("Quiz", "id", quizId);
        }
        
        log.debug("✓ Quiz FK validation passed: {}", quizId);
    }

    /**
     * Validate that a test paper exists
     * @param paperId Paper document ID
     * @throws ResourceNotFoundException if paper doesn't exist
     */
    public void validatePaperExists(String paperId) throws ExecutionException, InterruptedException {
        if (paperId == null || paperId.isBlank()) {
            throw new IllegalArgumentException("Paper ID cannot be null or empty");
        }
        
        DocumentSnapshot snap = firestore.collection(TEST_PAPERS_COLLECTION)
                .document(paperId).get().get();
        
        if (!snap.exists()) {
            log.warn("⚠️ FK Violation: Paper not found: {}", paperId);
            throw new ResourceNotFoundException("Paper", "id", paperId);
        }
        
        log.debug("✓ Paper FK validation passed: {}", paperId);
    }

    /**
     * Validate optional foreign key - doesn't throw if null
     * @param value The FK value to validate (can be null)
     * @param collectionName Collection to check in
     * @param entityType Type name for error messages
     * @throws ResourceNotFoundException if FK exists but document doesn't
     */
    public void validateOptionalFK(String value, String collectionName, String entityType) 
            throws ExecutionException, InterruptedException {
        if (value == null || value.isBlank()) {
            return;  // Optional FK - null is OK
        }
        
        DocumentSnapshot snap = firestore.collection(collectionName)
                .document(value).get().get();
        
        if (!snap.exists()) {
            log.warn("⚠️ FK Violation: {} not found: {}", entityType, value);
            throw new ResourceNotFoundException(entityType, "id", value);
        }
        
        log.debug("✓ {} FK validation passed: {}", entityType, value);
    }
}
