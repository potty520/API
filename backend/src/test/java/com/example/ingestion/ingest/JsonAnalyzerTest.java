package com.example.ingestion.ingest;

import com.example.ingestion.common.ApiException;
import com.example.ingestion.common.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonAnalyzerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonAnalyzer analyzer = new JsonAnalyzer(new Jsons(mapper));

    private JsonNode json(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Test
    void detectsCommonRootAndFlattensNestedObjects() {
        JsonAnalyzer.Extraction extraction = analyzer.extract(
                json("{\"code\":0,\"data\":{\"list\":[{\"id\":1,\"user\":{\"name\":\"张三\"}}]}}"), "");
        assertEquals("data.list", extraction.detectedRoot());
        assertEquals(1, extraction.records().size());

        JsonAnalyzer.Analysis analysis = analyzer.analyze(extraction.records());
        assertEquals("integer", analysis.columns().get("id").type());
        assertEquals("string", analysis.columns().get("user_name").type());
        assertEquals("user.name", analysis.fieldMapping().get("user_name"));
    }

    @Test
    void rejectsMissingConfiguredRoot() {
        ApiException error = assertThrows(ApiException.class, () -> analyzer.extract(json("{\"data\":{}}"), "data.list"));
        assertEquals(422, error.getStatus());
    }

    @Test
    void widensIntegerToBigintAcrossRecords() {
        JsonAnalyzer.Analysis analysis = analyzer.analyze(List.of(json("{\"n\":1}"), json("{\"n\":12345678901}")));
        assertEquals("bigint", analysis.columns().get("n").type());
    }

    @Test
    void fallsBackToTextWhenTypesAreIncompatible() {
        JsonAnalyzer.Analysis analysis = analyzer.analyze(List.of(json("{\"v\":1}"), json("{\"v\":\"abc\"}")));
        assertEquals("text", analysis.columns().get("v").type());
    }

    @Test
    void normalizesIsoDateTimeToConfiguredZone() {
        JsonAnalyzer.Analysis analysis = analyzer.analyze(List.of(json("{\"t\":\"2024-01-01T00:00:00Z\"}")));
        assertEquals("datetime", analysis.columns().get("t").type());
        assertEquals("2024-01-01 08:00:00", analysis.rows().get(0).get("t"));
    }

    @Test
    void extractsChildTablesWithParentLinkColumns() {
        JsonAnalyzer.Analysis analysis = analyzer.analyze(
                List.of(json("{\"id\":7,\"items\":[{\"sku\":\"A\"},{\"sku\":\"B\"}]}")));
        JsonAnalyzer.ChildAnalysis child = analysis.childTables().get("items");
        assertNotNull(child);
        assertEquals(2, child.rows.size());
        assertEquals("A", child.rows.get(0).get("sku"));
        assertEquals(0, ((Number) child.rows.get(0).get("_item_index")).intValue());
        assertEquals(0, ((Number) child.rows.get(1).get("_parent_source_index")).intValue());
        assertTrue(child.columns.containsKey("_child_key"));
        assertTrue(child.columns.containsKey("_parent_key"));
        assertEquals(1, analysis.rows().size());
        assertFalse(analysis.rows().get(0).containsKey("items"));
    }

    @Test
    void conflictingColumnNamesGetDeterministicHashSuffix() {
        String payload = "{\"a-b\":1,\"a_b\":2}";
        JsonAnalyzer.Analysis first = analyzer.analyze(List.of(json(payload)));
        JsonAnalyzer.Analysis second = analyzer.analyze(List.of(json(payload)));
        // 列名必须在批次之间保持稳定, 否则每次同步都会 ADD COLUMN
        assertEquals(first.columns().keySet(), second.columns().keySet());
        assertTrue(first.columns().containsKey("a_b"));
        assertEquals(2, first.columns().keySet().stream().filter(name -> name.startsWith("a_b")).count());
        assertTrue(first.columns().keySet().stream().anyMatch(name -> name.matches("a_b_[0-9a-f]{6,16}")),
                () -> "冲突列名应带哈希后缀: " + first.columns().keySet());
    }

    @Test
    void mapsBusinessKeyByColumnOrSourcePath() {
        JsonAnalyzer.Analysis analysis = analyzer.analyze(List.of(json("{\"user\":{\"id\":9}}")));
        assertEquals("user_id", analyzer.mapBusinessKey("user.id", analysis));
        assertEquals("user_id", analyzer.mapBusinessKey("id", analysis));
        assertEquals("", analyzer.mapBusinessKey("   ", analysis));
    }

    @Test
    void snakeNormalizesIdentifiers() {
        assertEquals("user_name", JsonAnalyzer.snake("userName"));
        assertEquals("a_b", JsonAnalyzer.snake("A  b!"));
        assertEquals("field", JsonAnalyzer.snake(null));
        assertEquals("field", JsonAnalyzer.snake("###"));
    }
}
