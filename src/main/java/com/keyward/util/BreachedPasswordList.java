package com.keyward.util;

import com.keyward.Keyward;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class BreachedPasswordList {
    private final Set<String> passwords = new HashSet<>();

    public BreachedPasswordList(Keyward plugin) {
        try (InputStream in = plugin.getResource("breached-passwords.txt")) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim().toLowerCase();
                        if (!trimmed.isEmpty()) {
                            passwords.add(trimmed);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load breached password list: " + e.getMessage());
        }
    }

    public boolean isBreached(String password) {
        return passwords.contains(password.toLowerCase());
    }
}