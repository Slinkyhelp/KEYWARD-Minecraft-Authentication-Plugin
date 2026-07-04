package com.keyward.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class MojangAPI {
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_MS = 10 * 60 * 1000L;

    public Optional<UUID> checkPremium(String username) {
        String key = username.toLowerCase();
        CacheEntry cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_MS) {
            return cached.uuid;
        }
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    String idStr = obj.get("id").getAsString();
                    UUID uuid = UUID.fromString(idStr.replaceFirst(
                            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                    Optional<UUID> result = Optional.of(uuid);
                    cache.put(key, new CacheEntry(result, System.currentTimeMillis()));
                    return result;
                }
            } else if (code == 404) {
                Optional<UUID> result = Optional.empty();
                cache.put(key, new CacheEntry(result, System.currentTimeMillis()));
                return result;
            } else {
                return cached != null ? cached.uuid : Optional.empty();
            }
        } catch (Exception e) {
            return cached != null ? cached.uuid : Optional.empty();
        }
    }

    private static class CacheEntry {
        final Optional<UUID> uuid;
        final long timestamp;

        CacheEntry(Optional<UUID> uuid, long timestamp) {
            this.uuid = uuid;
            this.timestamp = timestamp;
        }
    }
}