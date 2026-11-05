package com.rackguard.rackguardserver.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;


public final class Util {
    private Util() {}

    public static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(String s) { return HexFormat.of().formatHex(sha256(s)); }

    public static String uidHash(String uid) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(uid.trim().toUpperCase()));
    }

    public static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean safeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public static LocalDateTime nowUtc() { return LocalDateTime.now(ZoneOffset.UTC); }

    public static LocalDateTime toUtc(OffsetDateTime t) {
        return t.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDateTime fromInstant(Instant i) { return LocalDateTime.ofInstant(i, ZoneOffset.UTC); }

    public record FileData(byte[] data, String contentType, String fileName) {}

    public static FileData export(String format, String name, List<String> header, List<List<Object>> rows) {
        if ("json".equalsIgnoreCase(format)) {
            List<Map<String, Object>> list = rows.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) m.put(header.get(i), r.get(i));
                return m;
            }).toList();
            try {
                return new FileData(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(list),
                        "application/json", name + ".json");
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        StringBuilder sb = new StringBuilder(String.join(",", header)).append('\n');
        for (List<Object> row : rows) {
            sb.append(String.join(",", row.stream().map(Util::csvCell).toList())).append('\n');
        }
        return new FileData(sb.toString().getBytes(StandardCharsets.UTF_8), "text/csv; charset=UTF-8", name + ".csv");
    }

    private static String csvCell(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        return s.matches("(?s).*[,\"\\n].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}