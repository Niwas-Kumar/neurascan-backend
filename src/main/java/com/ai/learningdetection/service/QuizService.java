package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.QuizDTOs;
import com.ai.learningdetection.entity.Quiz;
import com.ai.learningdetection.entity.QuizResponse;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.ai.learningdetection.exception.UnauthorizedAccessException;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    private final Firestore firestore;
    private final AiIntegrationService aiIntegrationService;

    private static final String QUIZZES_COLLECTION = "quizzes";
    private static final String QUIZ_RESPONSES_COLLECTION = "quiz_responses";

    public QuizDTOs.QuizDetail createQuiz(String teacherId, QuizDTOs.QuizGenerationRequest request) {
        try {
            DocumentReference quizRef = firestore.collection(QUIZZES_COLLECTION).document();
            List<Quiz.QuizQuestion> questions = new ArrayList<>();

            // Generate content via AI service if possible
            Map<String, Object> generated = aiIntegrationService.generateQuizFromText(request.getTopic(), request.getText(), request.getQuestionCount());
            List<Map<String, Object>> questionMaps = (List<Map<String, Object>>) generated.getOrDefault("questions", new ArrayList<>());

            if (questionMaps.isEmpty()) {
                // Fallback to server-side simple generation if external service returns none
                questions = buildFallbackQuestions(request.getTopic(), request.getText(), request.getQuestionCount());
            } else {
                for (Map<String, Object> q : questionMaps) {
                    questions.add(Quiz.QuizQuestion.builder()
                            .id(UUID.randomUUID().toString())
                            .question((String) q.getOrDefault("question", ""))
                            .options((List<String>) q.getOrDefault("options", new ArrayList<>()))
                            .answer((String) q.getOrDefault("answer", ""))
                            .build());
                }
            }

            Quiz quiz = Quiz.builder()
                    .id(quizRef.getId())
                    .teacherId(teacherId)
                    .classId(request.getClassId())
                    .topic(request.getTopic())
                    .createdAt(new Date())
                    .questions(questions)
                    .build();

            quizRef.set(quiz).get();

            return QuizDTOs.QuizDetail.builder()
                    .id(quiz.getId())
                    .teacherId(quiz.getTeacherId())
                    .classId(quiz.getClassId())
                    .topic(quiz.getTopic())
                    .createdAt(quiz.getCreatedAt().toString())
                    .questions(questions.stream().map(q->QuizDTOs.QuizQuestion.builder()
                            .id(q.getId())
                            .question(q.getQuestion())
                            .options(q.getOptions())
                            .answer(q.getAnswer())
                            .build()).collect(Collectors.toList()))
                    .build();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to create quiz", e);
        }
    }

    private List<Quiz.QuizQuestion> buildFallbackQuestions(String topic, String text, int questionCount) {
        List<Quiz.QuizQuestion> questions = new ArrayList<>();
        if (text == null || text.isBlank()) {
            // Default question set
            for (int i = 1; i <= questionCount; i++) {
                questions.add(Quiz.QuizQuestion.builder()
                        .id(UUID.randomUUID().toString())
                        .question("What is the main goal of this lesson?")
                        .options(List.of("Understanding","Memorizing","Foundational","Optional"))
                        .answer("Understanding")
                        .build());
            }
            return questions;
        }

        String[] sentences = text.split("[\\.\\?\\!]\\s*");
        for (int i = 0; i < Math.min(questionCount, sentences.length); i++) {
            String sentence = sentences[i].trim();
            if (sentence.isEmpty()) continue;

            String[] words = sentence.split("\\s+");
            if (words.length < 4) continue;

            int missingIndex = Math.min(1, words.length - 1);
            String correct = words[missingIndex];
            String questionText = sentence.replaceFirst(correct, "____");

            questions.add(Quiz.QuizQuestion.builder()
                    .id(UUID.randomUUID().toString())
                    .question(questionText)
                    .options(List.of(correct, "optionA", "optionB", "optionC"))
                    .answer(correct)
                    .build());
        }

        if (questions.isEmpty()) {
            questions.add(Quiz.QuizQuestion.builder()
                    .id(UUID.randomUUID().toString())
                    .question("What is the main topic of this material?")
                    .options(List.of(topic, "General","Language","Math"))
                    .answer(topic)
                    .build());
        }

        return questions;
    }

    public List<QuizDTOs.QuizDetail> getQuizzesByTeacher(String teacherId) {
        try {
            QuerySnapshot snapshot = firestore.collection(QUIZZES_COLLECTION)
                    .whereEqualTo("teacherId", teacherId)
                    .get().get();

            return snapshot.getDocuments().stream().map(d -> {
                Quiz q = d.toObject(Quiz.class);
                return QuizDTOs.QuizDetail.builder()
                        .id(q.getId())
                        .teacherId(q.getTeacherId())
                        .classId(q.getClassId())
                        .topic(q.getTopic())
                        .createdAt(q.getCreatedAt().toString())
                        .questions(q.getQuestions().stream().map(qu -> QuizDTOs.QuizQuestion.builder()
                                .id(qu.getId())
                                .question(qu.getQuestion())
                                .options(qu.getOptions())
                                .answer(qu.getAnswer())
                                .build()).collect(Collectors.toList()))
                        .build();
            }).collect(Collectors.toList());

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch quizzes", e);
        }
    }

    public QuizDTOs.QuizDetail getQuizById(String quizId, String teacherId) {
        try {
            DocumentSnapshot snapshot = firestore.collection(QUIZZES_COLLECTION).document(quizId).get().get();
            if (!snapshot.exists()) {
                throw new ResourceNotFoundException("Quiz", "id", quizId);
            }
            Quiz q = snapshot.toObject(Quiz.class);
            if (!q.getTeacherId().equals(teacherId)) {
                throw new UnauthorizedAccessException("Not authorized to view this quiz");
            }

            return QuizDTOs.QuizDetail.builder()
                    .id(q.getId())
                    .teacherId(q.getTeacherId())
                    .classId(q.getClassId())
                    .topic(q.getTopic())
                    .createdAt(q.getCreatedAt().toString())
                    .questions(q.getQuestions().stream().map(qu -> QuizDTOs.QuizQuestion.builder()
                            .id(qu.getId())
                            .question(qu.getQuestion())
                            .options(qu.getOptions())
                            .answer(qu.getAnswer())
                            .build()).collect(Collectors.toList()))
                    .build();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to get quiz", e);
        }
    }

    public List<QuizResponse> getQuizResponsesForStudent(String studentId, String requesterId, String requesterRole) {
        try {
            if ("PARENT".equalsIgnoreCase(requesterRole) || "ROLE_PARENT".equalsIgnoreCase(requesterRole)) {
                DocumentSnapshot parentDoc = firestore.collection("parents").document(requesterId).get().get();
                if (!parentDoc.exists()) {
                    throw new UnauthorizedAccessException("Parent not found");
                }
                String linkedStudent = parentDoc.getString("studentId");
                if (!studentId.equals(linkedStudent)) {
                    throw new UnauthorizedAccessException("Not authorized to access student quiz data");
                }
            } else if ("TEACHER".equals(requesterRole)) {
                DocumentSnapshot studentDoc = firestore.collection("students").document(studentId).get().get();
                if (!studentDoc.exists() || !requesterId.equals(studentDoc.getString("teacherId"))) {
                    throw new UnauthorizedAccessException("Not authorized to access this student");
                }
            } else {
                throw new UnauthorizedAccessException("Not authorized");
            }

            QuerySnapshot responses = firestore.collection(QUIZ_RESPONSES_COLLECTION)
                    .whereEqualTo("studentId", studentId)
                    .get().get();

            return responses.getDocuments().stream().map(d -> d.toObject(QuizResponse.class)).collect(Collectors.toList());

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch quiz responses", e);
        }
    }

    public QuizResponse submitQuizResponse(QuizDTOs.QuizSubmissionRequest request) {
        try {
            DocumentSnapshot quizSnapshot = firestore.collection(QUIZZES_COLLECTION)
                    .document(request.getQuizId()).get().get();
            if (!quizSnapshot.exists()) {
                throw new ResourceNotFoundException("Quiz", "id", request.getQuizId());
            }
            Quiz quiz = quizSnapshot.toObject(Quiz.class);

            int correct = 0;
            for (Quiz.QuizQuestion question : quiz.getQuestions()) {
                String answer = request.getAnswers().getOrDefault(question.getId(), "");
                if (question.getAnswer().equalsIgnoreCase(answer.trim())) {
                    correct++;
                }
            }

            int score = (int) Math.round((double) correct / quiz.getQuestions().size() * 100);

            DocumentReference responseRef = firestore.collection(QUIZ_RESPONSES_COLLECTION).document();
            QuizResponse resp = QuizResponse.builder()
                    .id(responseRef.getId())
                    .quizId(request.getQuizId())
                    .studentId(request.getStudentId())
                    .classId(request.getClassId())
                    .answers(request.getAnswers())
                    .score(score)
                    .submittedAt(new Date())
                    .build();
            responseRef.set(resp).get();

            return resp;

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to submit quiz", e);
        }
    }

    public List<QuizResponse> getQuizResponses(String quizId, String teacherId) {
        try {
            // confirm teacher owns quiz
            DocumentSnapshot quizSnapshot = firestore.collection(QUIZZES_COLLECTION).document(quizId).get().get();
            if (!quizSnapshot.exists()) {
                throw new ResourceNotFoundException("Quiz", "id", quizId);
            }
            Quiz quiz = quizSnapshot.toObject(Quiz.class);
            if (!quiz.getTeacherId().equals(teacherId)) {
                throw new UnauthorizedAccessException("Not authorized to fetch responses for this quiz");
            }

            QuerySnapshot responses = firestore.collection(QUIZ_RESPONSES_COLLECTION)
                    .whereEqualTo("quizId", quizId).get().get();

            return responses.getDocuments().stream().map(d -> d.toObject(QuizResponse.class)).collect(Collectors.toList());

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch quiz responses", e);
        }
    }
}