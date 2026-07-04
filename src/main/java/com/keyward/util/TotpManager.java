package com.keyward.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class TotpManager {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int TIME_STEP = 30;
    private static final int CODE_DIGITS = 6;

    public String generateSecret() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public boolean verifyCode(String base32Secret, String code) {
        long currentWindow = System.currentTimeMillis() / 1000L / TIME_STEP;
        for (long i = -1; i <= 1; i++) {
            if (generateCode(base32Secret, currentWindow + i).equals(code)) {
                return true;
            }
        }
        return false;
    }

    public String getCurrentCode(String base32Secret) {
        long currentWindow = System.currentTimeMillis() / 1000L / TIME_STEP;
        return generateCode(base32Secret, currentWindow);
    }

    private String generateCode(String base32Secret, long counter) {
        byte[] key = base32Decode(base32Secret);
        byte[] data = new byte[8];
        long value = counter;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] base32Decode(String input) {
        input = input.toUpperCase().replace("=", "");
        int byteCount = input.length() * 5 / 8;
        byte[] result = new byte[byteCount];
        int buffer = 0, bitsLeft = 0, index = 0;
        for (char c : input.toCharArray()) {
            int val = CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }

    public List<String> generateBackupCodes(int count) {
        SecureRandom random = new SecureRandom();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                sb.append(random.nextInt(10));
            }
            codes.add(sb.toString());
        }
        return codes;
    }
}