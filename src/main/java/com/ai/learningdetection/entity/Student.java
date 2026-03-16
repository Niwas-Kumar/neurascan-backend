package com.ai.learningdetection.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    private String id;
    private String name;
    private String className;
    private Integer age;
    private String teacherId;
}

