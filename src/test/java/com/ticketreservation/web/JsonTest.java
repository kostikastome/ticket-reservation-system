package com.ticketreservation.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    @Test
    void parsesAllValueTypes() {
        Object parsed = Json.parse("""
                {"name": "Anne-Marie", "seats": 3, "price": 19.99, "ok": true, "no": false,
                 "nothing": null, "list": [1, "two", {"three": 3}], "escaped": "a\\"b\\\\c\\n\\u00e9"}""");

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertEquals("Anne-Marie", map.get("name"));
        assertEquals(new BigDecimal("3"), map.get("seats"));
        assertEquals(new BigDecimal("19.99"), map.get("price"));
        assertEquals(true, map.get("ok"));
        assertEquals(false, map.get("no"));
        assertNull(map.get("nothing"));
        assertEquals(List.of(new BigDecimal("1"), "two", Map.of("three", new BigDecimal("3"))), map.get("list"));
        assertEquals("a\"b\\c\né", map.get("escaped"));
    }

    @Test
    void writesValuesAndEscapesStrings() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "say \"hi\"\n");
        map.put("html", "<script>");
        map.put("count", 2);
        map.put("price", new BigDecimal("10.50"));
        map.put("flag", true);
        map.put("none", null);
        map.put("list", Arrays.asList(1L, "x"));

        assertEquals("{\"text\":\"say \\\"hi\\\"\\n\",\"html\":\"\\u003cscript\\u003e\",\"count\":2,"
                + "\"price\":10.50,\"flag\":true,\"none\":null,\"list\":[1,\"x\"]}", Json.write(map));
    }

    @Test
    void roundTripsWhatItWrites() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("emoji", "✈ Tirana → Frankfurt");
        original.put("nested", List.of(Map.of("a", "b")));
        assertEquals(original, Json.parse(Json.write(original)));
    }

    @Test
    void rejectsInvalidJson() {
        for (String bad : new String[]{"", "{", "{\"a\" 1}", "[1,]", "{\"a\":1}x", "tru", "\"unterminated",
                "{\"a\":\"\\q\"}", "01x", "[".repeat(100)}) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
        }
    }
}
