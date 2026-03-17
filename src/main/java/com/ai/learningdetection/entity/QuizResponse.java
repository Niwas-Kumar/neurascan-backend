package com.ai.learningdetection.entity;

import lombok.*;

import java.util.Date;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizResponse {
    private String id;
    private String quizId;
    private String studentId;
    private String classId;
    private Map<String, String> answers;
    private int score;
    private Date submittedAt;
}
