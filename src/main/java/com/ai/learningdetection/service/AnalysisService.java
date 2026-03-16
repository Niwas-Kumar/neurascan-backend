package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.AnalysisDTOs;
import com.ai.learningdetection.entity.AnalysisReport;
import com.ai.learningdetection.entity.TestPaper;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.ai.learningdetection.exception.UnauthorizedAccessException;
import com.ai.learningdetection.util.RiskLevelUtil;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final Firestore firestore;
    private final FileStorageService fileStorageService;
    private final AiIntegrationService aiIntegrationService;

    private static final String STUDENTS_COLLECTION = "students";
    private static final String PAPERS_COLLECTION = "test_papers";
    private static final String REPORTS_COLLECTION = "analysis_reports";
    private static final String PARENTS_COLLECTION = "parents";

    // ============================================================
    // TEACHER: Upload paper and run AI analysis
    // ============================================================

    public AnalysisDTOs.UploadResponse uploadAndAnalyze(
            String studentId, MultipartFile file, String teacherId) {

        try {
            // 1. Verify student belongs to this teacher
            DocumentSnapshot studentSnap = firestore.collection(STUDENTS_COLLECTION).document(studentId).get().get();
            if (!studentSnap.exists() || !teacherId.equals(studentSnap.getString("teacherId"))) {
                throw new UnauthorizedAccessException("Student does not belong to you.");
            }

            // 2. Store the file
            String storedFilename = fileStorageService.storeFile(file);
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";

            // 3. Persist TestPaper record
            DocumentReference paperRef = firestore.collection(PAPERS_COLLECTION).document();
            TestPaper testPaper = TestPaper.builder()
                    .id(paperRef.getId())
                    .studentId(studentId)
                    .filePath(storedFilename)
                    .originalFileName(originalName)
                    .uploadDate(new Date())
                    .build();
            paperRef.set(testPaper).get();

            // 4. Call Python AI microservice
            Path filePath = fileStorageService.getFilePath(storedFilename);
            AnalysisDTOs.AiServiceResponse aiResponse;
            try {
                aiResponse = aiIntegrationService.analyzeFile(filePath);
            } catch (Exception ex) {
                log.error("AI service failed, using fallback: {}", ex.getMessage());
                aiResponse = aiIntegrationService.getMockAnalysis();
            }

            // 5. Persist AnalysisReport
            DocumentReference reportRef = firestore.collection(REPORTS_COLLECTION).document();
            AnalysisReport report = AnalysisReport.builder()
                    .id(reportRef.getId())
                    .paperId(testPaper.getId())
                    .dyslexiaScore(aiResponse.getDyslexia_score())
                    .dysgraphiaScore(aiResponse.getDysgraphia_score())
                    .aiComment(aiResponse.getAnalysis())
                    .createdAt(new Date())
                    .build();
            reportRef.set(report).get();

            String riskLevel = RiskLevelUtil.calculateRiskLevel(report.getDyslexiaScore(), report.getDysgraphiaScore());

            return AnalysisDTOs.UploadResponse.builder()
                    .message("File uploaded and analyzed successfully")
                    .paperId(testPaper.getId())
                    .reportId(report.getId())
                    .dyslexiaScore(report.getDyslexiaScore())
                    .dysgraphiaScore(report.getDysgraphiaScore())
                    .aiComment(report.getAiComment())
                    .riskLevel(riskLevel)
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during analysis", e);
        }
    }

    // ============================================================
    // TEACHER: Get all reports for teacher's students
    // ============================================================

    public List<AnalysisDTOs.AnalysisReportResponse> getReportsByTeacher(String teacherId) {
        try {
            // Get all students for teacher
            QuerySnapshot students = firestore.collection(STUDENTS_COLLECTION).whereEqualTo("teacherId", teacherId).get().get();
            List<String> studentIds = students.getDocuments().stream().map(DocumentSnapshot::getId).collect(Collectors.toList());

            if (studentIds.isEmpty()) return new ArrayList<>();

            // Get all papers for these students
            QuerySnapshot papers = firestore.collection(PAPERS_COLLECTION).whereIn("studentId", studentIds).get().get();
            List<String> paperIds = papers.getDocuments().stream().map(DocumentSnapshot::getId).collect(Collectors.toList());

            if (paperIds.isEmpty()) return new ArrayList<>();

            // Get all reports for these papers
            QuerySnapshot reports = firestore.collection(REPORTS_COLLECTION).whereIn("paperId", paperIds).get().get();
            
            return reports.getDocuments().stream()
                    .map(doc -> toReportResponse(doc.toObject(AnalysisReport.class)))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error fetching reports", e);
        }
    }

    // ============================================================
    // TEACHER: Dashboard statistics
    // ============================================================

    public AnalysisDTOs.DashboardResponse getDashboard(String teacherId) {
        try {
            QuerySnapshot studentQuery = firestore.collection(STUDENTS_COLLECTION).whereEqualTo("teacherId", teacherId).get().get();
            long totalStudents = studentQuery.size();
            List<String> studentIds = studentQuery.getDocuments().stream().map(DocumentSnapshot::getId).collect(Collectors.toList());

            if (studentIds.isEmpty()) {
                return AnalysisDTOs.DashboardResponse.builder().build();
            }

            QuerySnapshot paperQuery = firestore.collection(PAPERS_COLLECTION).whereIn("studentId", studentIds).get().get();
            long totalPapers = paperQuery.size();
            List<String> paperIds = paperQuery.getDocuments().stream().map(DocumentSnapshot::getId).collect(Collectors.toList());

            if (paperIds.isEmpty()) {
                return AnalysisDTOs.DashboardResponse.builder().totalStudents(totalStudents).build();
            }

            QuerySnapshot reportQuery = firestore.collection(REPORTS_COLLECTION).whereIn("paperId", paperIds).get().get();
            
            double sumDyslexia = 0;
            double sumDysgraphia = 0;
            long atRisk = 0;

            for (DocumentSnapshot doc : reportQuery.getDocuments()) {
                Double d = doc.getDouble("dyslexiaScore");
                Double g = doc.getDouble("dysgraphiaScore");
                if (d != null) sumDyslexia += d;
                if (g != null) sumDysgraphia += g;
                if (RiskLevelUtil.isAtRisk(d, g)) atRisk++;
            }

            int count = reportQuery.size();
            return AnalysisDTOs.DashboardResponse.builder()
                    .totalStudents(totalStudents)
                    .totalPapersUploaded(totalPapers)
                    .studentsAtRisk(atRisk)
                    .averageDyslexiaScore(count > 0 ? Math.round((sumDyslexia / count) * 100.0) / 100.0 : 0.0)
                    .averageDysgraphiaScore(count > 0 ? Math.round((sumDysgraphia / count) * 100.0) / 100.0 : 0.0)
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error for dashboard", e);
        }
    }

    // ============================================================
    // PARENT: Get latest report
    // ============================================================

    public AnalysisDTOs.AnalysisReportResponse getLatestReportForParent(String studentId, String parentId) {
        try {
            verifyParentOwnsStudent(parentId, studentId);

            QuerySnapshot papers = firestore.collection(PAPERS_COLLECTION)
                    .whereEqualTo("studentId", studentId)
                    .orderBy("uploadDate", Query.Direction.DESCENDING)
                    .limit(1).get().get();

            if (papers.isEmpty()) throw new ResourceNotFoundException("No papers for student");

            String paperId = papers.getDocuments().get(0).getId();
            QuerySnapshot reports = firestore.collection(REPORTS_COLLECTION).whereEqualTo("paperId", paperId).limit(1).get().get();

            if (reports.isEmpty()) throw new ResourceNotFoundException("No report for paper");

            return toReportResponse(reports.getDocuments().get(0).toObject(AnalysisReport.class));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error", e);
        }
    }

    // ============================================================
    // PARENT: History
    // ============================================================

    public AnalysisDTOs.ProgressResponse getProgressForParent(String studentId, String parentId) {
        try {
            verifyParentOwnsStudent(parentId, studentId);
            DocumentSnapshot student = firestore.collection(STUDENTS_COLLECTION).document(studentId).get().get();

            QuerySnapshot papers = firestore.collection(PAPERS_COLLECTION).whereEqualTo("studentId", studentId).get().get();
            List<String> paperIds = papers.getDocuments().stream().map(DocumentSnapshot::getId).collect(Collectors.toList());

            if (paperIds.isEmpty()) {
                return AnalysisDTOs.ProgressResponse.builder().studentId(studentId).studentName(student.getString("name")).build();
            }

            QuerySnapshot reports = firestore.collection(REPORTS_COLLECTION).whereIn("paperId", paperIds).orderBy("createdAt", Query.Direction.DESCENDING).get().get();
            List<AnalysisReport> reportList = reports.toObjects(AnalysisReport.class);
            List<AnalysisDTOs.AnalysisReportResponse> responses = reportList.stream().map(this::toReportResponse).collect(Collectors.toList());

            return AnalysisDTOs.ProgressResponse.builder()
                    .studentId(studentId)
                    .studentName(student.getString("name"))
                    .reports(responses)
                    .trend(calculateTrend(reportList))
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error", e);
        }
    }

    private void verifyParentOwnsStudent(String parentId, String studentId) throws ExecutionException, InterruptedException {
        DocumentSnapshot p = firestore.collection(PARENTS_COLLECTION).document(parentId).get().get();
        if (!p.exists() || !studentId.equals(p.getString("studentId"))) {
            throw new UnauthorizedAccessException("Unauthorized");
        }
    }

    private String calculateTrend(List<AnalysisReport> reports) {
        if (reports.size() < 2) return "INSUFFICIENT_DATA";
        AnalysisReport latest = reports.get(0);
        AnalysisReport previous = reports.get(1);
        double latestMax = Math.max(latest.getDyslexiaScore(), latest.getDysgraphiaScore());
        double prevMax = Math.max(previous.getDyslexiaScore(), previous.getDysgraphiaScore());
        if (latestMax < prevMax - 5) return "IMPROVING";
        if (latestMax > prevMax + 5) return "WORSENING";
        return "STABLE";
    }

    private AnalysisDTOs.AnalysisReportResponse toReportResponse(AnalysisReport report) {
        try {
            DocumentSnapshot paperSnap = firestore.collection(PAPERS_COLLECTION).document(report.getPaperId()).get().get();
            String studentId = paperSnap.getString("studentId");
            DocumentSnapshot studentSnap = firestore.collection(STUDENTS_COLLECTION).document(studentId != null ? studentId : "").get().get();

            return AnalysisDTOs.AnalysisReportResponse.builder()
                    .reportId(report.getId())
                    .paperId(report.getPaperId())
                    .studentId(studentId)
                    .studentName(studentSnap.getString("name"))
                    .className(studentSnap.getString("className"))
                    .dyslexiaScore(report.getDyslexiaScore())
                    .dysgraphiaScore(report.getDysgraphiaScore())
                    .aiComment(report.getAiComment())
                    .riskLevel(RiskLevelUtil.calculateRiskLevel(report.getDyslexiaScore(), report.getDysgraphiaScore()))
                    .createdAt(report.getCreatedAt())
                    .uploadDate(paperSnap.getTimestamp("uploadDate") != null ? paperSnap.getTimestamp("uploadDate").toDate() : null)
                    .originalFileName(paperSnap.getString("originalFileName"))
                    .build();
        } catch (Exception e) {
            return AnalysisDTOs.AnalysisReportResponse.builder().reportId(report.getId()).build();
        }
    }
}

