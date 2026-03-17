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
import java.util.List;
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
            DocumentReference docRef = firestore.collection(CLASSES_COLLECTION).document();
            String now = java.time.Instant.now().toString();

            ClassRoom classRoom = ClassRoom.builder()
                    .id(docRef.getId())
                    .className(request.getClassName())
                    .section(request.getSection())
                    .academicYear(request.getAcademicYear())
                    .subject(request.getSubject())
                    .schoolId(request.getSchoolId())
                    .teacherId(request.getTeacherId())
                    .studentIds(request.getStudentIds() == null ? new ArrayList<>() : request.getStudentIds())
                    .isActive(true)
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
            QuerySnapshot query = firestore.collection(CLASSES_COLLECTION)
                    .whereEqualTo("teacherId", teacherId)
                    .whereEqualTo("isActive", true)
                    .get().get();
            List<ClassDTOs.ClassResponse> list = new ArrayList<>();
            for (DocumentSnapshot doc : query.getDocuments()) {
                ClassRoom classRoom = doc.toObject(ClassRoom.class);
                if (classRoom != null) list.add(toResponse(classRoom));
            }
            return list;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error fetching classes", e);
        }
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
            classRoom.setIsActive(false);
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
