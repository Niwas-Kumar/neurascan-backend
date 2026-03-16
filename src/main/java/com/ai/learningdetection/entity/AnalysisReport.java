package com.ai.learningdetection.entity;

import lombok.*;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisReport {

    private String id;
    private String paperId;
    private Double dyslexiaScore;
    private Double dysgraphiaScore;
    private String aiComment;
    private Date createdAt;
}

