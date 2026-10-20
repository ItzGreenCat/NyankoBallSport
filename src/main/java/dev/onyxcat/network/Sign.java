package dev.onyxcat.network;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;

public final class Sign {

    public static final String SALT =
            "2slhe02lsfiwowlcixisla_sls-_slaor";

    /**
     * 参与提交签名的字段。
     * 未列出的字段不参与签名。
     */
    private static final String[] UPLOAD_SIGN_FIELD_ORDER = {
            "sportType",
            "totalTime",
            "totalDis",
            "speed",
            "startTime",
            "stopTime",
            "complete",
            "selDistance",
            "unCompleteReason",
            "getPrize",
            "status",
            "uuid",
            "uid",
            "avgStepFreq",
            "totalSteps",
            "selectedUnid",
            "calorie",
            "policy",
            "selRunTime",
            "validDis",
            "validTime",
            "useMobilityTools",
            "errorCode",
            "geeToken",
            "unauthorized",
            "themeId",
            "faceCheck",
            "goalId",
            "address",
            "avgPower",
            "totalAscent"
    };

    private Sign() {
    }

    /**
     * 五点/点位请求的URL签名。
     *
     * 只替换第一次出现的https://，与Rust replacen(..., 1)一致。
     * 请求body不参与计算。
     */
    public static String signUrlMD5(String url) {
        if (url == null) {
            throw new IllegalArgumentException("url不能为空");
        }

        int index = url.indexOf("https://");

        String httpUrl = index < 0
                ? url
                : url.substring(0, index)
                + "http://"
                + url.substring(index + "https://".length());

        return md5(httpUrl + SALT);
    }

    /**
     * 提交签名：
     * 筛选字段 → 忽略大小写排序 → key=value以&连接
     * → 追加盐 → MD5。
     */
    public static String signature(
            JsonNode values,
            boolean hasRoomId
    ) {
        List<Map.Entry<String, String>> entries =
                buildSignMap(values, hasRoomId);

        String query = joinQuery(entries, true);

        return md5(query + SALT);
    }

    /**
     * 默认不包含roomId。
     */
    public static String signature(JsonNode values) {
        return signature(values, false);
    }

    /**
     * 原始签名串：
     * 筛选字段 → 按字段名自然序排序 → key=value以&连接。
     *
     * 不追加盐，也不计算MD5。
     */
    public static String originalSign(
            JsonNode values,
            boolean hasRoomId
    ) {
        List<Map.Entry<String, String>> entries =
                buildSignMap(values, hasRoomId);

        return joinQuery(entries, false);
    }

    /**
     * 默认不包含roomId。
     */
    public static String originalSign(JsonNode values) {
        return originalSign(values, false);
    }

    private static List<Map.Entry<String, String>> buildSignMap(
            JsonNode values,
            boolean hasRoomId
    ) {
        if (values == null || !values.isObject()) {
            throw new IllegalArgumentException(
                    "签名参数必须是JSON对象"
            );
        }

        List<Map.Entry<String, String>> entries =
                new ArrayList<>();

        for (String key : UPLOAD_SIGN_FIELD_ORDER) {
            JsonNode value = values.get(key);

            /*
             * 缺失字段不参与签名；
             * 显式null参与签名，值为字符串"null"。
             */
            if (value != null) {
                entries.add(
                        new AbstractMap.SimpleImmutableEntry<>(
                                key,
                                androidValue(value)
                        )
                );
            }
        }

        if (hasRoomId) {
            JsonNode roomId = values.get("roomId");

            if (roomId != null) {
                entries.add(
                        new AbstractMap.SimpleImmutableEntry<>(
                                "roomId",
                                androidValue(roomId)
                        )
                );
            }
        }

        entries.removeIf(entry ->
                "signature".equalsIgnoreCase(entry.getKey())
        );

        return entries;
    }

    /**
     * 字符串取原值，其他类型使用其JSON文本。
     *
     * 示例：
     * "abc" → abc
     * true  → true
     * 123   → 123
     * null  → null
     */
    private static String androidValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "null";
        }

        if (value.isTextual()) {
            return value.textValue();
        }

        return value.toString();
    }

    private static String joinQuery(
            List<Map.Entry<String, String>> entries,
            boolean ignoreCase
    ) {
        List<Map.Entry<String, String>> sorted =
                new ArrayList<>(entries);

        Comparator<String> keyComparator = ignoreCase
                ? String.CASE_INSENSITIVE_ORDER
                : Comparator.naturalOrder();

        sorted.sort((a, b) ->
                keyComparator.compare(a.getKey(), b.getKey())
        );

        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, String> entry : sorted) {
            if (result.length() > 0) {
                result.append('&');
            }

            result.append(entry.getKey())
                    .append('=')
                    .append(entry.getValue());
        }

        // 与Rust一致，不对key或value进行URL编码。
        return result.toString();
    }

    private static String md5(String text) {
        return EnvelopeCrypto.md5Hex(
                text.getBytes(StandardCharsets.UTF_8)
        );
    }
}
