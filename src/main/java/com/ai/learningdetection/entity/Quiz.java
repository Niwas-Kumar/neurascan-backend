package com.ai.learningdetection.entity;

import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {
    private String id;
    private String teacherId;
    private String classId;
    private String topic;
    private Date createdAt;
    private List<QuizQuestion> questions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizQuestion {
        private String id;
        private String question;
        private List<String> options;
        private String answer;
    }
}
