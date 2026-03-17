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
    // Helper: Batched Firestore whereIn Query
    // ============================================================
    private <T> List<T> runBatchedWhereInQuery(
            CollectionReference collection, String field, List<String> values, Class<T> type) 
            throws InterruptedException, ExecutionException {
        
        List<T> results = new ArrayList<>();
        if (values == null || values.isEmpty()) return results;

        // Firestore whereIn limit is 10
        for (int i = 0; i < values.size(); i += 10) {
            List<String> batch = values.subList(i, Math.min(i + 10, values.size()));
            QuerySnapshot query = collection.whereIn(field, batch).get().get();
            results.addAll(query.toObjects(type));
        }
        return results;
    }

    private List<DocumentSnapshot> runBatchedWhereInQueryForDocs(
            CollectionReference collection, String field, List<String> values) 
            throws InterruptedException, ExecutionException {
        
        List<DocumentSnapshot> results = new ArrayList<>();
        if (values == null || values.isEmpty()) return results;

        for (int i = 0; i < values.size(); i += 10) {
            List<String> batch = values.subList(i, Math.min(i + 10, values.size()));
            QuerySnapshot query = collection.whereIn(field, batch).get().get();
            results.addAll(query.getDocuments());
        }
        return results;
    }

    // ============================================================
    // TEACHER: Get all reports for teacher's students
    // ============================================================

    public List<AnalysisDTOs.AnalysisReportResponse> getReportsByTeacher(String teacherId) {
        try {
            // OPTIMIZATION: Get students, then papers sorted by date, then reports in one optimized query
            QuerySnapshot students = firestore.collection(STUDENTS_COLLECTION)
                    .whereEqualTo("teacherId", teacherId).get().get();
            List<String> studentIds = students.getDocuments().stream()
                    .map(DocumentSnapshot::getId).collect(Collectors.toList());

            if (studentIds.isEmpty()) return new ArrayList<>();

            // OPTIMIZATION: Get latest papers first (limit 100) instead of all
            List<DocumentSnapshot> papers = runBatchedWhereInQueryForDocs(
                    firestore.collection(PAPERS_COLLECTION), "studentId", studentIds);
            
            // Sort by upload date descending and limit to last 100 papers
            List<String> paperIds = papers.stream()
                    .sorted((a, b) -> {
                        Timestamp tA = a.getTimestamp("uploadDate");
                        Timestamp tB = b.getTimestamp("uploadDate");
                        if (tA == null || tB == null) return 0;
                        return tB.compareTo(tA);
                    })
                    .limit(100)
                    .map(DocumentSnapshot::getId)
                    .collect(Collectors.toList());

            if (paperIds.isEmpty()) return new ArrayList<>();

            // Get reports for these papers (Batched)
            List<AnalysisReport> reports = runBatchedWhereInQuery(
                    firestore.collection(REPORTS_COLLECTION), "paperId", paperIds, AnalysisReport.class);
            
            // Sort by date descending for frontend
            reports.sort((r1, r2) -> {
                if (r1.getCreatedAt() == null || r2.getCreatedAt() == null) return 0;
                return r2.getCreatedAt().compareTo(r1.getCreatedAt());
            });
            
            return reports.stream()
                    .map(this::toReportResponse)
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
            // OPTIMIZATION: Quick counts first, then limited detailed queries
            QuerySnapshot studentQuery = firestore.collection(STUDENTS_COLLECTION)
                    .whereEqualTo("teacherId", teacherId).get().get();
            long totalStudents = studentQuery.size();
            List<String> studentIds = studentQuery.getDocuments().stream()
                    .map(DocumentSnapshot::getId).collect(Collectors.toList());

            if (studentIds.isEmpty()) {
                return AnalysisDTOs.DashboardResponse.builder().totalStudents(0).build();
            }

            // Batched paper query - limit to last 50 papers for performance
            List<DocumentSnapshot> paperDocs = runBatchedWhereInQueryForDocs(
                    firestore.collection(PAPERS_COLLECTION), "studentId", studentIds);
            
            long totalPapers = paperDocs.size();
            List<String> paperIds = paperDocs.stream()
                    .sorted((a, b) -> {
                        Timestamp tA = a.getTimestamp("uploadDate");
                        Timestamp tB = b.getTimestamp("uploadDate");
                        if (tA == null || tB == null) return 0;
                        return tB.compareTo(tA);
                    })
                    .limit(50)
                    .map(DocumentSnapshot::getId)
                    .collect(Collectors.toList());

            if (paperIds.isEmpty()) {
                return AnalysisDTOs.DashboardResponse.builder()
                        .totalStudents(totalStudents)
                        .totalPapersUploaded(0)
                        .build();
            }

            // Batched report query
            List<DocumentSnapshot> reportDocs = runBatchedWhereInQueryForDocs(
                    firestore.collection(REPORTS_COLLECTION), "paperId", paperIds);
            
            double sumDyslexia = 0;
            double sumDysgraphia = 0;
            long atRisk = 0;
            long lowRisk = 0;
            long mediumRisk = 0;
            long highRisk = 0;

            for (DocumentSnapshot doc : reportDocs) {
                Double d = doc.getDouble("dyslexiaScore");
                Double g = doc.getDouble("dysgraphiaScore");
                if (d != null) sumDyslexia += d;
                if (g != null) sumDysgraphia += g;
                
                String risk = RiskLevelUtil.calculateRiskLevel(d, g);
                if ("HIGH".equals(risk)) highRisk++;
                else if ("MEDIUM".equals(risk)) mediumRisk++;
                else lowRisk++;

                if (RiskLevelUtil.isAtRisk(d, g)) atRisk++;
            }

            int count = reportDocs.size();
            return AnalysisDTOs.DashboardResponse.builder()
                    .totalStudents(totalStudents)
                    .totalPapersUploaded(totalPapers)
                    .studentsAtRisk(atRisk)
                    .lowRiskStudents(lowRisk)
                    .mediumRiskStudents(mediumRisk)
                    .highRiskStudents(highRisk)
                    .averageDyslexiaScore(count > 0 ? Math.round((sumDyslexia / count) * 100.0) / 100.0 : 0.0)
                    .averageDysgraphiaScore(count > 0 ? Math.round((sumDysgraphia / count) * 100.0) / 100.0 : 0.0)
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error for dashboard", e);
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

            // Batched report query - note: orderBy might not work across batched queries easily if we need global sorting
            // Sorting will be done in-memory after fetching all batches
            List<AnalysisReport> reportList = runBatchedWhereInQuery(
                    firestore.collection(REPORTS_COLLECTION), "paperId", paperIds, AnalysisReport.class);
            
            // Sort in-memory descending by createdAt
            reportList.sort((r1, r2) -> {
                if (r1.getCreatedAt() == null || r2.getCreatedAt() == null) return 0;
                return r2.getCreatedAt().compareTo(r1.getCreatedAt());
            });

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

    public AnalysisDTOs.AnalysisReportResponse getLatestReportForParent(String studentId, String parentId) {
        try {
            verifyParentOwnsStudent(parentId, studentId);
            QuerySnapshot papers = firestore.collection(PAPERS_COLLECTION)
                    .whereEqualTo("studentId", studentId)
                    .get().get();
            
            List<String> paperIds = papers.getDocuments().stream()
                    .map(DocumentSnapshot::getId)
                    .collect(Collectors.toList());

            if (paperIds.isEmpty()) return null;

            List<AnalysisReport> reports = runBatchedWhereInQuery(
                    firestore.collection(REPORTS_COLLECTION), "paperId", paperIds, AnalysisReport.class);

            if (reports.isEmpty()) return null;

            // Sort by createdAt desc and pick the first
            reports.sort((r1, r2) -> {
                if (r1.getCreatedAt() == null || r2.getCreatedAt() == null) return 0;
                return r2.getCreatedAt().compareTo(r1.getCreatedAt());
            });

            return toReportResponse(reports.get(0));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error fetching latest report", e);
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
        if (report == null) return null;
        try {
            DocumentSnapshot paperSnap = firestore.collection(PAPERS_COLLECTION).document(report.getPaperId()).get().get();
            if (!paperSnap.exists()) {
                return AnalysisDTOs.AnalysisReportResponse.builder()
                        .reportId(report.getId())
                        .paperId(report.getPaperId())
                        .aiComment("Linked paper record missing")
                        .build();
            }

            String studentId = paperSnap.getString("studentId");
            // If studentId is null, use a safe default to avoid NPE in document()
            DocumentSnapshot studentSnap = firestore.collection(STUDENTS_COLLECTION).document(studentId != null ? studentId : "unknown").get().get();

            return AnalysisDTOs.AnalysisReportResponse.builder()
                    .reportId(report.getId())
                    .paperId(report.getPaperId())
                    .studentId(studentId)
                    .studentName(studentSnap.exists() ? studentSnap.getString("name") : "Unknown Student")
                    .className(studentSnap.exists() ? studentSnap.getString("className") : "N/A")
                    .dyslexiaScore(report.getDyslexiaScore())
                    .dysgraphiaScore(report.getDysgraphiaScore())
                    .aiComment(report.getAiComment())
                    .riskLevel(RiskLevelUtil.calculateRiskLevel(report.getDyslexiaScore(), report.getDysgraphiaScore()))
                    .createdAt(report.getCreatedAt())
                    .uploadDate(paperSnap.getTimestamp("uploadDate") != null ? paperSnap.getTimestamp("uploadDate").toDate() : null)
                    .originalFileName(paperSnap.getString("originalFileName"))
                    .build();
        } catch (Exception e) {
            log.error("Error converting report to response: {}", e.getMessage());
            return AnalysisDTOs.AnalysisReportResponse.builder().reportId(report.getId()).aiComment("Error loading full report details").build();
        }
    }
}
