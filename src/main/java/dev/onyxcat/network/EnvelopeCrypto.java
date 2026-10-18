package dev.onyxcat.network;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;


public class EnvelopeCrypto {

    public static final String KEYDATA_ONE = "nhang.school";
    public static final String KEYDATA_TWO = "5K0E8400-E29";
    public static final String KEYDATA_THREE = "597DEA1AFB49";

    public static final String SALT_ALPHABET = "+kot8A*B45jF6CD@a!UVWubcdKLZ{efgMpNOxyz01PQ}Rn)Tvw23XYh(iG7rsEqJHI9+Slm/";

    public static final String RSA_PUB_PEM = "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC5pqlTzsGNZk1RxhH4O4x3JNTD\n" +
            "V7FbVH66mPfW5v1tnIy4ty7xv8DGMG4Zn/TvstwlJWeYOADHdi8uF21lJaBvzvPt\n" +
            "VEhifHXZq825fI9hGYtDoaVQmCN/Nfs2dKmt89XDrhtl3SZxO6TumOCTQt+5oqjF\n" +
            "2Jo3o1YtkAyGzjaJnwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    private static final SecureRandom RNG = new SecureRandom();
    private static volatile PublicKey cachedRsaPubKey = null;

    public enum OuterOrder {
        OBSERVED,
        INSERT
    }

    public static class EnvelopeSession {
        private String four;

        public EnvelopeSession() {
            this.four = null;
        }

        public EnvelopeSession(String fixedFour) {
            this.four = fixedFour;
        }

        public String[] keyData() {
            if (this.four == null) {
                this.four = keyDataFourFromMs((double) nowMs());
            }
            return new String[]{
                    KEYDATA_ONE,
                    KEYDATA_TWO,
                    KEYDATA_THREE,
                    this.four
            };
        }

        public static EnvelopeSession withFour(String four) {
            return new EnvelopeSession(four);
        }
    }

    public static class BuiltEnvelope {
        private final String json;
        private final byte[] container;
        private final String reqKey;
        private final long tsMs;
        private final String[] keyData;

        public BuiltEnvelope(String json, byte[] container, String reqKey, long tsMs, String[] keyData) {
            this.json = json;
            this.container = container;
            this.reqKey = reqKey;
            this.tsMs = tsMs;
            this.keyData = keyData;
        }

        public String getJson() {
            return json;
        }

        public byte[] getContainer() {
            return container;
        }

        public String getReqKey() {
            return reqKey;
        }

        public long getTsMs() {
            return tsMs;
        }

        public String[] getKeyData() {
            return keyData;
        }
    }
    public static PublicKey rsaPublicKey() {
        if (cachedRsaPubKey == null) {
            synchronized (EnvelopeCrypto.class) {
                if (cachedRsaPubKey == null) {
                    try {
                        String base64Pem = RSA_PUB_PEM
                                .replace("-----BEGIN PUBLIC KEY-----", "")
                                .replace("-----END PUBLIC KEY-----", "")
                                .replaceAll("\\s+", "");
                        byte[] der = Base64.getDecoder().decode(base64Pem);
                        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
                        KeyFactory kf = KeyFactory.getInstance("RSA");
                        cachedRsaPubKey = kf.generatePublic(spec);
                    } catch (Exception e) {
                        throw new RuntimeException("内置 RSA 公钥解析失败", e);
                    }
                }
            }
        }
        return cachedRsaPubKey;
    }

    public static long nowMs() {
        return System.currentTimeMillis();
    }

