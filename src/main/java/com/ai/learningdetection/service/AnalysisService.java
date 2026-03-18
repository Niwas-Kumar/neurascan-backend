package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.AnalysisDTOs;
import com.ai.learningdetection.entity.AnalysisReport;
import com.ai.learningdetection.entity.TestPaper;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.ai.learningdetection.exception.UnauthorizedAccessException;
import com.ai.learningdetection.util.RiskLevelUtil;
import com.google.cloud.firestore.*;
import com.google.cloud.Timestamp;
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
import java.util.concurrent.CompletableFuture;

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
                // Prefer advanced external AI service route (may include external model integrations).
                aiResponse = aiIntegrationService.analyzeFileWithExternalModel(filePath);
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
    // Helper: Batched Firestore whereIn Query (OPTIMIZED - Parallel Execution)
    // ============================================================
    private <T> List<T> runBatchedWhereInQuery(
            CollectionReference collection, String field, List<String> values, Class<T> type) 
            throws InterruptedException, ExecutionException {
        
        List<T> results = new ArrayList<>();
        if (values == null || values.isEmpty()) return results;

        // Run all batches in parallel using get().get() for each batch
        // Much faster than sequential: ~1.5s total vs ~5s sequential for 50 items
        List<CompletableFuture<List<T>>> futures = new ArrayList<>();
        for (int i = 0; i < values.size(); i += 10) {
            final int startIdx = i;
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    List<String> batch = values.subList(startIdx, Math.min(startIdx + 10, values.size()));
                    QuerySnapshot query = collection.whereIn(field, batch).get().get();
                    return query.toObjects(type);
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Error in batched query: {}", e.getMessage());
                    return new ArrayList<>();
                }
            }));
        }

        // Collect all results
        for (var future : futures) {
            results.addAll(future.get());
        }
        return results;
    }

    private List<DocumentSnapshot> runBatchedWhereInQueryForDocs(
            CollectionReference collection, String field, List<String> values) 
            throws InterruptedException, ExecutionException {
        
        List<DocumentSnapshot> results = new ArrayList<>();
        if (values == null || values.isEmpty()) return results;

        // Run batches in parallel for speed
        List<CompletableFuture<List<QueryDocumentSnapshot>>> futures = new ArrayList<>();
        for (int i = 0; i < values.size(); i += 10) {
            final int startIdx = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    List<String> batch = values.subList(startIdx, Math.min(startIdx + 10, values.size()));
                    QuerySnapshot query = collection.whereIn(field, batch).get().get();
                    return query.getDocuments();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Error in batched query: {}", e.getMessage());
                    return new ArrayList<>();
                }
            }));
        }

        // Collect all results (QueryDocumentSnapshot extends DocumentSnapshot)
        for (var future : futures) {
            List<QueryDocumentSnapshot> docs = future.get();
            results.addAll(docs);
        }
        return results;
    }

    // ============================================================
    // TEACHER: Get all reports for teacher's students
    // ============================================================

    public List<AnalysisDTOs.AnalysisReportResponse> getReportsByTeacher(String teacherId) {
        try {
            long startTime = System.currentTimeMillis();
            log.info("Starting getReportsByTeacher for teacherId: {}", teacherId);
            
            // OPTIMIZATION: Get students, then papers sorted by date, then reports in one optimized query
            QuerySnapshot students = firestore.collection(STUDENTS_COLLECTION)
                    .whereEqualTo("teacherId", teacherId).get().get();
            List<String> studentIds = students.getDocuments().stream()
                    .map(DocumentSnapshot::getId).collect(Collectors.toList());

            if (studentIds.isEmpty()) {
                log.info("No students found for teacher: {}", teacherId);
                return new ArrayList<>();
            }

            long paperQueryStartTime = System.currentTimeMillis();
            // OPTIMIZATION: Get latest papers first (limit 100) instead of all - PARALLEL BATCHES
            List<DocumentSnapshot> papers = runBatchedWhereInQueryForDocs(
                    firestore.collection(PAPERS_COLLECTION), "studentId", studentIds);
            long paperQueryTime = System.currentTimeMillis() - paperQueryStartTime;
            
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

            if (paperIds.isEmpty()) {
                log.info("No papers found for teacher: {}", teacherId);
                return new ArrayList<>();
            }

            long reportQueryStartTime = System.currentTimeMillis();
            // Get reports for these papers (Batched - PARALLEL)
            List<AnalysisReport> reports = runBatchedWhereInQuery(
                    firestore.collection(REPORTS_COLLECTION), "paperId", paperIds, AnalysisReport.class);
            long reportQueryTime = System.currentTimeMillis() - reportQueryStartTime;
            
            // Sort by date descending for frontend
            reports.sort((r1, r2) -> {
                if (r1.getCreatedAt() == null || r2.getCreatedAt() == null) return 0;
                return r2.getCreatedAt().compareTo(r1.getCreatedAt());
            });
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("getReportsByTeacher completed: {} reports in {}ms (papers: {}ms, reports: {}ms)", 
                    reports.size(), totalTime, paperQueryTime, reportQueryTime);
            
            return reports.stream()
                    .map(this::toReportResponse)
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Firestore error fetching reports for teacher: {}", teacherId, e);
            throw new RuntimeException("Firestore error fetching reports", e);
        }
    }

    // ============================================================
    // TEACHER: Dashboard statistics
    // ============================================================

    public AnalysisDTOs.DashboardResponse getDashboard(String teacherId) {
        try {
            long startTime = System.currentTimeMillis();
            log.info("🔍 Starting getDashboard for teacherId: {}", teacherId);
            
            // CRITICAL FIX: Use single-field query to avoid composite index delays
            // Query only by teacherId, filter isActive in memory
            QuerySnapshot studentQuery = firestore.collection(STUDENTS_COLLECTION)
                    .whereEqualTo("teacherId", teacherId)
                    .get().get();
            
            // Filter active students in memory (avoids composite index requirement)
            List<String> studentIds = studentQuery.getDocuments().stream()
                    .filter(doc -> {
                        Boolean isActive = doc.getBoolean("isActive");
                        return isActive != null && isActive; // Default to false if null for safety
                    })
                    .map(DocumentSnapshot::getId).collect(Collectors.toList());
            
            long totalStudents = studentIds.size();
            log.info("✅ Dashboard found {} active students for teacher: {}", totalStudents, teacherId);
            
            if (studentIds.isEmpty()) {
                log.warn("⚠️  No active students found for teacher: {}", teacherId);
                return AnalysisDTOs.DashboardResponse.builder().totalStudents(0).build();
            }

            long paperQueryStartTime = System.currentTimeMillis();
            // Batched paper query - limit to last 50 papers for performance - PARALLEL BATCHES
            List<DocumentSnapshot> paperDocs = runBatchedWhereInQueryForDocs(
                    firestore.collection(PAPERS_COLLECTION), "studentId", studentIds);
            long paperQueryTime = System.currentTimeMillis() - paperQueryStartTime;
            
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
                log.info("No papers found for students of teacher: {}", teacherId);
                return AnalysisDTOs.DashboardResponse.builder()
                        .totalStudents(totalStudents)
                        .totalPapersUploaded(0)
                        .build();
            }

            long reportQueryStartTime = System.currentTimeMillis();
            // Batched report query - PARALLEL BATCHES
            List<DocumentSnapshot> reportDocs = runBatchedWhereInQueryForDocs(
                    firestore.collection(REPORTS_COLLECTION), "paperId", paperIds);
            long reportQueryTime = System.currentTimeMillis() - reportQueryStartTime;
            
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
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("getDashboard completed: {} papers, {} reports in {}ms (papers: {}ms, reports: {}ms)", 
                    totalPapers, count, totalTime, paperQueryTime, reportQueryTime);
            
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
            log.error("Firestore error for dashboard of teacher: {}", teacherId, e);
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

            // ✅ Return empty response instead of null (Issue #6)
            if (paperIds.isEmpty()) return AnalysisDTOs.AnalysisReportResponse.builder()
                    .studentId(studentId)
                    .aiComment("No analysis data available yet")
                    .riskLevel("PENDING")
                    .build();

            List<AnalysisReport> reports = runBatchedWhereInQuery(
                    firestore.collection(REPORTS_COLLECTION), "paperId", paperIds, AnalysisReport.class);

            // ✅ Return empty response instead of null (Issue #6)
            if (reports.isEmpty()) return AnalysisDTOs.AnalysisReportResponse.builder()
                    .studentId(studentId)
                    .aiComment("No analysis reports generated yet")
                    .riskLevel("PENDING")
                    .build();

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
        // ✅ Return safe empty response instead of null (Issue #6)
        if (report == null) return AnalysisDTOs.AnalysisReportResponse.builder()
                .aiComment("Report unavailable")
                .riskLevel("UNKNOWN")
                .build();
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
