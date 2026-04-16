package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.AnalysisReport;
import com.ai.learningdetection.entity.Student;
import com.ai.learningdetection.util.RiskLevelUtil;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final Firestore firestore;

    private static final String STUDENTS_COLLECTION = "students";
    private static final String PAPERS_COLLECTION = "test_papers";
    private static final String REPORTS_COLLECTION = "analysis_reports";

    /**
     * Export students list as CSV for a teacher.
     */
    public String exportStudentsCsv(String teacherId) throws ExecutionException, InterruptedException {
        QuerySnapshot snap = firestore.collection(STUDENTS_COLLECTION)
                .whereEqualTo("teacherId", teacherId)
                .get().get();

        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            writer.writeNext(new String[]{"Name", "Roll Number", "Class", "Section", "Age", "Gender", "Date of Birth", "Status"});
            for (DocumentSnapshot doc : snap.getDocuments()) {
                Student s = doc.toObject(Student.class);
                if (s == null) continue;
                writer.writeNext(new String[]{
                        safe(s.getName()),
                        safe(s.getRollNumber()),
                        safe(s.getClassName()),
                        safe(s.getSection()),
                        s.getAge() != null ? String.valueOf(s.getAge()) : "",
                        safe(s.getGender()),
                        safe(s.getDateOfBirth()),
                        s.isActive() ? "Active" : "Inactive"
                });
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CSV", e);
        }
        return sw.toString();
    }

    /**
     * Export analysis reports as CSV for a teacher.
     */
    public String exportReportsCsv(String teacherId) throws ExecutionException, InterruptedException {
        // 1. Get all students for this teacher
        QuerySnapshot studentSnap = firestore.collection(STUDENTS_COLLECTION)
                .whereEqualTo("teacherId", teacherId).get().get();
        Map<String, Student> studentMap = new HashMap<>();
        List<String> studentIds = new ArrayList<>();
        for (DocumentSnapshot doc : studentSnap.getDocuments()) {
            Student s = doc.toObject(Student.class);
            if (s != null) {
                s.setId(doc.getId());
                studentMap.put(doc.getId(), s);
                studentIds.add(doc.getId());
            }
        }

        if (studentIds.isEmpty()) {
            return csvWithHeader();
        }

        // 2. Get test papers for those students (batched)
        Map<String, String> paperToStudent = new HashMap<>();
        Map<String, Date> paperUploadDate = new HashMap<>();
        for (int i = 0; i < studentIds.size(); i += 10) {
            List<String> batch = studentIds.subList(i, Math.min(i + 10, studentIds.size()));
            QuerySnapshot paperSnap = firestore.collection(PAPERS_COLLECTION)
                    .whereIn("studentId", batch).get().get();
            for (DocumentSnapshot doc : paperSnap.getDocuments()) {
                paperToStudent.put(doc.getId(), doc.getString("studentId"));
                Timestamp ts = doc.getTimestamp("uploadDate");
                paperUploadDate.put(doc.getId(), ts != null ? ts.toDate() : null);
            }
        }

        if (paperToStudent.isEmpty()) {
            return csvWithHeader();
        }

        // 3. Get reports for those papers (batched)
        List<String> paperIds = new ArrayList<>(paperToStudent.keySet());
        List<AnalysisReport> reports = new ArrayList<>();
        for (int i = 0; i < paperIds.size(); i += 10) {
            List<String> batch = paperIds.subList(i, Math.min(i + 10, paperIds.size()));
            QuerySnapshot repSnap = firestore.collection(REPORTS_COLLECTION)
                    .whereIn("paperId", batch).get().get();
            for (DocumentSnapshot doc : repSnap.getDocuments()) {
                AnalysisReport r = doc.toObject(AnalysisReport.class);
                if (r != null) {
                    r.setId(doc.getId());
                    reports.add(r);
                }
            }
        }

        // 4. Write CSV
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            writer.writeNext(new String[]{
                    "Student Name", "Roll Number", "Class", "Dyslexia Score",
                    "Dysgraphia Score", "Risk Level", "Analysis Date", "AI Comment"
            });
            reports.sort((a, b) -> {
                if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            });
            for (AnalysisReport r : reports) {
                String studentId = paperToStudent.get(r.getPaperId());
                Student s = studentId != null ? studentMap.get(studentId) : null;
                String risk = RiskLevelUtil.calculateRiskLevel(
                        r.getDyslexiaScore() != null ? r.getDyslexiaScore() : 0,
                        r.getDysgraphiaScore() != null ? r.getDysgraphiaScore() : 0);
                Date date = r.getCreatedAt() != null ? r.getCreatedAt() : paperUploadDate.get(r.getPaperId());
                writer.writeNext(new String[]{
                        s != null ? safe(s.getName()) : "",
                        s != null ? safe(s.getRollNumber()) : "",
                        s != null ? safe(s.getClassName()) : "",
                        r.getDyslexiaScore() != null ? String.format("%.1f", r.getDyslexiaScore()) : "",
                        r.getDysgraphiaScore() != null ? String.format("%.1f", r.getDysgraphiaScore()) : "",
                        risk,
                        date != null ? sdf.format(date) : "",
                        safe(r.getAiComment())
                });
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate reports CSV", e);
        }
        return sw.toString();
    }

    private String csvWithHeader() {
        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            writer.writeNext(new String[]{
                    "Student Name", "Roll Number", "Class", "Dyslexia Score",
                    "Dysgraphia Score", "Risk Level", "Analysis Date", "AI Comment"
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CSV header", e);
        }
        return sw.toString();
    }

    private String safe(String val) {
        return val != null ? val : "";
    }
}
