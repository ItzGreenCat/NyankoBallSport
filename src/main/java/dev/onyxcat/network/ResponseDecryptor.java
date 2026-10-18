package dev.onyxcat.network;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

public class ResponseDecryptor {

    public static class Decrypted {
        private final String plaintext;
        private final String business;
        private final String expectedMd5;
        private final String actualMd5;

        public Decrypted(String plaintext, String business, String expectedMd5, String actualMd5) {
            this.plaintext = plaintext;
            this.business = business;
            this.expectedMd5 = expectedMd5;
            this.actualMd5 = actualMd5;
        }

        public String getPlaintext() {
            return plaintext;
        }

        public String getBusiness() {
            return business;
        }

        public String getExpectedMd5() {
            return expectedMd5;
        }

        public String getActualMd5() {
            return actualMd5;
        }
    }

    private static byte[] cStringBytes(String v) {
        byte[] b = v.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < b.length; i++) {
            if (b[i] == 0) {
                return Arrays.copyOf(b, i);
            }
        }
        return b;
    }

    public static int fold32(String value) {
        int h = 0;
        for (byte b : cStringBytes(value)) {
            int c = b & 0xFF;
            h = (((h & 0x00FFFFFF) << 8) | c) ^ (h >>> 24);
        }
        return h;
    }

    private static int ror32(int value, int bits) {
        return Integer.rotateRight(value, bits);
    }
    public static byte[] derivePaesKey(String one, String two, String three, String four) {
        String[] vals = {one, two, three, four};
        for (String v : vals) {
            int l = cStringBytes(v).length;
            if (l < 8 || l > 12) {
                throw new IllegalArgumentException("keyData UTF-8 长度必须为 8..12B: " + v);
            }
        }
        int a = fold32(one);
        int b = fold32(two);
        int c = fold32(three);
        int d = fold32(four);

        int w9 = c ^ a;
        int w10 = w9 ^ ror32(w9, 24);
        w9 = w10 ^ ror32(w9, 8);
        w10 = w9 ^ b;
        w9 ^= d;

        int w8 = d ^ b;
        int w11 = w8 ^ ror32(w8, 24);
        w8 = w11 ^ ror32(w8, 8);
        w11 = w8 ^ a;
        w8 ^= c;

        int w12 = w8 & w10;
        w11 ^= w12;
        w12 = w8 | w9;
        w8 ^= w9;
        w10 ^= w12;
        w8 = w10 ^ (~w8);
        w12 = w8 ^ w11;
        w8 |= w11;
        w8 ^= w10;
        w10 = w12 & (~w10);
        w9 ^= w10;

        ByteBuffer out = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(w9);
        out.putInt(w8);
        out.putInt(w12);
        out.putInt(w11);
        return out.array();
    }

    public static String rsaRecoveredDigest(String responseS, PublicKey pubKey) {
        RSAPublicKey rsaPub = (RSAPublicKey) pubKey;
        byte[] sig = EnvelopeCrypto.b64Decode(responseS);

        BigInteger n = rsaPub.getModulus();
        BigInteger e = rsaPub.getPublicExponent();
        int size = (n.bitLength() + 7) / 8;

        if (sig.length != size) {
            throw new IllegalArgumentException("s 解码长度错误: " + sig.length + " != " + size);
        }

        BigInteger m = new BigInteger(1, sig);
        byte[] emRaw = m.modPow(e, n).toByteArray();

        byte[] em = new byte[size];
        if (emRaw.length > size) {
            System.arraycopy(emRaw, emRaw.length - size, em, 0, size);
        } else {
            System.arraycopy(emRaw, 0, em, size - emRaw.length, emRaw.length);
        }

        if (em.length < 2 || em[0] != 0x00 || em[1] != 0x01) {
            throw new IllegalArgumentException("s 不是 RSA PKCS#1 type-1 块");
        }

        int marker = -1;
        for (int i = 2; i < em.length; i++) {
            if (em[i] == 0x00) {
                marker = i;
                break;
            }
        }
        if (marker == -1) {
            throw new IllegalArgumentException("s 缺少 RSA PKCS#1 分隔符");
        }
        if (marker < 10) {
            throw new IllegalArgumentException("s 的 RSA PKCS#1 type-1 填充不合法");
        }
        for (int i = 2; i < marker; i++) {
            if ((em[i] & 0xFF) != 0xFF) {
                throw new IllegalArgumentException("s 的 RSA PKCS#1 type-1 填充不合法");
            }
        }

        byte[] digestBytes = Arrays.copyOfRange(em, marker + 1, em.length);
        String digest = new String(digestBytes, StandardCharsets.UTF_8);
        if (digest.length() != 32 || !isLowerHex(digest)) {
            throw new IllegalArgumentException("RSA(s) 尾部不是 32 字节小写 hex");
        }
        return digest;
    }

    public static Decrypted decryptResponse(byte[] raw, byte[] key, PublicKey pubKey) {
        String text = new String(raw, StandardCharsets.UTF_8).trim();
        if (!text.startsWith("{") || !text.endsWith("}")) {
            throw new IllegalArgumentException("响应不是合法 JSON");
        }

        String objJson = text;
        String respStr = extractJsonString(objJson, "resp");
        if (respStr != null) {
            objJson = unescapeJsonString(respStr).trim();
        } else {
            String respObj = extractJsonObject(objJson, "resp");
            if (respObj != null) {
                objJson = respObj;
            }
        }

        String r = extractJsonString(objJson, "r");
        String s = extractJsonString(objJson, "s");
        String vRaw = extractJsonRawValue(objJson, "v");
        boolean hasRsv = (r != null && s != null && vRaw != null);

        if (!hasRsv) {
            return new Decrypted(text, objJson, "", "");
        }

        long version;
        try {
            String cleanV = vRaw.replace("\"", "").trim();
            version = Long.parseLong(cleanV);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("响应 v 不是整数: " + vRaw);
        }
        if (version != 101L) {
            throw new IllegalArgumentException("响应版本错误: " + version + " != 101");
        }

        String expected = rsaRecoveredDigest(s, pubKey);

        byte[] layer1;
        try {
            layer1 = EnvelopeCrypto.aes128CbcDecrypt(key, EnvelopeCrypto.b64Decode(r));
        } catch (Exception e) {
            throw new IllegalArgumentException("第一层 AES 解密失败: " + e.getMessage(), e);
        }

        String actual = EnvelopeCrypto.md5Hex(layer1);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("响应验签失败: 实际 MD5 " + actual + " 期望 " + expected);
        }

        String plaintext = new String(layer1, StandardCharsets.UTF_8).trim();
        if (!plaintext.startsWith("{") || !plaintext.endsWith("}")) {
            String head = plaintext.length() > 64 ? plaintext.substring(0, 64) : plaintext;
            throw new IllegalArgumentException("内层信封 JSON 解析失败 / 前64B: " + head);
        }

        String d = extractJsonString(plaintext, "d");
        String outerJson;
        if (d != null) {
            byte[] layer2 = EnvelopeCrypto.aes128CbcDecrypt(key, EnvelopeCrypto.b64Decode(d));
            outerJson = new String(layer2, StandardCharsets.UTF_8).trim();
            if (!outerJson.startsWith("{") || !outerJson.endsWith("}")) {
                throw new IllegalArgumentException("第二层明文 JSON 解析失败");
            }
        } else {
            outerJson = plaintext;
        }

        String dataB64 = extractJsonString(outerJson, "data");
        String businessJson;
        if (dataB64 != null) {
            byte[] bytes = EnvelopeCrypto.b64Decode(dataB64);
            businessJson = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!businessJson.startsWith("{") && !businessJson.startsWith("[")) {
                throw new IllegalArgumentException("业务 data Base64 解码后不是 JSON");
            }
        } else {
            businessJson = outerJson;
        }

        return new Decrypted(plaintext, businessJson, expected, actual);
    }
    private static boolean isLowerHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!ok) return false;
        }
        return true;
    }
    private static String extractJsonString(String json, String key) {
        int valStart = findKeyColonIndex(json, key);
        if (valStart == -1) return null;
        while (valStart < json.length() && Character.isWhitespace(json.charAt(valStart))) {
            valStart++;
        }
        if (valStart >= json.length() || json.charAt(valStart) != '"') {
            return null;
        }
        int strStart = valStart + 1;
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = strStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                sb.append('\\').append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }
    private static String extractJsonObject(String json, String key) {
        int valStart = findKeyColonIndex(json, key);
        if (valStart == -1) return null;
        while (valStart < json.length() && Character.isWhitespace(json.charAt(valStart))) {
            valStart++;
        }
        if (valStart >= json.length() || json.charAt(valStart) != '{') {
            return null;
        }
        int depth = 0;
        boolean inStr = false;
        boolean escaped = false;
        for (int i = valStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inStr = !inStr;
            } else if (!inStr) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(valStart, i + 1);
                    }
                }
            }
        }
        return null;
    }
    private static String extractJsonRawValue(String json, String key) {
        int valStart = findKeyColonIndex(json, key);
        if (valStart == -1) return null;
        while (valStart < json.length() && Character.isWhitespace(json.charAt(valStart))) {
            valStart++;
        }
        if (valStart >= json.length()) return null;
        int end = valStart;
        boolean inStr = false;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"') inStr = !inStr;
            else if (!inStr && (c == ',' || c == '}')) break;
            end++;
        }
        return json.substring(valStart, end).trim();
    }

    private static int findKeyColonIndex(String json, String key) {
        String target = "\"" + key + "\"";
        int idx = 0;
        while ((idx = json.indexOf(target, idx)) != -1) {
            int afterKey = idx + target.length();
            while (afterKey < json.length() && Character.isWhitespace(json.charAt(afterKey))) {
                afterKey++;
            }
            if (afterKey < json.length() && json.charAt(afterKey) == ':') {
                return afterKey + 1;
            }
            idx = afterKey;
        }
        return -1;
    }

    private static String unescapeJsonString(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
