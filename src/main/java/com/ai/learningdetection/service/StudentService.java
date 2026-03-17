package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.StudentDTOs;
import com.ai.learningdetection.entity.Student;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.ai.learningdetection.exception.UnauthorizedAccessException;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final Firestore firestore;

    private static final String STUDENTS_COLLECTION = "students";
    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String PAPERS_COLLECTION = "test_papers";

    // -------------------------------------------------------
    // Get all students for a teacher
    // -------------------------------------------------------
    public List<StudentDTOs.StudentResponse> getStudentsByTeacher(String teacherId, String search, String className, String tag, String rollNumber) {
        try {
            Query query = firestore.collection(STUDENTS_COLLECTION)
                    .whereEqualTo("teacherId", teacherId)
                    .whereEqualTo("isActive", true);

            if (className != null && !className.isBlank()) {
                query = query.whereEqualTo("className", className);
            }
            if (rollNumber != null && !rollNumber.isBlank()) {
                query = query.whereEqualTo("rollNumber", rollNumber);
            }

            QuerySnapshot querySnapshot = query.get().get();
            return querySnapshot.getDocuments().stream()
                    .map(doc -> toResponse(doc.toObject(Student.class)))
                    .filter(s -> {
                        if (search == null || search.isBlank()) return true;
                        String lower = search.toLowerCase();
                        return (s.getName() != null && s.getName().toLowerCase().contains(lower))
                                || (s.getRollNumber() != null && s.getRollNumber().toLowerCase().contains(lower))
                                || (s.getClassName() != null && s.getClassName().toLowerCase().contains(lower))
                                || (s.getTags() != null && s.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lower)));
                    })
                    .filter(s -> {
                        if (tag == null || tag.isBlank()) return true;
                        return s.getTags() != null && s.getTags().contains(tag);
                    })
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error fetching students", e);
        }
    }

    // -------------------------------------------------------
    // Create a new student
    // -------------------------------------------------------
    public StudentDTOs.StudentResponse createStudent(StudentDTOs.StudentRequest request, String teacherId) {
        try {
            DocumentSnapshot teacherSnap = firestore.collection(TEACHERS_COLLECTION).document(teacherId).get().get();
            if (!teacherSnap.exists()) {
                throw new ResourceNotFoundException("Teacher", "id", teacherId);
            }

            // Enforce roll number uniqueness within school
            String schoolId = request.getSchoolId();
            if (schoolId == null || schoolId.isBlank()) {
                schoolId = teacherSnap.getString("schoolId");
            }

            if (schoolId == null || schoolId.isBlank()) {
                throw new RuntimeException("Teacher schoolId is not set");
            }

            QuerySnapshot existingRoll = firestore.collection(STUDENTS_COLLECTION)
                    .whereEqualTo("rollNumber", request.getRollNumber())
                    .whereEqualTo("schoolId", schoolId)
                    .get().get();

            if (!existingRoll.isEmpty()) {
                throw new IllegalArgumentException("Roll number already exists in this school");
            }

            DocumentReference docRef = firestore.collection(STUDENTS_COLLECTION).document();
            String now = java.time.Instant.now().toString();
            Student student = Student.builder()
                    .id(docRef.getId())
                    .rollNumber(request.getRollNumber())
                    .name(request.getName())
                    .className(request.getClassName())
                    .section(request.getSection())
                    .age(request.getAge())
                    .dateOfBirth(request.getDateOfBirth())
                    .gender(request.getGender())
                    .schoolId(schoolId)
                    .teacherId(teacherId)
                    .parentUid(request.getParentUid())
                    .profilePhotoUrl(request.getProfilePhotoUrl())
                    .isActive(true)
                    .tags(request.getTags() == null ? java.util.Collections.emptyList() : request.getTags())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            docRef.set(student).get();
            log.info("Created student '{}' (roll {}) in school {} for teacher id={}", student.getName(), student.getRollNumber(), schoolId, teacherId);
            return toResponse(student);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error creating student", e);
        }
    }

    // -------------------------------------------------------
    // Update an existing student
    // -------------------------------------------------------
    public StudentDTOs.StudentResponse updateStudent(String studentId,
                                                     StudentDTOs.StudentRequest request,
                                                     String teacherId) {
        try {
            DocumentReference docRef = firestore.collection(STUDENTS_COLLECTION).document(studentId);
            DocumentSnapshot snap = docRef.get().get();

            if (!snap.exists() || !teacherId.equals(snap.getString("teacherId"))) {
                throw new ResourceNotFoundException("Student", "id", studentId);
            }

            Student student = snap.toObject(Student.class);
            student.setRollNumber(request.getRollNumber());
            student.setName(request.getName());
            student.setClassName(request.getClassName());
            student.setSection(request.getSection());
            student.setAge(request.getAge());
            student.setDateOfBirth(request.getDateOfBirth());
            student.setGender(request.getGender());
            student.setProfilePhotoUrl(request.getProfilePhotoUrl());
            student.setParentUid(request.getParentUid());
            student.setTags(request.getTags() == null ? java.util.Collections.emptyList() : request.getTags());
            student.setUpdatedAt(java.time.Instant.now().toString());

            docRef.set(student).get();
            log.info("Updated student id={} in Firestore", studentId);
            return toResponse(student);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error updating student", e);
        }
    }

    // -------------------------------------------------------
    // Delete a student
    // -------------------------------------------------------
    public void deleteStudent(String studentId, String teacherId) {
        try {
            DocumentReference docRef = firestore.collection(STUDENTS_COLLECTION).document(studentId);
            DocumentSnapshot snap = docRef.get().get();

            if (!snap.exists() || !teacherId.equals(snap.getString("teacherId"))) {
                throw new ResourceNotFoundException("Student", "id", studentId);
            }

            docRef.delete().get();
            log.info("Deleted student id={} from Firestore", studentId);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error deleting student", e);
        }
    }

    // -------------------------------------------------------
    // Get a single student
    // -------------------------------------------------------
    public StudentDTOs.StudentResponse getStudentById(String studentId, String teacherId) {
        try {
            DocumentSnapshot snap = firestore.collection(STUDENTS_COLLECTION).document(studentId).get().get();
            if (!snap.exists() || !teacherId.equals(snap.getString("teacherId"))) {
                throw new ResourceNotFoundException("Student", "id", studentId);
            }
            return toResponse(snap.toObject(Student.class));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error fetching student", e);
        }
    }

    // -------------------------------------------------------
    // Verify ownership
    // -------------------------------------------------------
    public void verifyStudentBelongsToTeacher(String studentId, String teacherId) {
        try {
            DocumentSnapshot snap = firestore.collection(STUDENTS_COLLECTION).document(studentId).get().get();
            if (!snap.exists() || !teacherId.equals(snap.getString("teacherId"))) {
                throw new UnauthorizedAccessException("Student does not belong to this teacher");
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error verifying ownership", e);
        }
    }

    // -------------------------------------------------------
    // Mapper
    // -------------------------------------------------------
    private StudentDTOs.StudentResponse toResponse(Student s) {
        try {
            DocumentSnapshot teacherSnap = firestore.collection(TEACHERS_COLLECTION).document(s.getTeacherId()).get().get();
            String teacherName = teacherSnap.exists() ? teacherSnap.getString("name") : "Unknown";

            QuerySnapshot papers = firestore.collection(PAPERS_COLLECTION)
                    .whereEqualTo("studentId", s.getId())
                    .get().get();

            // For future: optimize this by batching paper counts or caching
            // For now, removing redundant logging to slightly improve performance
            return StudentDTOs.StudentResponse.builder()
                    .id(s.getId())
                    .rollNumber(s.getRollNumber())
                    .name(s.getName())
                    .className(s.getClassName())
                    .section(s.getSection())
                    .age(s.getAge())
                    .dateOfBirth(s.getDateOfBirth())
                    .gender(s.getGender())
                    .schoolId(s.getSchoolId())
                    .teacherId(s.getTeacherId())
                    .teacherName(teacherName)
                    .parentUid(s.getParentUid())
                    .profilePhotoUrl(s.getProfilePhotoUrl())
                    .isActive(s.isActive())
                    .tags(s.getTags())
                    .createdAt(s.getCreatedAt())
                    .updatedAt(s.getUpdatedAt())
                    .totalPapers(papers.size())
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error building student response: {}", e.getMessage());
            return StudentDTOs.StudentResponse.builder().id(s.getId()).name(s.getName()).build();
        }
    }
}

