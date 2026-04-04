package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.ClassDTOs;
import com.ai.learningdetection.entity.ClassRoom;
import com.ai.learningdetection.entity.Student;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassService {

    private final Firestore firestore;

    private static final String CLASSES_COLLECTION = "classes";
    private static final String STUDENTS_COLLECTION = "students";

    public ClassDTOs.ClassResponse createClass(ClassDTOs.ClassCreateRequest request) {
        try {
            String normalizedClassName = normalizeClassName(request.getClassName());
            if (normalizedClassName == null) {
                throw new IllegalArgumentException("Class name is required");
            }

            QuerySnapshot existingClasses = firestore.collection(CLASSES_COLLECTION)
                    .whereEqualTo("teacherId", request.getTeacherId())
                    .get().get();

            boolean duplicateExists = existingClasses.getDocuments().stream()
                    .map(doc -> doc.toObject(ClassRoom.class))
                    .filter(c -> c != null && c.isActive())
                    .anyMatch(c -> normalizedClassName.equalsIgnoreCase(String.valueOf(c.getClassName()).trim()));

            if (duplicateExists) {
                throw new IllegalArgumentException("Class already exists: " + normalizedClassName);
            }

            DocumentReference docRef = firestore.collection(CLASSES_COLLECTION).document();
            String now = java.time.Instant.now().toString();

            ClassRoom classRoom = ClassRoom.builder()
                    .id(docRef.getId())
                    .className(normalizedClassName)
                    .section(request.getSection())
                    .academicYear(request.getAcademicYear())
                    .subject(request.getSubject())
                    .schoolId(request.getSchoolId())
                    .teacherId(request.getTeacherId())
                    .studentIds(request.getStudentIds() == null ? new ArrayList<>() : request.getStudentIds())
                    .active(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            docRef.set(classRoom).get();
            log.info("Created class {} for teacher {}", classRoom.getClassName(), classRoom.getTeacherId());
            return toResponse(classRoom);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error creating class", e);
        }
    }

    public ClassDTOs.ClassResponse getClassById(String classId, String teacherId) {
        try {
            DocumentSnapshot snap = firestore.collection(CLASSES_COLLECTION).document(classId).get().get();
            if (!snap.exists()) {
                throw new ResourceNotFoundException("Class", "id", classId);
            }
            ClassRoom classRoom = snap.toObject(ClassRoom.class);
            if (classRoom == null || !teacherId.equals(classRoom.getTeacherId())) {
                throw new ResourceNotFoundException("Class", "id", classId);
            }
            return toResponse(classRoom);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error fetching class", e);
        }
    }

    public List<ClassDTOs.ClassResponse> getClassesByTeacher(String teacherId) {
        try {
            // Build class summaries from active students to guarantee accurate counts.
            QuerySnapshot studentQuery = firestore.collection(STUDENTS_COLLECTION)
                    .whereEqualTo("teacherId", teacherId)
                    .get().get();

            Map<String, List<String>> classToStudentIds = new HashMap<>();
            Map<String, String> classToSchoolId = new HashMap<>();

            for (DocumentSnapshot doc : studentQuery.getDocuments()) {
                Student student = doc.toObject(Student.class);
                if (student == null || !student.isActive()) {
                    continue;
                }

                String className = normalizeClassName(student.getClassName());
                if (className == null) {
                    continue;
                }

                classToStudentIds
                        .computeIfAbsent(className, key -> new ArrayList<>())
                        .add(student.getId());

                if (!classToSchoolId.containsKey(className)) {
                    classToSchoolId.put(className, student.getSchoolId());
                }
            }

            QuerySnapshot classQuery = firestore.collection(CLASSES_COLLECTION)
                    .whereEqualTo("teacherId", teacherId)
                    .get().get();

            Map<String, ClassDTOs.ClassResponse> classSummaries = new HashMap<>();

            for (DocumentSnapshot doc : classQuery.getDocuments()) {
                ClassRoom classRoom = doc.toObject(ClassRoom.class);
                if (classRoom == null || !classRoom.isActive()) {
                    continue;
                }

                String className = normalizeClassName(classRoom.getClassName());
                if (className == null) {
                    continue;
                }

                List<String> studentIds = classToStudentIds.getOrDefault(className, new ArrayList<>());

                classSummaries.put(className, ClassDTOs.ClassResponse.builder()
                        // Use className as id to support /classes/:classId/students drill-down.
                        .id(className)
                        .className(className)
                        .section(classRoom.getSection())
                        .academicYear(classRoom.getAcademicYear())
                        .subject(classRoom.getSubject())
                        .schoolId(classRoom.getSchoolId() != null ? classRoom.getSchoolId() : classToSchoolId.get(className))
                        .teacherId(teacherId)
                        .studentIds(studentIds)
                        .isActive(true)
                        .createdAt(classRoom.getCreatedAt())
                        .updatedAt(classRoom.getUpdatedAt())
                        .studentCount(studentIds.size())
                        .build());
            }

            for (Map.Entry<String, List<String>> entry : classToStudentIds.entrySet()) {
                String className = entry.getKey();
                List<String> studentIds = entry.getValue();

                if (classSummaries.containsKey(className)) {
                    continue;
                }

                classSummaries.put(className, ClassDTOs.ClassResponse.builder()
                        .id(className)
                        .className(className)
                        .section(null)
                        .academicYear(null)
                        .subject(null)
                        .schoolId(classToSchoolId.get(className))
                        .teacherId(teacherId)
                        .studentIds(studentIds)
                        .isActive(true)
                        .createdAt(null)
                        .updatedAt(null)
                        .studentCount(studentIds.size())
                        .build());
            }

            List<ClassDTOs.ClassResponse> list = new ArrayList<>(classSummaries.values());

            list.sort(Comparator.comparing(ClassDTOs.ClassResponse::getClassName, String.CASE_INSENSITIVE_ORDER));
            return list;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error fetching classes", e);
        }
    }

    private String normalizeClassName(String className) {
        if (className == null) {
            return null;
        }
        String trimmed = className.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public ClassDTOs.ClassResponse updateClass(String classId, ClassDTOs.ClassCreateRequest request, String teacherId) {
        try {
            DocumentReference docRef = firestore.collection(CLASSES_COLLECTION).document(classId);
            DocumentSnapshot snap = docRef.get().get();
            if (!snap.exists()) {
                throw new ResourceNotFoundException("Class", "id", classId);
            }
            ClassRoom classRoom = snap.toObject(ClassRoom.class);
            if (classRoom == null || !teacherId.equals(classRoom.getTeacherId())) {
                throw new ResourceNotFoundException("Class", "id", classId);
            }

            classRoom.setClassName(request.getClassName());
            classRoom.setSection(request.getSection());
            classRoom.setAcademicYear(request.getAcademicYear());
            classRoom.setSubject(request.getSubject());
            classRoom.setStudentIds(request.getStudentIds() == null ? new ArrayList<>() : request.getStudentIds());
            classRoom.setUpdatedAt(java.time.Instant.now().toString());
            docRef.set(classRoom).get();
            return toResponse(classRoom);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error updating class", e);
        }
    }

    public void deleteClass(String classId, String teacherId) {
        try {
            DocumentReference docRef = firestore.collection(CLASSES_COLLECTION).document(classId);
            DocumentSnapshot snap = docRef.get().get();
            if (!snap.exists()) {
                throw new ResourceNotFoundException("Class", "id", classId);
            }
            ClassRoom classRoom = snap.toObject(ClassRoom.class);
            if (classRoom == null || !teacherId.equals(classRoom.getTeacherId())) {
                throw new ResourceNotFoundException("Class", "id", classId);
            }
            classRoom.setActive(false);
            classRoom.setUpdatedAt(java.time.Instant.now().toString());
            docRef.set(classRoom).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error deleting class", e);
        }
    }

    private ClassDTOs.ClassResponse toResponse(ClassRoom c) {
        return ClassDTOs.ClassResponse.builder()
                .id(c.getId())
                .className(c.getClassName())
                .section(c.getSection())
                .academicYear(c.getAcademicYear())
                .subject(c.getSubject())
                .schoolId(c.getSchoolId())
                .teacherId(c.getTeacherId())
                .studentIds(c.getStudentIds())
                .isActive(c.isActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .studentCount(c.getStudentIds() == null ? 0 : c.getStudentIds().size())
                .build();
    }
}
