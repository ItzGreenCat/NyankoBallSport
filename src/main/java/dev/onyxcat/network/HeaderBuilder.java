package dev.onyxcat.network;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class HeaderBuilder {
    public static String tokenSuffix = "2slhe02lsfiwowlcixisla_sls-_slaor";
    public static String iosVersion = "7.3.40";
    public static String androidVersion = "7.3.70";
    public static String androidUA = "Mozilla/5.0 (Linux; Android 14; 22081212C Build/UKQ1.231003.002) AppleWebKit/537.36 SWCampus/7.3.70";
    public static String iosUA = "SWCampus/7.3.40 (iPhone; iOS 18.1; Scale/3.00)";
    public static class BuiltHeader {
        private final String json;
        private final List<AdditionalHeader> additionalHeaders;

        public BuiltHeader(String json, List<AdditionalHeader> additionalHeaders) {
            this.json = json;
            this.additionalHeaders = additionalHeaders;
        }

        public String getJson() {
            return json;
        }

        public List<AdditionalHeader> getAdditionalHeaders() {
            return additionalHeaders;
        }
    }
    public static String nativeTokenSign(long uid, String token, long timestampMs) {
        String safeToken = token != null ? token : "";
        String query = "timeStamp=" + timestampMs + "&token=" + safeToken + "&uid=" + uid;
        String raw = query + tokenSuffix;
        return md5Hex(raw.getBytes(StandardCharsets.UTF_8));
    }
    private static String insertUuid(Map<String, Object> map) {
        String u = UUID.randomUUID().toString().toUpperCase();
        map.put("nonce", u);
        return u;
    }
    public static BuiltHeader buildNativeIosHeader(
            HeaderIdentity identity,
            long uid,
            String token,
            Long timestampMs
    ) {
        Map<String, Object> m = new LinkedHashMap<>();

        String deviceId = (identity.getDeviceId() == null || identity.getDeviceId().isEmpty())
                ? UUID.randomUUID().toString().toUpperCase()
                : identity.getDeviceId();
        long ts = (timestampMs != null) ? timestampMs : System.currentTimeMillis();
        long install = identity.installTime(ts);
        String safeToken = (token != null) ? token : "";

        m.put("osType", "1");
        m.put("DeviceId", deviceId);
        m.put("deviceName", identity.getDeviceName());
        m.put("CustomDeviceId", deviceId + "_iOS_sportsWorld_campus");
        m.put("osVersion", identity.getOsVersion());
        if (identity.getIdfa() != null && !identity.getIdfa().isEmpty()) {
            m.put("IDFA", identity.getIdfa());
        }
        m.put("logicPixel", "360x640");
        m.put("physicPixel", "1080x1920");
        m.put("cpuModel", "x86_64");
        m.put("appVersion", iosVersion);
        m.put("isRoot", Boolean.FALSE);
        m.put("appInstallTime", install);

        String nonce = insertUuid(m);

        if (uid >= 1) {
            m.put("uid", uid);
        }
        if (!safeToken.isEmpty()) {
            m.put("token", safeToken);
        }
        m.put("timeStamp", ts);
        m.put("studentId", (uid >= 1) ? uid : 0L);

        String sign = nativeTokenSign(uid, safeToken, ts);
        if (uid >= 1 && !safeToken.isEmpty()) {
            m.put("tokenSign", sign);
        }

        List<AdditionalHeader> extra = new ArrayList<>(3);
        extra.add(new AdditionalHeader("nonce", nonce));
        extra.add(new AdditionalHeader("timeStamp", String.valueOf(ts)));
        extra.add(new AdditionalHeader("tokenSign", sign));

        return new BuiltHeader(toCompactJson(m), extra);
    }
    public static BuiltHeader buildAndroidHeader(
            HeaderIdentity identity,
            long uid,
            String token,
            Long timestampMs
    ) {
        Map<String, Object> m = new LinkedHashMap<>();

        String deviceId = (identity.getDeviceId() == null || identity.getDeviceId().isEmpty())
                ? UUID.randomUUID().toString().toUpperCase()
                : identity.getDeviceId();
        long ts = (timestampMs != null) ? timestampMs : System.currentTimeMillis();
        long install = identity.installTime(ts);
        String safeToken = (token != null) ? token : "";

        m.put("Accept", "application/json");
        m.put("Content-Type", "application/json");
        m.put("appVersion", androidVersion);
        m.put("osType", "0");
        m.put("DeviceId", deviceId);
        m.put("osVersion", identity.getOsVersion());
        m.put("deviceName", identity.getDeviceName());
        m.put("IMEI", "");
        m.put("logicPixel", "1080x2400");
        m.put("physicPixel", "1080x2400");
        m.put("androidId", "");
        m.put("blMac", "");
        m.put("wifiMac", "");
        m.put("cpuModel", "arm64-v8a");
        m.put("isRoot", Boolean.FALSE);
        m.put("appUpdateTime", install);
        m.put("appInstallTime", install);

        String nonce = insertUuid(m);

        m.put("timeStamp", ts);
        m.put("CustomDeviceId", deviceId + "_android_sportsWorld_campus");
        m.put("uid", uid);
        m.put("token", safeToken);
        m.put("studentId", uid);

        String sign = nativeTokenSign(uid, safeToken, ts);
        m.put("tokenSign", sign);

        List<AdditionalHeader> extra = new ArrayList<AdditionalHeader>(3);
        extra.add(new AdditionalHeader("nonce", nonce));
        extra.add(new AdditionalHeader("timeStamp", String.valueOf(ts)));
        extra.add(new AdditionalHeader("tokenSign", sign));

        return new BuiltHeader(toCompactJson(m), extra);
    }
    public static BuiltHeader buildHeaderFor(
            HeaderIdentity identity,
            long uid,
            String token,
            Long timestampMs
    ) {
        if ("android".equals(identity.getPlatform())) {
            return buildAndroidHeader(identity, uid, token, timestampMs);
        } else {
            return buildNativeIosHeader(identity, uid, token, timestampMs);
        }
    }
    public static String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }
    private static String toCompactJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                sb.append('"').append(escapeJson((String) val)).append('"');
            } else {
                sb.append(val);
            }
        }
        sb.append('}');
        return sb.toString();
    }
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
