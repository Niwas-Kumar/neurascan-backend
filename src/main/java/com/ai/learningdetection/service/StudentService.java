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
    public List<StudentDTOs.StudentResponse> getStudentsByTeacher(String teacherId) {
        try {
            QuerySnapshot query = firestore.collection(STUDENTS_COLLECTION)
                    .whereEqualTo("teacherId", teacherId)
                    .get().get();

            return query.getDocuments().stream()
                    .map(doc -> toResponse(doc.toObject(Student.class)))
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

            DocumentReference docRef = firestore.collection(STUDENTS_COLLECTION).document();
            Student student = Student.builder()
                    .id(docRef.getId())
                    .name(request.getName())
                    .className(request.getClassName())
                    .age(request.getAge())
                    .teacherId(teacherId)
                    .build();

            docRef.set(student).get();
            log.info("Created student '{}' in Firestore for teacher id={}", student.getName(), teacherId);
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
            student.setName(request.getName());
            student.setClassName(request.getClassName());
            student.setAge(request.getAge());

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

            return StudentDTOs.StudentResponse.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .className(s.getClassName())
                    .age(s.getAge())
                    .teacherId(s.getTeacherId())
                    .teacherName(teacherName)
                    .totalPapers(papers.size())
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error building student response: {}", e.getMessage());
            return StudentDTOs.StudentResponse.builder().id(s.getId()).name(s.getName()).build();
        }
    }
}

