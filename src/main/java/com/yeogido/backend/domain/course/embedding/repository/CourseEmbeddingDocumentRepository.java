package com.yeogido.backend.domain.course.embedding.repository;

import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class CourseEmbeddingDocumentRepository {

    // Conservative application budget for the embedding model's 2048-token input limit.
    static final int MAX_DOCUMENT_BYTES = 1800;
    private static final int MAX_ITEM_SECTIONS = 12;
    private final EntityManager entityManager;

    public CourseEmbeddingDocumentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Long> findCourseIdsAfter(long afterId, int limit) {
        return entityManager.createQuery("""
                        select course.id from Course course
                        where course.deletedAt is null and course.id > :afterId
                        order by course.id
                        """, Long.class)
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList();
    }

    public Map<Long, CourseDocument> findDocuments(List<Long> courseIds) {
        if (courseIds.isEmpty()) return Map.of();
        List<Object[]> courses = entityManager.createQuery("""
                        select course.id, course.title, course.description, region.name, region.fullName
                        from Course course join course.region region
                        where course.id in :ids and course.deletedAt is null
                        order by course.id
                        """, Object[].class)
                .setParameter("ids", courseIds).getResultList();
        Map<Long, List<String>> sections = new LinkedHashMap<>();
        for (Object[] row : courses) {
            sections.put((Long) row[0], new ArrayList<>(List.of(
                    "코스: " + value(row[1]),
                    "설명: " + value(row[2]),
                    "지역: " + value(row[3]) + " " + value(row[4]))));
        }
        List<Object[]> items = entityManager.createQuery("""
                        select item.course.id, place.name, place.overview,
                               content.title, content.description, contentPlace.name, contentPlace.overview
                        from CourseItem item
                        left join item.place place
                        left join item.content content
                        left join content.place contentPlace
                        where item.course.id in :ids and item.course.deletedAt is null
                        order by item.course.id, item.day, item.orderNo, item.id
                        """, Object[].class)
                .setParameter("ids", courseIds).getResultList();
        for (Object[] item : items) {
            List<String> fields = new ArrayList<>();
            for (int index = 1; index < item.length; index++) {
                String field = value(item[index]);
                if (!field.isBlank() && !fields.contains(field)) fields.add(field);
            }
            if (!fields.isEmpty()) sections.get((Long) item[0]).add("방문: " + String.join(" / ", fields));
        }
        Map<Long, CourseDocument> result = new LinkedHashMap<>();
        sections.forEach((id, fields) -> result.put(id, buildDocument(id, fields)));
        return result;
    }

    static CourseDocument buildDocument(Long courseId, List<String> sourceSections) {
        String sourceHash = hash(sourceSections);
        List<String> sampled = new ArrayList<>(sourceSections.subList(0, Math.min(3, sourceSections.size())));
        List<String> items = sourceSections.subList(sampled.size(), sourceSections.size()).stream().distinct().toList();
        int count = Math.min(MAX_ITEM_SECTIONS, items.size());
        for (int index = 0; index < count; index++) {
            int position = count == 1 ? 0 : (int) ((long) index * (items.size() - 1) / (count - 1));
            sampled.add(items.get(position));
        }
        int sectionBudget = (MAX_DOCUMENT_BYTES - Math.max(0, sampled.size() - 1)) / Math.max(1, sampled.size());
        String text = String.join("\n", sampled.stream().map(part -> truncateUtf8(part, sectionBudget)).toList());
        return new CourseDocument(courseId, text, sourceHash);
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString().strip();
    }

    private static String truncateUtf8(String text, int byteBudget) {
        int end = 0;
        int bytes = 0;
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            int codePointBytes = codePoint <= 0x7F ? 1 : codePoint <= 0x7FF ? 2 : codePoint <= 0xFFFF ? 3 : 4;
            if (bytes + codePointBytes > byteBudget) break;
            bytes += codePointBytes;
            end += Character.charCount(codePoint);
        }
        return text.substring(0, end);
    }

    private static String hash(List<String> sections) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("course-document-v1".getBytes(StandardCharsets.UTF_8));
            for (String section : sections) {
                byte[] bytes = section.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    public record CourseDocument(Long courseId, String text, String sourceHash) { }
}