    public static String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] out = md.digest(data);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : out) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }

    public static String b64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] b64Decode(String data) {
        try {
            return Base64.getDecoder().decode(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("base64 解码失败: " + e.getMessage(), e);
        }
    }

    public static byte[] aes128CbcEncrypt(byte[] key, byte[] plaintext) {
        byte[] zeroIv = new byte[16];
        return aes128CbcEncryptIv(key, zeroIv, plaintext);
    }
    public static byte[] aes128CbcEncryptIv(byte[] key, byte[] iv, byte[] plaintext) {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("AES key 必须是 16B，得到 " + (key == null ? 0 : key.length));
        }
        if (iv == null || iv.length != 16) {
            throw new IllegalArgumentException("AES IV 必须是 16B，得到 " + (iv == null ? 0 : iv.length));
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败: " + e.getMessage(), e);
        }
    }
    public static byte[] aes128CbcDecrypt(byte[] key, byte[] ciphertext) {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("AES key 必须是 16B，得到 " + (key == null ? 0 : key.length));
        }
        try {
            byte[] zeroIv = new byte[16];
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(zeroIv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密/PKCS7 失败: " + e.getMessage(), e);
        }
    }
    public static String randomFragment(int n) {
        byte[] alphabet = SALT_ALPHABET.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) {
            bytes[i] = alphabet[RNG.nextInt(alphabet.length)];
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String reqKey() {
        return randomFragment(16);
    }
    public static String normalizeRawKeyData(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 7) {
            int missing = 12 - bytes.length;
            return value + randomFragment(missing);
        } else if (bytes.length >= 13) {
            byte[] last12 = Arrays.copyOfRange(bytes, bytes.length - 12, bytes.length);
            return new String(last12, StandardCharsets.UTF_8);
        } else {
            return value;
        }
    }
    public static String keyDataFourFromMs(double nowMsF64) {
        String formatted = String.format(Locale.US, "%.6f", nowMsF64);
        return normalizeRawKeyData(formatted);
    }
    public static BuiltEnvelope buildEnvelope(EnvelopeSession session, String plain, OuterOrder order) {
        return buildEnvelopeTs(session, plain, order, nowMs());
    }

    public static BuiltEnvelope buildEnvelopeTs(
            EnvelopeSession session,
            String plain,
            OuterOrder order,
            long tsMs
    ) {
        String[] keyData = session.keyData();
        String containerData = b64Encode(plain.getBytes(StandardCharsets.UTF_8));
        byte[] container = serializeContainer(containerData, tsMs, keyData);
        String rk = reqKey();

        byte[] encryptedContainer = aes128CbcEncrypt(rk.getBytes(StandardCharsets.UTF_8), container);
        String d = b64Encode(encryptedContainer);
        String h = md5Hex(container);
        String k = rsaEncryptPkcs1v15Base64(rk.getBytes(StandardCharsets.UTF_8));

        String json = serializeEnvelopeFields(d, h, k, order);
        return new BuiltEnvelope(json, container, rk, tsMs, keyData);
    }

    private static String rsaEncryptPkcs1v15Base64(byte[] data) {
        try {
            PublicKey pk = rsaPublicKey();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, pk, RNG);
            byte[] ct = cipher.doFinal(data);
            return b64Encode(ct);
        } catch (Exception e) {
            throw new RuntimeException("RSA PKCS1v15 加密失败", e);
        }
    }
    public static byte[] serializeContainer(String dataB64, long tsMs, String[] keyData) {
        String json = "{" +
                "\"data\":\"" + escapeJson(dataB64) + "\"," +
                "\"timeStamp\":" + tsMs + "," +
                "\"platform\":1," +
                "\"keyDataOne\":\"" + escapeJson(keyData[0]) + "\"," +
                "\"keyDataTwo\":\"" + escapeJson(keyData[1]) + "\"," +
                "\"keyDataThree\":\"" + escapeJson(keyData[2]) + "\"," +
                "\"keyDataFour\":\"" + escapeJson(keyData[3]) + "\"" +
                "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
    public static String serializeEnvelopeFields(String d, String h, String k, OuterOrder order) {
        if (order == OuterOrder.OBSERVED) {
            return "{\"k\":\"" + k + "\",\"p\":101,\"d\":\"" + d + "\",\"h\":\"" + h + "\",\"t\":0}";
        } else {
            return "{\"d\":\"" + d + "\",\"h\":\"" + h + "\",\"k\":\"" + k + "\",\"p\":101,\"t\":0}";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
