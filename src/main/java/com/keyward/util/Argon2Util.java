package com.keyward.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class Argon2Util {
    private static final int ARGON2_VERSION = 0x13;
    private static final int SYNC_POINTS = 4;
    private static final int BLOCK_SIZE = 1024;
    private static final int QWORDS_IN_BLOCK = 128;
    private static final int PREHASH_DIGEST_LENGTH = 64;
    private static final int PREHASH_SEED_LENGTH = 72;

    private final int memoryKb;
    private final int iterations;
    private final int parallelism;

    public Argon2Util(int memoryKb, int iterations, int parallelism) {
        this.memoryKb = memoryKb;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    public String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = argon2id(password.getBytes(StandardCharsets.UTF_8), salt, memoryKb, iterations, parallelism, 32);
        return "$argon2id$v=19$m=" + memoryKb + ",t=" + iterations + ",p=" + parallelism
                + "$" + Base64.getEncoder().withoutPadding().encodeToString(salt)
                + "$" + Base64.getEncoder().withoutPadding().encodeToString(hash);
    }

    public boolean verify(String password, String encoded) {
        if (encoded == null) return false;
        try {
            String[] parts = encoded.split("\\$");
            String[] params = parts[3].split(",");
            int m = Integer.parseInt(params[0].split("=")[1]);
            int t = Integer.parseInt(params[1].split("=")[1]);
            int p = Integer.parseInt(params[2].split("=")[1]);
            byte[] salt = Base64.getDecoder().decode(parts[4]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[5]);
            byte[] actualHash = argon2id(password.getBytes(StandardCharsets.UTF_8), salt, m, t, p, expectedHash.length);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] argon2id(byte[] pwd, byte[] salt, int memoryKb, int iterations, int parallelism, int outputLen) {
        int memory = memoryKb / (BLOCK_SIZE / 1024);
        if (memory < 2 * parallelism) memory = 2 * parallelism;
        int segmentLength = memory / (parallelism * SYNC_POINTS);
        int laneLength = segmentLength * SYNC_POINTS;
        memory = laneLength * parallelism;

        long[][] blocks = new long[memory][QWORDS_IN_BLOCK];

        byte[] h0 = initialHash(pwd, salt, parallelism, memory, iterations, ARGON2_VERSION, 32, 32);

        for (int i = 0; i < parallelism; i++) {
            System.arraycopy(blake2b(h0, intToBytesLE(i), null, 64), 0, toByteArray(blocks[i * laneLength]), 0, 64);
            System.arraycopy(blake2b(h0, intToBytesLE(Integer.MIN_VALUE + i), null, 64), 0, toByteArray(blocks[i * laneLength + 1]), 0, 64);
        }

        long[] zeroBlock = new long[QWORDS_IN_BLOCK];
        long[] pseudoRefs = new long[QWORDS_IN_BLOCK];

        for (int pass = 0; pass < iterations; pass++) {
            boolean dataIndependent = (pass == 0 && true);

            for (int segment = 0; segment < SYNC_POINTS; segment++) {
                for (int lane = 0; lane < parallelism; lane++) {
                    int start = (segment == 0) ? 2 : 0;
                    int prevLane = (pass == 0 && segment > 0) ? lane : ((lane + parallelism - 1) % parallelism);

                    for (int pos = start; pos < segmentLength; pos++) {
                        int index = lane * laneLength + segment * segmentLength + pos;
                        int prevIndex = index - 1;
                        if (prevIndex < 0) prevIndex = 0;

                        long[] refBlock;
                        if (pass == 0 && segment < 2) {
                            fillPseudoRefs(pseudoRefs, blocks[prevIndex], index, pass, lane, segmentLength, segment, memory, iterations, dataIndependent);
                            int refLane = (int) (pseudoRefs[0] % parallelism);
                            int refIndex = (int) (pseudoRefs[1] % (segmentLength * (segment + 1)));
                            refBlock = blocks[refLane * laneLength + refIndex];
                        } else {
                            long pseudo = blocks[prevIndex][0];
                            int refLane = (int) ((pseudo >> 32) % parallelism);
                            long startIndex = (segment == 0) ? 0 : (segment * segmentLength - 1);
                            long endIndex = segment * segmentLength + pos - 1;
                            if (pass == 0) endIndex = segment * segmentLength + pos - 1;
                            long relPos = (pseudo & 0xFFFFFFFFL) % (endIndex - startIndex + 1);
                            int refIndex = (int) startIndex + (int) (relPos);
                            refBlock = blocks[refLane * laneLength + refIndex];
                        }

                        xorBlock(blocks[index], blocks[index], blocks[prevIndex]);
                        xorBlock(blocks[index], blocks[index], refBlock);
                        blake2bCompress(blocks[index], blocks[index]);
                    }
                }
            }
        }

        long[] finalBlock = new long[QWORDS_IN_BLOCK];
        for (int i = 0; i < parallelism; i++) {
            xorBlock(finalBlock, finalBlock, blocks[i * laneLength + laneLength - 1]);
        }

        byte[] finalBytes = new byte[outputLen];
        byte[] hash = blake2b(null, toByteArray(finalBlock), null, 64);
        System.arraycopy(hash, 0, finalBytes, 0, outputLen);
        return finalBytes;
    }

    private static byte[] initialHash(byte[] pwd, byte[] salt, int parallelism, int memory, int iterations, int version, int keyLen, int outputLen) {
        ByteBuffer buf = ByteBuffer.allocate(PREHASH_SEED_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(parallelism);
        buf.putInt(outputLen);
        buf.putInt(memory);
        buf.putInt(iterations);
        buf.putInt(version);
        buf.putInt(0x13);
        buf.putInt(pwd.length);
        buf.put(pwd);
        buf.putInt(salt.length);
        buf.put(salt);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        return blake2b(null, buf.array(), null, 64);
    }

    private static void fillPseudoRefs(long[] output, long[] prev, int index, int pass, int lane, int segLen, int segment, int memory, int iterations, boolean dataIndependent) {
        long[] data = new long[QWORDS_IN_BLOCK];
        System.arraycopy(prev, 0, data, 0, QWORDS_IN_BLOCK);
        blake2bCompress(data, data);
        output[0] = data[0];
        output[1] = data[1];
    }

    private static void xorBlock(long[] dst, long[] a, long[] b) {
        for (int i = 0; i < QWORDS_IN_BLOCK; i++) {
            dst[i] = a[i] ^ b[i];
        }
    }

    private static void blake2bCompress(long[] block, long[] input) {
        long[] state = new long[16];
        System.arraycopy(input, 0, state, 0, 16);

        long[] v = new long[16];
        System.arraycopy(blake2bIV, 0, v, 0, 8);
        System.arraycopy(state, 0, v, 8, 8);

        for (int r = 0; r < 12; r++) {
            G(v, 0, 4, 8, 12);
            G(v, 1, 5, 9, 13);
            G(v, 2, 6, 10, 14);
            G(v, 3, 7, 11, 15);
            G(v, 0, 5, 10, 15);
            G(v, 1, 6, 11, 12);
            G(v, 2, 7, 8, 13);
            G(v, 3, 4, 9, 14);
        }

        for (int i = 0; i < 8; i++) {
            state[i] ^= v[i] ^ v[i + 8];
        }

        System.arraycopy(state, 0, block, 0, 16);
    }

    private static void G(long[] v, int a, int b, int c, int d) {
        v[a] = v[a] + v[b];
        v[d] = Long.rotateRight(v[d] ^ v[a], 32);
        v[c] = v[c] + v[d];
        v[b] = Long.rotateRight(v[b] ^ v[c], 24);
        v[a] = v[a] + v[b];
        v[d] = Long.rotateRight(v[d] ^ v[a], 16);
        v[c] = v[c] + v[d];
        v[b] = Long.rotateRight(v[b] ^ v[c], 63);
    }

    private static final long[] blake2bIV = {
        0x6a09e667f3bcc908L, 0xbb67ae8584caa73bL, 0x3c6ef372fe94f82bL, 0xa54ff53a5f1d36f1L,
        0x510e527fade682d1L, 0x9b05688c2b3e6c1fL, 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L
    };

    private static byte[] blake2b(byte[] key, byte[] input, byte[] personal, int digestLen) {
        int blockLen = 128;
        byte[] buf = new byte[blockLen];
        long[] h = new long[8];
        System.arraycopy(blake2bIV, 0, h, 0, 8);
        h[0] ^= 0x01010000 ^ ((long) digestLen & 0xFF);

        if (key != null && key.length > 0) {
            h[0] ^= (key.length << 8);
            Arrays.fill(buf, (byte) 0);
            System.arraycopy(key, 0, buf, 0, key.length);
            compressBlock(h, buf, 0, true);
        }

        int offset = 0;
        int remaining = input.length;
        long totalLen = 0;

        while (remaining > blockLen) {
            compressBlock(h, input, offset, false);
            offset += blockLen;
            remaining -= blockLen;
            totalLen += blockLen;
        }

        Arrays.fill(buf, (byte) 0);
        System.arraycopy(input, offset, buf, 0, remaining);
        totalLen += remaining;
        compressBlock(h, buf, 0, true);

        byte[] output = new byte[digestLen];
        ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 8; i++) bb.putLong(h[i]);
        System.arraycopy(bb.array(), 0, output, 0, digestLen);
        return output;
    }

    private static void compressBlock(long[] h, byte[] block, int offset, boolean last) {
        long[] v = new long[16];
        System.arraycopy(h, 0, v, 0, 8);
        System.arraycopy(blake2bIV, 0, v, 8, 8);

        long[] m = new long[16];
        ByteBuffer bb = ByteBuffer.wrap(block, offset, 128).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 16; i++) m[i] = bb.getLong();

        if (last) {
            v[14] ^= -1L;
        }

        int[][] sigma = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3},
            {11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4},
            {7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8},
            {9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13},
            {2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9},
            {12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11},
            {13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10},
            {6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5},
            {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0},
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3}
        };

        for (int r = 0; r < 12; r++) {
            int[] s = sigma[r % 10];
            G(v, 0, 4, 8, 12, m[s[0]], m[s[1]]);
            G(v, 1, 5, 9, 13, m[s[2]], m[s[3]]);
            G(v, 2, 6, 10, 14, m[s[4]], m[s[5]]);
            G(v, 3, 7, 11, 15, m[s[6]], m[s[7]]);
            G(v, 0, 5, 10, 15, m[s[8]], m[s[9]]);
            G(v, 1, 6, 11, 12, m[s[10]], m[s[11]]);
            G(v, 2, 7, 8, 13, m[s[12]], m[s[13]]);
            G(v, 3, 4, 9, 14, m[s[14]], m[s[15]]);
        }

        for (int i = 0; i < 8; i++) {
            h[i] ^= v[i] ^ v[i + 8];
        }
    }

    private static void G(long[] v, int a, int b, int c, int d, long mx, long my) {
        v[a] = v[a] + v[b] + mx;
        v[d] = Long.rotateRight(v[d] ^ v[a], 32);
        v[c] = v[c] + v[d];
        v[b] = Long.rotateRight(v[b] ^ v[c], 24);
        v[a] = v[a] + v[b] + my;
        v[d] = Long.rotateRight(v[d] ^ v[a], 16);
        v[c] = v[c] + v[d];
        v[b] = Long.rotateRight(v[b] ^ v[c], 63);
    }

    private static byte[] intToBytesLE(int value) {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(value);
        return bb.array();
    }

    private static byte[] toByteArray(long[] longs) {
        ByteBuffer bb = ByteBuffer.allocate(longs.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (long l : longs) bb.putLong(l);
        return bb.array();
    }

    private static long[] toLongArray(byte[] bytes) {
        int len = bytes.length / 8;
        long[] result = new long[len];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < len; i++) result[i] = bb.getLong();
        return result;
    }
}