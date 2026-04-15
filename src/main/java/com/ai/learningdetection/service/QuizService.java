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
    private static final String STUDENTS_COLLECTION = "students";

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
                            .category((String) q.getOrDefault("category", ""))
                            .screeningTarget((String) q.getOrDefault("screeningTarget", ""))
                            .difficulty((String) q.getOrDefault("difficulty", "medium"))
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
                            .category(q.getCategory())
                            .screeningTarget(q.getScreeningTarget())
                            .difficulty(q.getDifficulty())
                            .build()).collect(Collectors.toList()))
                    .build();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to create quiz", e);
        }
    }

    private List<Quiz.QuizQuestion> buildFallbackQuestions(String topic, String text, int questionCount) {
        // Screening question bank for dyslexia/dysgraphia detection
        List<Quiz.QuizQuestion> bank = buildScreeningQuestionBank();
        Collections.shuffle(bank);
        return bank.stream().limit(questionCount).collect(Collectors.toList());
    }

    private List<Quiz.QuizQuestion> buildScreeningQuestionBank() {
        List<Quiz.QuizQuestion> bank = new ArrayList<>();

        // ── Letter Discrimination (Dyslexia — reversal detection) ──
        bank.add(screeningQ("Which word is spelled correctly?",
                List.of("dog", "bog", "dob", "gob"), "dog",
                "letter_discrimination", "b/d reversal awareness", "easy"));
        bank.add(screeningQ("Select the correct spelling of the word that means 'a round object used in games':",
                List.of("ball", "dall", "boll", "pall"), "ball",
                "letter_discrimination", "b/d/p reversal awareness", "easy"));
        bank.add(screeningQ("Which of these is a real word?",
                List.of("bed", "ded", "beb", "deb"), "bed",
                "letter_discrimination", "b/d discrimination", "easy"));
        bank.add(screeningQ("Choose the word that means 'the opposite of good':",
                List.of("bad", "dad", "pad", "dab"), "bad",
                "letter_discrimination", "b/d/p reversal detection", "easy"));
        bank.add(screeningQ("Which spelling is correct for a place where you sleep?",
                List.of("bedroom", "dedroom", "bedloom", "pedroom"), "bedroom",
                "letter_discrimination", "b/d reversal in longer words", "medium"));
        bank.add(screeningQ("Identify the correctly spelled word meaning 'a young dog':",
                List.of("puppy", "buppy", "dubby", "puffy"), "puppy",
                "letter_discrimination", "p/b discrimination", "medium"));

        // ── Phoneme Awareness (Dyslexia — sound processing) ──
        bank.add(screeningQ("Which word rhymes with 'cat'?",
                List.of("hat", "cup", "dog", "run"), "hat",
                "phoneme_awareness", "rhyme recognition", "easy"));
        bank.add(screeningQ("Which word starts with the same sound as 'fish'?",
                List.of("fun", "sun", "bun", "gum"), "fun",
                "phoneme_awareness", "initial phoneme matching", "easy"));
        bank.add(screeningQ("How many syllables are in the word 'butterfly'?",
                List.of("3", "2", "4", "1"), "3",
                "phoneme_awareness", "syllable segmentation", "medium"));
        bank.add(screeningQ("Which word does NOT rhyme with 'make'?",
                List.of("milk", "cake", "take", "lake"), "milk",
                "phoneme_awareness", "rhyme discrimination", "medium"));
        bank.add(screeningQ("If you remove the first sound from 'stop', what word do you get?",
                List.of("top", "sop", "pop", "pot"), "top",
                "phoneme_awareness", "phoneme deletion", "medium"));
        bank.add(screeningQ("Blend these sounds together: /k/ /a/ /t/. What word does it make?",
                List.of("cat", "cut", "kit", "coat"), "cat",
                "phoneme_awareness", "phoneme blending", "easy"));

        // ── Spelling Patterns (Dyslexia — orthographic processing) ──
        bank.add(screeningQ("Which is the correct spelling?",
                List.of("because", "becuase", "becouse", "becasue"), "because",
                "spelling_patterns", "common letter transposition", "medium"));
        bank.add(screeningQ("Choose the correct spelling of the word meaning 'adequate':",
                List.of("enough", "enugh", "enogh", "enouph"), "enough",
                "spelling_patterns", "irregular spelling recognition", "medium"));
        bank.add(screeningQ("Select the correctly spelled word:",
                List.of("friend", "freind", "frend", "freand"), "friend",
                "spelling_patterns", "ie/ei pattern confusion", "medium"));
        bank.add(screeningQ("Which word is spelled correctly?",
                List.of("said", "sed", "siad", "sayd"), "said",
                "spelling_patterns", "sight word accuracy", "easy"));
        bank.add(screeningQ("Choose the correct spelling for the word meaning 'not the same':",
                List.of("different", "diffrent", "diferent", "differant"), "different",
                "spelling_patterns", "syllable omission detection", "medium"));
        bank.add(screeningQ("Choose the correct spelling for the word meaning 'very pretty':",
                List.of("beautiful", "beatiful", "beutiful", "beautful"), "beautiful",
                "spelling_patterns", "complex vowel sequence", "hard"));

        // ── Visual-Spatial (Dysgraphia — spatial awareness) ──
        bank.add(screeningQ("Which sequence continues the pattern: 1, 3, 5, 7, ___?",
                List.of("9", "8", "10", "6"), "9",
                "visual_spatial", "number pattern recognition", "easy"));
        bank.add(screeningQ("Which group of letters is in correct alphabetical order?",
                List.of("a, b, c, d", "a, c, b, d", "b, a, c, d", "a, b, d, c"), "a, b, c, d",
                "visual_spatial", "alphabetical sequencing", "easy"));
        bank.add(screeningQ("Which word has all its letters in the correct left-to-right order?",
                List.of("draw", "darw", "dwar", "dwra"), "draw",
                "visual_spatial", "letter ordering accuracy", "easy"));
        bank.add(screeningQ("What comes next in the pattern: circle, square, circle, square, ___?",
                List.of("circle", "triangle", "square", "star"), "circle",
                "visual_spatial", "visual pattern continuation", "easy"));
        bank.add(screeningQ("Put these steps in the correct order: 1) Write your answer 2) Read the question 3) Check your work",
                List.of("2, 1, 3", "1, 2, 3", "3, 2, 1", "2, 3, 1"), "2, 1, 3",
                "visual_spatial", "sequential task ordering", "medium"));
        bank.add(screeningQ("Which number sequence is in the correct order?",
                List.of("12, 13, 14, 15", "12, 14, 13, 15", "15, 14, 13, 12", "12, 31, 14, 51"), "12, 13, 14, 15",
                "visual_spatial", "number reversal detection", "medium"));

        // ── Reading Comprehension (Both — processing + retention) ──
        bank.add(screeningQ("'The cat sat on the mat.' What is the cat doing?",
                List.of("Sitting", "Running", "Sleeping", "Eating"), "Sitting",
                "reading_comprehension", "simple sentence comprehension", "easy"));
        bank.add(screeningQ("'Before you eat, wash your hands.' What should you do first?",
                List.of("Wash your hands", "Eat", "Sit down", "Set the table"), "Wash your hands",
                "reading_comprehension", "temporal sequence understanding", "easy"));
        bank.add(screeningQ("'The boy was sad because he lost his toy.' Why was the boy sad?",
                List.of("He lost his toy", "He was tired", "He was hungry", "He was sick"), "He lost his toy",
                "reading_comprehension", "cause-effect relationship", "easy"));
        bank.add(screeningQ("Read: 'Tom has 3 apples. He gives 1 to Sam.' How many apples does Tom have now?",
                List.of("2", "3", "1", "4"), "2",
                "reading_comprehension", "comprehension with numbers", "easy"));
        bank.add(screeningQ("'Even though it was raining, Sarah walked to school without an umbrella.' What can you conclude?",
                List.of("She got wet", "She stayed dry", "She stayed home", "She drove to school"), "She got wet",
                "reading_comprehension", "inferential comprehension", "medium"));
        bank.add(screeningQ("Choose the best title: 'Bees make honey. They live in hives. Bees help flowers grow by carrying pollen.'",
                List.of("All About Bees", "Flowers and Gardens", "Making Food", "Animals in the Wild"), "All About Bees",
                "reading_comprehension", "main idea identification", "medium"));

        // ── Writing Mechanics (Dysgraphia — grammar/punctuation) ──
        bank.add(screeningQ("Which sentence uses correct capitalization?",
                List.of("My name is John.", "my name is john.", "My Name Is John.", "my Name is John."), "My name is John.",
                "writing_mechanics", "capitalization rules", "easy"));
        bank.add(screeningQ("Which sentence has correct punctuation?",
                List.of("Where are you going?", "Where are you going.", "where are you going?", "Where are you going"), "Where are you going?",
                "writing_mechanics", "question mark usage", "easy"));
        bank.add(screeningQ("Choose the sentence with proper spacing between words:",
                List.of("The dog is big.", "Thedog is big.", "The dogis big.", "The dog isbig."), "The dog is big.",
                "writing_mechanics", "word boundary awareness", "easy"));
        bank.add(screeningQ("Which sentence is grammatically correct?",
                List.of("She goes to school every day.", "She go to school every day.", "She going to school every day.", "She goed to school every day."), "She goes to school every day.",
                "writing_mechanics", "subject-verb agreement", "medium"));
        bank.add(screeningQ("Choose the correct plural form of 'child':",
                List.of("children", "childs", "childrens", "childes"), "children",
                "writing_mechanics", "irregular plural knowledge", "medium"));
        bank.add(screeningQ("Which is the correct contraction of 'do not'?",
                List.of("don't", "dont", "do'nt", "don,t"), "don't",
                "writing_mechanics", "contraction formation", "easy"));

        return bank;
    }

    private Quiz.QuizQuestion screeningQ(String question, List<String> options, String answer,
                                          String category, String screeningTarget, String difficulty) {
        List<String> shuffled = new ArrayList<>(options);
        Collections.shuffle(shuffled);
        return Quiz.QuizQuestion.builder()
                .id(UUID.randomUUID().toString())
                .question(question)
                .options(shuffled)
                .answer(answer)
                .category(category)
                .screeningTarget(screeningTarget)
                .difficulty(difficulty)
                .build();
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
                                .category(qu.getCategory())
                                .screeningTarget(qu.getScreeningTarget())
                                .difficulty(qu.getDifficulty())
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
                            .category(qu.getCategory())
                            .screeningTarget(qu.getScreeningTarget())
                            .difficulty(qu.getDifficulty())
                            .build()).collect(Collectors.toList()))
                    .build();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to get quiz", e);
        }
    }

    public List<QuizResponse> getQuizResponsesForStudent(String studentId, String requesterId, String requesterRole) {
        try {
            String normalizedRole = normalizeRole(requesterRole);

            if ("PARENT".equals(normalizedRole)) {
                // Check via new parent-student relationship system
                QuerySnapshot relationshipQuery = firestore.collection("parent_student_relationships")
                        .whereEqualTo("parentId", requesterId)
                        .whereEqualTo("studentId", studentId)
                        .whereEqualTo("verificationStatus", "VERIFIED")
                        .limit(1)
                        .get().get();

                boolean hasRelationship = false;
                if (!relationshipQuery.isEmpty()) {
                    DocumentSnapshot rel = relationshipQuery.getDocuments().get(0);
                    hasRelationship = rel.getString("disconnectedAt") == null;
                }

                if (!hasRelationship) {
                    throw new UnauthorizedAccessException("You do not have permission to view this student's data. Please connect using the verified parent-student flow.");
                }
            } else if ("TEACHER".equals(normalizedRole)) {
                DocumentSnapshot studentDoc = firestore.collection(STUDENTS_COLLECTION).document(studentId).get().get();
                if (!studentDoc.exists() || !requesterId.equals(studentDoc.getString("teacherId"))) {
                    throw new UnauthorizedAccessException("Not authorized to access this student");
                }
            } else {
                throw new UnauthorizedAccessException("Not authorized");
            }

            QuerySnapshot responses = firestore.collection(QUIZ_RESPONSES_COLLECTION)
                    .whereEqualTo("studentId", studentId)
                    .get().get();
            
            System.out.println("[QUIZ_SUCCESS] Fetched " + responses.size() + " quiz responses for student: " + studentId);
            return responses.getDocuments().stream().map(d -> d.toObject(QuizResponse.class)).collect(Collectors.toList());

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch quiz responses", e);
        }
    }

    public QuizResponse submitQuizResponse(QuizDTOs.QuizSubmissionRequest request, String teacherId) {
        try {
            DocumentSnapshot quizSnapshot = firestore.collection(QUIZZES_COLLECTION)
                    .document(request.getQuizId()).get().get();
            if (!quizSnapshot.exists()) {
                throw new ResourceNotFoundException("Quiz", "id", request.getQuizId());
            }
            Quiz quiz = quizSnapshot.toObject(Quiz.class);

            if (quiz == null || !teacherId.equals(quiz.getTeacherId())) {
                throw new UnauthorizedAccessException("Not authorized to submit responses for this quiz");
            }

            if (request.getStudentId() != null && !request.getStudentId().isBlank()) {
                DocumentSnapshot studentSnapshot = firestore.collection(STUDENTS_COLLECTION)
                        .document(request.getStudentId())
                        .get().get();

                if (!studentSnapshot.exists() || !teacherId.equals(studentSnapshot.getString("teacherId"))) {
                    throw new UnauthorizedAccessException("Student does not belong to this teacher");
                }
            }

            if (quiz.getClassId() != null && request.getClassId() != null
                    && !quiz.getClassId().equals(request.getClassId())) {
                throw new UnauthorizedAccessException("Class mismatch for quiz submission");
            }

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

    private String normalizeRole(String requesterRole) {
        if (requesterRole == null || requesterRole.isBlank()) {
            return "";
        }
        return requesterRole.startsWith("ROLE_")
                ? requesterRole.substring("ROLE_".length()).toUpperCase(Locale.ROOT)
                : requesterRole.toUpperCase(Locale.ROOT);
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