package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.QuizDTOs;
import com.ai.learningdetection.entity.Quiz;
import com.ai.learningdetection.entity.QuizLink;
import com.ai.learningdetection.entity.Student;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Service for distributing quiz links via email.
 * Generates secure tokens and sends quiz invitations to students and parents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizDistributionService {

    private final Firestore firestore;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private static final String QUIZ_LINKS_COLLECTION = "quiz_links";
    private static final String QUIZZES_COLLECTION = "quizzes";
    private static final String STUDENTS_COLLECTION = "students";
    private static final String TEACHERS_COLLECTION = "teachers";
    private static final int TOKEN_LENGTH = 32;
    private static final int DEFAULT_DAYS_UNTIL_EXPIRY = 7;

    /**
     * Distribute quiz to students by sending them unique attempt links.
     */
    public List<QuizDTOs.QuizLinkResponse> distributeQuizToStudents(
            String quizId,
            List<String> studentIds,
            String teacherId,
            String customMessage,
            int validityDays) throws ExecutionException, InterruptedException {
        
        try {
            // Verify quiz belongs to teacher
            DocumentSnapshot quizSnap = firestore.collection(QUIZZES_COLLECTION)
                    .document(quizId).get().get();
            
            if (!quizSnap.exists()) {
                throw new RuntimeException("Quiz not found");
            }
            
            Quiz quiz = quizSnap.toObject(Quiz.class);
            if (!quiz.getTeacherId().equals(teacherId)) {
                throw new RuntimeException("Unauthorized: Quiz does not belong to this teacher");
            }
            
            List<QuizDTOs.QuizLinkResponse> links = new ArrayList<>();
            List<String> sentEmails = new ArrayList<>();

            // OPTIMIZED: Batch fetch all students at once instead of N+1
            List<DocumentReference> studentRefs = studentIds.stream()
                    .map(id -> firestore.collection(STUDENTS_COLLECTION).document(id))
                    .collect(Collectors.toList());
            List<DocumentSnapshot> studentSnaps = firestore.getAll(studentRefs.toArray(new DocumentReference[0])).get();
            
            // Collect parent UIDs for batch fetch
            Map<String, Student> studentMap = new HashMap<>();
            Set<String> parentUids = new HashSet<>();
            for (DocumentSnapshot snap : studentSnaps) {
                if (!snap.exists()) continue;
                Student student = snap.toObject(Student.class);
                studentMap.put(snap.getId(), student);
                if (student.getParentUid() != null && !student.getParentUid().isEmpty()) {
                    parentUids.add(student.getParentUid());
                }
            }

            // Batch fetch all parents at once
            Map<String, String> parentEmailMap = new HashMap<>();
            if (!parentUids.isEmpty()) {
                List<DocumentReference> parentRefs = parentUids.stream()
                        .map(uid -> firestore.collection("parents").document(uid))
                        .collect(Collectors.toList());
                List<DocumentSnapshot> parentSnaps = firestore.getAll(parentRefs.toArray(new DocumentReference[0])).get();
                for (DocumentSnapshot pSnap : parentSnaps) {
                    if (pSnap.exists()) {
                        parentEmailMap.put(pSnap.getId(), pSnap.getString("email"));
                    }
                }
            }

            // Now iterate with pre-fetched data (no more N+1)
            for (String studentId : studentIds) {
                try {
                    Student student = studentMap.get(studentId);
                    if (student == null) {
                        log.warn("Student not found: {}", studentId);
                        continue;
                    }

                    String parentEmail = student.getParentUid() != null 
                            ? parentEmailMap.get(student.getParentUid()) : null;

                    if (parentEmail == null || parentEmail.isEmpty()) {
                        log.warn("No parent email found for student: {}", studentId);
                        continue;
                    }

                    // Create quiz link
                    QuizDTOs.QuizLinkResponse linkResponse = createAndSendQuizLink(
                            quizId,
                            studentId,
                            parentEmail,
                            "STUDENT",
                            quiz.getTopic(),
                            customMessage,
                            teacherId,
                            quiz.getClassId(),
                            validityDays);

                    links.add(linkResponse);
                    sentEmails.add(parentEmail);

                    log.info("Quiz link sent for student: {} to parent: {}", student.getName(), parentEmail);
                    
                } catch (Exception e) {
                    log.error("Error sending quiz to student {}: {}", studentId, e.getMessage());
                }
            }
            
            // Update quiz with distribution info
            updateQuizDistributionStatus(quizId, sentEmails);
            
            log.info("✅ Quiz distributed to {} students", links.size());
            return links;
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("❌ Error distributing quiz: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Distribute quiz to parents via email.
     */
    public List<QuizDTOs.QuizLinkResponse> distributeQuizToParents(
            String quizId,
            List<String> parentEmails,
            String teacherId,
            String customMessage,
            int validityDays) throws ExecutionException, InterruptedException {
        
        try {
            // Verify quiz belongs to teacher
            DocumentSnapshot quizSnap = firestore.collection(QUIZZES_COLLECTION)
                    .document(quizId).get().get();
            
            if (!quizSnap.exists()) {
                throw new RuntimeException("Quiz not found");
            }
            
            Quiz quiz = quizSnap.toObject(Quiz.class);
            if (!quiz.getTeacherId().equals(teacherId)) {
                throw new RuntimeException("Unauthorized: Quiz does not belong to this teacher");
            }
            
            List<QuizDTOs.QuizLinkResponse> links = new ArrayList<>();
            
            // Create links for each parent email
            for (String parentEmail : parentEmails) {
                try {
                    QuizDTOs.QuizLinkResponse linkResponse = createAndSendQuizLink(
                            quizId,
                            parentEmail,  // Use email as ID for parents
                            parentEmail,
                            "PARENT",
                            quiz.getTopic(),
                            customMessage,
                            teacherId,
                            quiz.getClassId(),
                            validityDays);
                    
                    links.add(linkResponse);
                    log.info("📧 Quiz link sent to parent: {}", parentEmail);
                    
                } catch (Exception e) {
                    log.error("❌ Error sending quiz to parent {}: {}", parentEmail, e.getMessage());
                }
            }
            
            log.info("✅ Quiz distributed to {} parents", links.size());
            return links;
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("❌ Error distributing quiz to parents: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Create a quiz link and send email with unique token.
     */
    private QuizDTOs.QuizLinkResponse createAndSendQuizLink(
            String quizId,
            String recipientId,
            String recipientEmail,
            String recipientType,
            String quizTopic,
            String customMessage,
            String teacherId,
            String classId,
            int validityDays) throws ExecutionException, InterruptedException {
        
        // Generate secure token
        String token = generateSecureToken();
        
        // Calculate expiry date using teacher-specified validity
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, validityDays);
        Date expiryDate = calendar.getTime();
        
        // Create QuizLink entity
        DocumentReference linkRef = firestore.collection(QUIZ_LINKS_COLLECTION).document();
        
        QuizLink link = QuizLink.builder()
                .id(linkRef.getId())
                .quizId(quizId)
                .token(token)
                .recipientId(recipientId)
                .recipientEmail(recipientEmail)
                .recipientType(recipientType)
                .createdAt(new Date())
                .expiredAt(expiryDate)
                .isExpired(false)
                .accessCount(0)
                .teacherId(teacherId)
                .classId(classId)
                .build();
        
        linkRef.set(link).get();
        
        // Generate quiz attempt URL
        String attemptUrl = generateQuizAttemptUrl(quizId, token);
        
        // Send email
        sendQuizInvitationEmail(recipientEmail, quizTopic, attemptUrl, recipientType, customMessage);
        
        // Mark as sent
        link.setSentAt(new Date());
        linkRef.set(link).get();
        
        return QuizDTOs.QuizLinkResponse.builder()
                .token(token)
                .attemptUrl(attemptUrl)
                .recipientEmail(recipientEmail)
                .recipientType(recipientType)
                .createdAt(new Date())
                .expiresAt(expiryDate)
                .build();
    }

    /**
     * Send quiz invitation email with embedded link.
     */
    private void sendQuizInvitationEmail(
            String recipientEmail,
            String quizTopic,
            String attemptUrl,
            String recipientType,
            String customMessage) {

        try {
            String recipientLabel = "PARENT".equals(recipientType) ? "your child" : "Student";
            String teacherName = "Your Teacher"; // Could be fetched from teacher entity

            // Use EmailService to send via SendGrid
            boolean sent = emailService.sendQuizInvitationEmail(
                    recipientEmail,
                    recipientLabel,
                    quizTopic,
                    attemptUrl,
                    teacherName,
                    customMessage);

            if (sent) {
                log.info("📧 Quiz invitation email sent to: {}", recipientEmail);
            } else {
                log.warn("Quiz invitation email NOT sent to: {} (check SendGrid config). Quiz link: {}", recipientEmail, attemptUrl);
            }

        } catch (Exception e) {
            log.error("❌ Error sending quiz email: {}", e.getMessage());
        }
    }

    /**
     * Generate HTML email template for quiz invitation.
     */
    private String generateQuizEmailHtml(
            String recipientLabel,
            String quizTopic,
            String attemptUrl,
            String customMessage) {
        
        String messageBlock = customMessage != null && !customMessage.isEmpty()
                ? "<p style='background: #f0f8ff; padding: 15px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #1a73e8;'><strong>Message from your teacher:</strong><br>" + customMessage + "</p>"
                : "";
        
        return "<!DOCTYPE html>\n" +
                "<html><head><meta charset='UTF-8'><style>\n" +
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }\n" +
                ".container { max-width: 600px; margin: 40px auto; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); overflow: hidden; }\n" +
                ".header { background: linear-gradient(135deg, #1a73e8 0%, #8b5cf6 100%); color: white; padding: 40px 20px; text-align: center; }\n" +
                ".header h1 { margin: 0; font-size: 28px; font-weight: 700; }\n" +
                ".header p { margin: 10px 0 0 0; opacity: 0.9; }\n" +
                ".content { padding: 40px 30px; }\n" +
                ".quiz-box { background: #f8f9fa; border: 2px solid #e8eaed; border-radius: 8px; padding: 24px; text-align: center; margin: 30px 0; }\n" +
                ".quiz-topic { font-size: 24px; font-weight: 700; color: #1a73e8; margin: 10px 0; }\n" +
                ".quiz-instructions { color: #666; margin: 15px 0; font-size: 14px; }\n" +
                ".button-container { text-align: center; margin: 30px 0; }\n" +
                ".button { display: inline-block; background: linear-gradient(135deg, #1a73e8 0%, #8b5cf6 100%); color: white; padding: 14px 32px; border-radius: 6px; text-decoration: none; font-weight: 700; font-size: 16px; }\n" +
                ".button:hover { opacity: 0.95; }\n" +
                ".info-box { background: #e8f0fe; border-left: 4px solid #1a73e8; padding: 16px; margin: 20px 0; border-radius: 4px; }\n" +
                ".info-box p { margin: 0; font-size: 13px; color: #1a73e8; }\n" +
                ".footer { background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e8eaed; font-size: 12px; color: #80868b; }\n" +
                "</style></head><body>\n" +
                "<div class='container'>\n" +
                "  <div class='header'><h1>🧠 NeuraScan</h1><p>Take Your Quiz</p></div>\n" +
                "  <div class='content'>\n" +
                "    <p>Hello " + recipientLabel + ",</p>\n" +
                "    <p>Your teacher has shared a quiz with you. Test your knowledge and see how well you understand the material!</p>\n" +
                "    \n" +
                "    <div class='quiz-box'>\n" +
                "      <div style='font-size: 14px; color: #666;'>Quiz</div>\n" +
                "      <div class='quiz-topic'>" + quizTopic + "</div>\n" +
                "      <div class='quiz-instructions'>Multiple Choice Questions • Score & Feedback</div>\n" +
                "    </div>\n" +
                messageBlock +
                "    <div class='button-container'>\n" +
                "      <a href='" + attemptUrl + "' class='button'>Start Quiz Now →</a>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class='info-box'>\n" +
                "      <p><strong>📌 Tips:</strong> Make sure to read each question carefully. You can answer questions at your own pace.</p>\n" +
                "    </div>\n" +
                "    \n" +
                "    <p style='color: #666; font-size: 14px;'>This link will expire in 30 days. If you have any questions, contact your teacher.</p>\n" +
                "  </div>\n" +
                "  <div class='footer'><p>© 2026 NeuraScan. All rights reserved.</p></div>\n" +
                "</div>\n" +
                "</body></html>";
    }

    /**
     * Generate quiz attempt URL with token.
     */
    private String generateQuizAttemptUrl(String quizId, String token) {
        return frontendUrl + "/quiz-attempt?quizId=" + quizId + "&token=" + token;
    }

    /**
     * Generate a cryptographically secure random token.
     */
    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        random.nextBytes(tokenBytes);
        return bytesToHex(tokenBytes);
    }

    /**
     * Convert bytes to hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Update quiz with distribution status.
     */
    private void updateQuizDistributionStatus(String quizId, List<String> sentEmails) {
        try {
            DocumentSnapshot quizSnap = firestore.collection(QUIZZES_COLLECTION)
                    .document(quizId).get().get();
            
            if (quizSnap.exists()) {
                Quiz quiz = quizSnap.toObject(Quiz.class);
                
                if (quiz.getDistributedToEmail() == null) {
                    quiz.setDistributedToEmail(new ArrayList<>());
                }
                quiz.getDistributedToEmail().addAll(sentEmails);
                quiz.setTotalDistributed(quiz.getTotalDistributed() + sentEmails.size());
                quiz.setDistributedAt(new Date());
                
                firestore.collection(QUIZZES_COLLECTION)
                        .document(quizId)
                        .set(quiz)
                        .get();
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not update quiz distribution status: {}", e.getMessage());
        }
    }

    /**
     * Get quiz progress/analytics for a quiz.
     */
    public QuizDTOs.QuizProgressResponse getQuizProgress(String quizId, String teacherId) 
            throws ExecutionException, InterruptedException {
        
        try {
            // Get quiz
            DocumentSnapshot quizSnap = firestore.collection(QUIZZES_COLLECTION)
                    .document(quizId).get().get();
            
            if (!quizSnap.exists()) {
                throw new RuntimeException("Quiz not found");
            }
            
            Quiz quiz = quizSnap.toObject(Quiz.class);
            
            if (!quiz.getTeacherId().equals(teacherId)) {
                throw new RuntimeException("Unauthorized");
            }
            
            // Get all attempts for this quiz
            QuerySnapshot attemptsSnapshot = firestore.collection("quiz_attempts")
                    .whereEqualTo("quizId", quizId)
                    .get().get();
            
            List<QuizDTOs.StudentQuizProgress> studentProgress = new ArrayList<>();
            
            for (var doc : attemptsSnapshot.getDocuments()) {
                var attempt = doc.toObject(com.ai.learningdetection.entity.QuizAttempt.class);
                
                // Get student info if student attempt
                String studentName = "Unknown";
                if (attempt.getStudentId() != null) {
                    try {
                        DocumentSnapshot studentSnap = firestore.collection(STUDENTS_COLLECTION)
                                .document(attempt.getStudentId()).get().get();
                        if (studentSnap.exists()) {
                            Student student = studentSnap.toObject(Student.class);
                            studentName = student.getName();
                        }
                    } catch (Exception e) {
                        log.warn("Could not fetch student info");
                    }
                }
                
                studentProgress.add(QuizDTOs.StudentQuizProgress.builder()
                        .studentId(attempt.getStudentId())
                        .studentName(studentName)
                        .attemptDate(attempt.getCompletedAt() != null ? attempt.getCompletedAt() : attempt.getStartedAt())
                        .score(attempt.getScore())
                        .timeSpentMs(attempt.getTotalTimeSpentMs())
                        .completed(attempt.isCompleted())
                        .learningGap(attempt.getLearningGapSummary())
                        .build());
            }
            
            return QuizDTOs.QuizProgressResponse.builder()
                    .quizId(quizId)
                    .topic(quiz.getTopic())
                    .totalAttempts(quiz.getTotalAttempts())
                    .averageScore(quiz.getAverageScore())
                    .participationRate(calculateParticipationRate(quiz, attemptsSnapshot.size()))
                    .studentProgress(studentProgress)
                    .build();
                    
        } catch (ExecutionException | InterruptedException e) {
            log.error("❌ Error fetching quiz progress: {}", e.getMessage());
            throw e;
        }
    }

    private int calculateParticipationRate(Quiz quiz, int attemptCount) {
        if (quiz.getTotalDistributed() == 0) {
            return 0;
        }
        return (int) ((attemptCount * 100.0) / quiz.getTotalDistributed());
    }
}
