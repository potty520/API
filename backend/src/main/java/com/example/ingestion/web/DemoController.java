package com.example.ingestion.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/demo-api")
@ConditionalOnProperty(name = "app.demo-api-enabled", havingValue = "true", matchIfMissing = true)
public class DemoController {
    private final Set<String> failedOnce = ConcurrentHashMap.newKeySet();

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> orders(@RequestParam(defaultValue = "v1") String scenario,
                                                       @RequestParam(defaultValue = "default") String key,
                                                       @RequestParam(defaultValue = "0") long delayMs) throws InterruptedException {
        if (delayMs > 0) Thread.sleep(Math.min(delayMs, 5000));
        if (scenario.equals("fail")) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("code", 503, "message", "模拟服务暂不可用"));
        if (scenario.equals("fail-once") && failedOnce.add(key)) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("code", 503, "message", "首次调用模拟失败"));
        boolean v2 = scenario.equals("v2");
        boolean missing = scenario.equals("missing-key");
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(order(missing ? null : 1001, "SO-20260805-001", "CUS-01", "华东制造", v2 ? 1299.5 : 1199.5, true,
                "2026-08-05 09:30:00", List.of("紧急", "线上"), v2 ? "WH-A" : null,
                List.of(line("MAT-A01", 2, 399.5), line("MAT-B03", 1, 400.5))));
        list.add(order(missing ? null : 1002, "SO-20260805-002", "CUS-02", "远航装备", 860, false,
                "2026-08-05 10:15:00", List.of(), v2 ? "WH-B" : null,
                List.of(line("MAT-C08", 4, 215))));
        if (v2) list.add(order(1003, "SO-20260805-003", "CUS-03", "新锐自动化", 3200.25, true,
                "2026-08-05 11:20:00", null, "WH-A", List.of(line("MAT-X01", 5, 640.05))));
        return ResponseEntity.ok(Map.of("code", 0, "message", "success", "data", Map.of("total", list.size(), "list", list)));
    }

    private Map<String, Object> order(Integer id, String no, String code, String customer, double amount, boolean paid,
                                      String createdAt, List<String> tags, String warehouse, List<Map<String, Object>> lines) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (id != null) result.put("id", id);
        result.put("orderNo", no);
        result.put("customer", Map.of("code", code, "name", customer));
        result.put("amount", amount);
        result.put("paid", paid);
        result.put("createdAt", createdAt);
        if (tags != null) result.put("tags", tags);
        if (warehouse != null) result.put("warehouse", warehouse);
        result.put("lines", lines);
        return result;
    }

    private Map<String, Object> line(String sku, int qty, double price) {
        return Map.of("sku", sku, "qty", qty, "price", price);
    }
}
