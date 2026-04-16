package com.ai.learningdetection.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportService {

    private final Firestore firestore;

    private static final String STUDENTS_COLLECTION = "students";
    private static final String PAPERS_COLLECTION = "test_papers";
    private static final String TEACHERS_COLLECTION = "teachers";

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(49, 46, 129));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, Color.WHITE);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(100, 116, 139));
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(30, 41, 59));
    private static final Color PRIMARY_COLOR = new Color(20, 184, 166);
    private static final Color HEADER_BG = new Color(49, 46, 129);

    public byte[] generateStudentReport(String studentId, String teacherId) {
        try {
            DocumentSnapshot studentSnap = firestore.collection(STUDENTS_COLLECTION).document(studentId).get().get();
            if (!studentSnap.exists()) {
                throw new RuntimeException("Student not found");
            }

            String studentTeacherId = studentSnap.getString("teacherId");
            if (!teacherId.equals(studentTeacherId)) {
                throw new RuntimeException("Student does not belong to this teacher");
            }

            String studentName = studentSnap.getString("name");
            String className = studentSnap.getString("className");
            String rollNumber = studentSnap.getString("rollNumber");
            String schoolId = studentSnap.getString("schoolId");
            String gender = studentSnap.getString("gender");

            // Fetch teacher name
            DocumentSnapshot teacherSnap = firestore.collection(TEACHERS_COLLECTION).document(teacherId).get().get();
            String teacherName = teacherSnap.exists() ? teacherSnap.getString("name") : "Unknown";

            // Fetch analysis papers
            QuerySnapshot papers = firestore.collection(PAPERS_COLLECTION)
                    .whereEqualTo("studentId", studentId)
                    .get().get();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Title
            Paragraph title = new Paragraph("NeuraScan Student Report", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5);
            doc.add(title);

            Paragraph date = new Paragraph("Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")), BODY_FONT);
            date.setAlignment(Element.ALIGN_CENTER);
            date.setSpacingAfter(20);
            doc.add(date);

            // Student info table
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{1, 2});
            infoTable.setSpacingAfter(20);

            addInfoRow(infoTable, "Student Name", studentName);
            addInfoRow(infoTable, "Class", className);
            addInfoRow(infoTable, "Roll Number", rollNumber);
            addInfoRow(infoTable, "Gender", gender);
            addInfoRow(infoTable, "Teacher", teacherName);
            addInfoRow(infoTable, "Total Analyses", String.valueOf(papers.size()));
            doc.add(infoTable);

            // Analysis results table
            if (!papers.isEmpty()) {
                Paragraph analysisTitle = new Paragraph("Analysis Results", new Font(Font.HELVETICA, 14, Font.BOLD, HEADER_BG));
                analysisTitle.setSpacingBefore(10);
                analysisTitle.setSpacingAfter(10);
                doc.add(analysisTitle);

                PdfPTable resultTable = new PdfPTable(4);
                resultTable.setWidthPercentage(100);
                resultTable.setWidths(new float[]{1, 2, 2, 1.5f});

                addHeaderCell(resultTable, "#");
                addHeaderCell(resultTable, "Date");
                addHeaderCell(resultTable, "Prediction");
                addHeaderCell(resultTable, "Confidence");

                int idx = 1;
                for (QueryDocumentSnapshot paperDoc : papers.getDocuments()) {
                    String createdAt = paperDoc.getString("createdAt");
                    String prediction = paperDoc.getString("prediction");
                    Double confidence = paperDoc.getDouble("confidence");

                    addBodyCell(resultTable, String.valueOf(idx++));
                    addBodyCell(resultTable, createdAt != null ? createdAt.substring(0, Math.min(10, createdAt.length())) : "-");
                    addBodyCell(resultTable, prediction != null ? prediction : "-");
                    addBodyCell(resultTable, confidence != null ? String.format("%.1f%%", confidence * 100) : "-");
                }
                doc.add(resultTable);
            } else {
                doc.add(new Paragraph("No analysis results available yet.", BODY_FONT));
            }

            // Footer
            Paragraph footer = new Paragraph("\nThis report was generated by NeuraScan AI Learning Disorder Detection System.", 
                    new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY));
            footer.setSpacingBefore(30);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error generating PDF report", e);
        }
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(new Color(226, 232, 240));
        labelCell.setPadding(6);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "-", VALUE_FONT));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(new Color(226, 232, 240));
        valueCell.setPadding(6);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY_FONT));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(new Color(226, 232, 240));
        table.addCell(cell);
    }
}
