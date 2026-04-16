package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.StudentNote;
import com.ai.learningdetection.exception.UnauthorizedAccessException;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentNoteService {

    private final Firestore firestore;
    private static final String NOTES_COLLECTION = "student_notes";
    private static final String STUDENTS_COLLECTION = "students";

    /**
     * Teacher creates a note for a student.
     */
    public StudentNote createNote(String studentId, String teacherId, String teacherName,
                                  String content, boolean visibleToParent) throws ExecutionException, InterruptedException {
        verifyTeacherOwnsStudent(studentId, teacherId);

        DocumentReference ref = firestore.collection(NOTES_COLLECTION).document();
        String now = Instant.now().toString();
        StudentNote note = StudentNote.builder()
                .id(ref.getId())
                .studentId(studentId)
                .authorId(teacherId)
                .authorName(teacherName)
                .content(content)
                .visibleToParent(visibleToParent)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ref.set(note).get();
        return note;
    }

    /**
     * Get notes for a student (teacher view — all notes).
     */
    public List<StudentNote> getNotesForTeacher(String studentId, String teacherId) throws ExecutionException, InterruptedException {
        verifyTeacherOwnsStudent(studentId, teacherId);
        return queryNotes(studentId, false);
    }

    /**
     * Get notes for a student (parent view — only parent-visible notes).
     */
    public List<StudentNote> getNotesForParent(String studentId, String parentId) throws ExecutionException, InterruptedException {
        verifyParentLinked(studentId, parentId);
        return queryNotes(studentId, true);
    }

    /**
     * Teacher updates a note.
     */
    public StudentNote updateNote(String noteId, String teacherId, String content, Boolean visibleToParent) throws ExecutionException, InterruptedException {
        DocumentReference ref = firestore.collection(NOTES_COLLECTION).document(noteId);
        DocumentSnapshot doc = ref.get().get();
        if (!doc.exists() || !teacherId.equals(doc.getString("authorId"))) {
            throw new UnauthorizedAccessException("Note not found or not yours.");
        }

        StudentNote note = doc.toObject(StudentNote.class);
        if (content != null) note.setContent(content);
        if (visibleToParent != null) note.setVisibleToParent(visibleToParent);
        note.setUpdatedAt(Instant.now().toString());
        ref.set(note).get();
        return note;
    }

    /**
     * Teacher deletes a note.
     */
    public void deleteNote(String noteId, String teacherId) throws ExecutionException, InterruptedException {
        DocumentReference ref = firestore.collection(NOTES_COLLECTION).document(noteId);
        DocumentSnapshot doc = ref.get().get();
        if (!doc.exists() || !teacherId.equals(doc.getString("authorId"))) {
            throw new UnauthorizedAccessException("Note not found or not yours.");
        }
        ref.delete().get();
    }

    private List<StudentNote> queryNotes(String studentId, boolean parentVisibleOnly) throws ExecutionException, InterruptedException {
        Query query = firestore.collection(NOTES_COLLECTION)
                .whereEqualTo("studentId", studentId)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (parentVisibleOnly) {
            query = query.whereEqualTo("visibleToParent", true);
        }

        QuerySnapshot snap = query.get().get();
        List<StudentNote> list = new ArrayList<>();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            list.add(doc.toObject(StudentNote.class));
        }
        return list;
    }

    private void verifyTeacherOwnsStudent(String studentId, String teacherId) throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = firestore.collection(STUDENTS_COLLECTION).document(studentId).get().get();
        if (!doc.exists() || !teacherId.equals(doc.getString("teacherId"))) {
            throw new UnauthorizedAccessException("Student does not belong to you.");
        }
    }

    private void verifyParentLinked(String studentId, String parentId) throws ExecutionException, InterruptedException {
        QuerySnapshot snap = firestore.collection("parent_student_relationships")
                .whereEqualTo("parentId", parentId)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("verificationStatus", "VERIFIED")
                .limit(1)
                .get().get();
        if (snap.isEmpty()) {
            throw new UnauthorizedAccessException("You are not linked to this student.");
        }
    }
}
