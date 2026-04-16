package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.Student;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final Firestore firestore;
    private static final String STUDENTS_COLLECTION = "students";

    private static final Set<String> REQUIRED_HEADERS = Set.of("name", "rollnumber", "classname");
    private static final int MAX_ROWS = 500;

    public Map<String, Object> importStudents(MultipartFile file, String teacherId, String schoolId) {
        List<Map<String, String>> errors = new ArrayList<>();
        List<String> createdIds = new ArrayList<>();
        int rowNum = 0;

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] headers = reader.readNext();
            if (headers == null) {
                return Map.of("success", false, "message", "Empty CSV file");
            }

            // Normalize headers
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerIndex.put(headers[i].trim().toLowerCase().replaceAll("[^a-z]", ""), i);
            }

            // Validate required headers
            for (String req : REQUIRED_HEADERS) {
                if (!headerIndex.containsKey(req)) {
                    return Map.of("success", false, "message", "Missing required column: " + req);
                }
            }

            String[] row;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                if (rowNum > MAX_ROWS) {
                    errors.add(Map.of("row", String.valueOf(rowNum), "error", "Exceeded max " + MAX_ROWS + " rows"));
                    break;
                }

                try {
                    String name = getCell(row, headerIndex, "name");
                    String rollNumber = getCell(row, headerIndex, "rollnumber");
                    String className = getCell(row, headerIndex, "classname");

                    if (name == null || name.isBlank()) {
                        errors.add(Map.of("row", String.valueOf(rowNum), "error", "Name is required"));
                        continue;
                    }

                    String section = getCell(row, headerIndex, "section");
                    String gender = getCell(row, headerIndex, "gender");
                    String dateOfBirth = getCell(row, headerIndex, "dateofbirth");
                    String ageStr = getCell(row, headerIndex, "age");
                    int age = 0;
                    if (ageStr != null && !ageStr.isBlank()) {
                        try { age = Integer.parseInt(ageStr.trim()); } catch (NumberFormatException ignored) {}
                    }

                    String now = Instant.now().toString();
                    DocumentReference docRef = firestore.collection(STUDENTS_COLLECTION).document();
                    Student student = Student.builder()
                            .id(docRef.getId())
                            .name(name.trim())
                            .rollNumber(rollNumber != null ? rollNumber.trim() : "")
                            .className(className != null ? className.trim() : "")
                            .section(section != null ? section.trim() : "")
                            .age(age)
                            .dateOfBirth(dateOfBirth)
                            .gender(gender)
                            .schoolId(schoolId != null ? schoolId : "UNASSIGNED")
                            .teacherId(teacherId)
                            .active(true)
                            .tags(Collections.emptyList())
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    docRef.set(student).get();
                    createdIds.add(student.getId());
                } catch (ExecutionException | InterruptedException e) {
                    errors.add(Map.of("row", String.valueOf(rowNum), "error", "Database error: " + e.getMessage()));
                }
            }
        } catch (IOException | CsvValidationException e) {
            return Map.of("success", false, "message", "Failed to parse CSV: " + e.getMessage());
        }

        log.info("CSV import complete: {} created, {} errors for teacher {}", createdIds.size(), errors.size(), teacherId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("imported", createdIds.size());
        result.put("errors", errors);
        result.put("totalProcessed", rowNum);
        return result;
    }

    private String getCell(String[] row, Map<String, Integer> headerIndex, String key) {
        Integer idx = headerIndex.get(key);
        if (idx == null || idx >= row.length) return null;
        String val = row[idx].trim();
        return val.isEmpty() ? null : val;
    }
}
