package com.ai.learningdetection.entity;

import lombok.*;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestPaper {

    private String id;
    private String studentId;
    private String filePath;
    private String originalFileName;
    private Date uploadDate;
}

