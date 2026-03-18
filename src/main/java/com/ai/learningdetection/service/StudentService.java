package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.StudentDTOs;
import com.ai.learningdetection.entity.Student;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.ai.learningdetection.exception.UnauthorizedAccessException;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final Firestore firestore;
    private final ForeignKeyValidator foreignKeyValidator;  // ✅ Add FK validation

    private static final String STUDENTS_COLLECTION = "students";
    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String PAPERS_COLLECTION = "test_papers";

    // -------------------------------------------------------
    // Get all students for a teacher (OPTIMIZED - batched queries)
    // -------------------------------------------------------
    public List<StudentDTOs.StudentResponse> getStudentsByTeacher(String teacherId, String search, String className, String tag, String rollNumber) {
        try {
            long startTime = System.currentTimeMillis();
            log.info("🔍 Starting getStudentsByTeacher for teacherId: {}", teacherId);
            
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
            List<Student> students = querySnapshot.getDocuments().stream()
                    .map(doc -> doc.toObject(Student.class))
                    .collect(Collectors.toList());
            
            log.info("✅ Found {} students for teacher: {}", students.size(), teacherId);
            
            if (students.isEmpty()) {
                log.warn("⚠️  No active students found for teacher: {}", teacherId);
                return new ArrayList<>();
            }

            // OPTIMIZATION #1: Batch fetch all teacher names (1 query per unique teacher)
            Map<String, String> teacherNameCache = batchFetchTeacherNames(students);

            // OPTIMIZATION #2: Batch count papers for all students (1 query per batch of 10 students)
            Map<String, Integer> paperCountCache = batchCountPapers(students);

            List<StudentDTOs.StudentResponse> responses = students.stream()
                    .map(s -> toResponseOptimized(s, teacherNameCache, paperCountCache))
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
            
            long queryTime = System.currentTimeMillis() - startTime;
            log.info("✅ getStudentsByTeacher completed: {} students returned in {}ms", responses.size(), queryTime);
            return responses;
        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Firestore error fetching students for teacher: {}", teacherId, e);
            throw new RuntimeException("Firestore error fetching students", e);
        }
    }

    // -------------------------------------------------------
    // OPTIMIZATION: Batch fetch teacher names
    // -------------------------------------------------------
    private Map<String, String> batchFetchTeacherNames(List<Student> students) throws ExecutionException, InterruptedException {
        Map<String, String> cache = new HashMap<>();
        
        // Get unique teacher IDs
        Set<String> teacherIds = students.stream()
                .map(Student::getTeacherId)
                .collect(Collectors.toSet());

        // Batch fetch all teachers
        for (String teacherId : teacherIds) {
            DocumentSnapshot snap = firestore.collection(TEACHERS_COLLECTION).document(teacherId).get().get();
            String teacherName = snap.exists() ? snap.getString("name") : "Unknown";
            cache.put(teacherId, teacherName);
        }

        return cache;
    }

    // -------------------------------------------------------
    // OPTIMIZATION: Batch count papers for all students
    // -------------------------------------------------------
    private Map<String, Integer> batchCountPapers(List<Student> students) throws ExecutionException, InterruptedException {
        Map<String, Integer> cache = new HashMap<>();
        
        // Initialize all student IDs to 0
        for (Student s : students) {
            cache.put(s.getId(), 0);
        }
        
        // Firestore in() operator max 10 items, so batch in groups
        for (int i = 0; i < students.size(); i += 10) {
            List<String> studentIdBatch = students.stream()
                    .skip(i)
                    .limit(10)
                    .map(Student::getId)
                    .collect(Collectors.toList());

            QuerySnapshot paperCounts = firestore.collection(PAPERS_COLLECTION)
                    .whereIn("studentId", studentIdBatch)
                    .get().get();

            // Count papers per student in this batch
            for (QueryDocumentSnapshot doc : paperCounts.getDocuments()) {
                String studentId = doc.getString("studentId");
                if (studentId != null) {
                    cache.put(studentId, cache.getOrDefault(studentId, 0) + 1);
                }
            }
        }

        return cache;
    }

    // -------------------------------------------------------
    // Create a new student
    // -------------------------------------------------------
    public StudentDTOs.StudentResponse createStudent(StudentDTOs.StudentRequest request, String teacherId) {
        try {
            // ✅ FK Validation #1: Validate teacher exists
            foreignKeyValidator.validateTeacherExists(teacherId);

            DocumentSnapshot teacherSnap = firestore.collection(TEACHERS_COLLECTION).document(teacherId).get().get();
            if (!teacherSnap.exists()) {
                throw new ResourceNotFoundException("Teacher", "id", teacherId);
            }

            // ✅ FK Validation #2: Validate parent exists (if provided)
            if (request.getParentUid() != null && !request.getParentUid().isBlank()) {
                foreignKeyValidator.validateParentExists(request.getParentUid());
            }

            // Enforce roll number uniqueness within school
            String schoolId = request.getSchoolId();
            if (schoolId == null || schoolId.isBlank()) {
                // Try standardized 'schoolId' field first, fallback to old 'school' field for migration
                schoolId = teacherSnap.getString("schoolId");
                if (schoolId == null || schoolId.isBlank()) {
                    schoolId = teacherSnap.getString("school");  // ✅ Fallback to old field name for backward compatibility
                }
            }

            // ✅ Allow creation without explicit school assignment (use default if not set)
            if (schoolId == null || schoolId.isBlank()) {
                schoolId = "UNASSIGNED";  // Default value - teacher may not have selected school yet
                log.warn("Teacher {} creating student without school assignment (using default: {})", teacherId, schoolId);
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
    // -------------------------------------------------------
    // Optimized toResponse - uses pre-fetched data (NO QUERIES)
    // -------------------------------------------------------
    private StudentDTOs.StudentResponse toResponseOptimized(
            Student s, 
            Map<String, String> teacherNameCache, 
            Map<String, Integer> paperCountCache) {
        String teacherName = teacherNameCache.getOrDefault(s.getTeacherId(), "Unknown");
        int totalPapers = paperCountCache.getOrDefault(s.getId(), 0);

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
                .totalPapers(totalPapers)
                .build();
    }

    // -------------------------------------------------------
    // Fallback toResponse - used for single student fetch
    // -------------------------------------------------------
    private StudentDTOs.StudentResponse toResponse(Student s) {
        try {
            DocumentSnapshot teacherSnap = firestore.collection(TEACHERS_COLLECTION).document(s.getTeacherId()).get().get();
            String teacherName = teacherSnap.exists() ? teacherSnap.getString("name") : "Unknown";

            QuerySnapshot papers = firestore.collection(PAPERS_COLLECTION)
                    .whereEqualTo("studentId", s.getId())
                    .get().get();

            // Fallback: single student fetch is acceptable
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
                    .teacherName(teacherName != null ? teacherName : "Unknown")
                    .parentUid(s.getParentUid())
                    .profilePhotoUrl(s.getProfilePhotoUrl())
                    .isActive(s.isActive())
                    .tags(s.getTags() != null ? s.getTags() : java.util.Collections.emptyList())
                    .createdAt(s.getCreatedAt())
                    .updatedAt(s.getUpdatedAt())
                    .totalPapers(papers.size())
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error building student response: {}", e.getMessage());
            return StudentDTOs.StudentResponse.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .teacherName("Unknown")
                    .totalPapers(0)
                    .build();
        }
    }
}

