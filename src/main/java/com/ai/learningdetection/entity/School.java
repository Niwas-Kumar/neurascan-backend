package com.ai.learningdetection.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IgnoreExtraProperties
public class School {

    private String id;
    private String name;
    private String code;        // Unique school code e.g. "SCH-DELHI-42X"
    private String address;

    @Builder.Default
    private boolean active = true;

    private String createdAt;
    private String updatedAt;
}
