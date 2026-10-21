package dev.onyxcat.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.onyxcat.Main;
import dev.onyxcat.network.*;
import dev.onyxcat.session.Session;
import dev.onyxcat.track.*;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RunningProcessor {
    public static final String recordPath = "/api/v70260/runnings/save/record";
    public static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OBS_SIGN_PATH =
            "/api/obs/temporary/url";

    private static final String GET_ONE_RECORD_PATH =
            "/api/v70260/runnings/get_one_record";

    public static void processRun(RunningArguments args){
        if(Session.uid == -1) {
            JOptionPane.showMessageDialog(null, "你还未登录！");
            return;
        }
        PolicyArgument policy = null;
        try{
            policy = PolicyProcessor.getPolicy();
        } catch (Exception e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"无法获取Policy");
        }
        if(policy != null){
            List<Point> points = null;

            try {
                points = PointProcessor.getPoints();

                if (points == null || points.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "没有获取到Point");
                } else {
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    Point basePoint = points.get(random.nextInt(points.size()));

                    double baseLat = basePoint.lat;
                    double baseLon = basePoint.lon;

                    double latOffsetMeters = clamp(
                            random.nextGaussian() * 120.0,
                            -200.0,
                            200.0
                    );

                    double lonOffsetMeters = clamp(
                            random.nextGaussian() * 120.0,
                            -200.0,
                            200.0
                    );

                    double metersPerDegreeLat = 111320.0;

                    double metersPerDegreeLon =
                            111320.0 * Math.cos(Math.toRadians(baseLat));

                    if (Math.abs(metersPerDegreeLon) < 1e-6) {
                        metersPerDegreeLon = 1e-6;
                    }

                    double newLat =
                            baseLat + latOffsetMeters / metersPerDegreeLat;

                    double newLon =
                            baseLon + lonOffsetMeters / metersPerDegreeLon;

                    Point anchor = new Point();
                    anchor.lat = newLat;
                    anchor.lon = newLon;
                    anchor.isFixed = basePoint.isFixed;
                    anchor.pointName = basePoint.pointName;
                    anchor.glat = basePoint.glat;
                    anchor.glon = basePoint.glon;

                    System.out.println("随机选中的基准点："
                            + basePoint);

                    System.out.println("生成的新锚点："
                            + anchor);
                    Anchor anchorInstance = new Anchor(anchor.lat,anchor.lon);
                    List<TrackGeometry.GeoPoint> bdPoints =
                            new ArrayList<>();

                    for (Point point : points) {
                        double lat = point.lat;
                        double lng = point.lon;

                        if (!Double.isFinite(lat)
                                || !Double.isFinite(lng)
                                || (lat == 0.0 && lng == 0.0)) {
                            lat = point.glat;
                            lng = point.glon;
                        }

                        if (!Double.isFinite(lat)
                                || !Double.isFinite(lng)
                                || (lat == 0.0 && lng == 0.0)) {
                            lat = anchor.lat;
                            lng = anchor.lon;
                        }

                        bdPoints.add(new TrackGeometry.GeoPoint(lat, lng));
                    }
                    long originalStartMs = System.currentTimeMillis();
                    long startMs = originalStartMs + random.nextInt(5) * 1000L;
                    TrackModels.Track track = TrackGenerator.build(
                            args.distance,
                            args.duration,
                            args.seed.longValue(),
                            startMs,
                            bdPoints
                    );
                    String fivePointJson = ObsBuilder.fivePointWrapper(
                                    anchorInstance,
                                    points,
                                    track.startTime);
                    ObsBuilder.validateFivePointWrapper(fivePointJson);
                    System.out.println("[runningProcessor] Five Point JSON：");
                    System.out.println(fivePointJson);
                    RunBodyBuilder.Params params = new RunBodyBuilder.Params();

                    params.track = track;

                    params.fivePointJson = ObsBuilder.fivePointWrapper(
                            points,
                            track.startTime
                    );

                    params.weight = Session.weight == 0 ? 68 : Session.weight;
                    params.uid = Session.uid;
                    params.selectedUnid = Long.valueOf(Session.unid);
                    params.policy = policy.policy;
                    params.minDistance = policy.minDistance;
                    params.faceCheck = args.faceCheck;
                    params.address = Session.headerInformation.getCity();

                    RunBodyBuilder.Result result = RunBodyBuilder.build(
                            params,
                            RunBodyBuilder::androidTenSec,
                            new RunBodyBuilder.Signer() {
                                @Override
                                public String signature(
                                        JsonNode body,
                                        boolean hasRoomId
                                ) {
                                    return Sign.signature(body, hasRoomId);
                                }

                                @Override
                                public String originalSign(
                                        JsonNode body,
                                        boolean hasRoomId
                                ) {
                                    return Sign.originalSign(body, hasRoomId);
                                }
                            }
                    );

                    String bodyPlain = result.bodyPlain;
                    System.out.println(bodyPlain);
                    HeaderBuilder.BuiltHeader builtHeader = HeaderBuilder.buildAndroidHeader(Session.headerInformation,Session.uid,Session.token,null);
                    EnvelopeCrypto.BuiltEnvelope headerEnvironment = EnvelopeCrypto.buildEnvelope(Session.envelopeSession,builtHeader.getJson(), EnvelopeCrypto.OuterOrder.OBSERVED);
                    long nowMs = EnvelopeCrypto.nowMs();
                    EnvelopeCrypto.BuiltEnvelope bodyEnvironment = EnvelopeCrypto.buildEnvelopeTs(Session.envelopeSession,bodyPlain, EnvelopeCrypto.OuterOrder.INSERT,nowMs + 1);
                    String runes = String.valueOf(policy.timestamp) + Session.uid;
                    System.out.println("runes:" + runes);
                    String runef = result.runUuid + params.track.startTime;
                    System.out.println("runef:" + runef);
                    HttpURLConnection conn = (HttpURLConnection) new URL(NetworkHandler.HOST_URL + recordPath).openConnection();
                    System.out.println(headerEnvironment.getJson());
                    int status;
                    String raw;

                    try {
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(30_000);
                        conn.setReadTimeout(30_000);
                        conn.setDoOutput(true);

                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        conn.setRequestProperty("User-Agent", HeaderBuilder.androidUA);
                        conn.setRequestProperty("appVersion", "7.3.40");
                        conn.setRequestProperty("headerSign", headerEnvironment.getJson());
                        conn.setRequestProperty("runes", runes);
                        conn.setRequestProperty("runef", runef);

                        for (AdditionalHeader header : builtHeader.getAdditionalHeaders()) {
                            conn.setRequestProperty(header.key,header.value);
                            System.out.printf("key:[%s] value:[%s]\n",header.key,header.value);
                        }

                        byte[] body = bodyEnvironment.getJson().getBytes(StandardCharsets.UTF_8);
                        conn.setFixedLengthStreamingMode(body.length);

                        try (OutputStream out = conn.getOutputStream()) {
                            out.write(body);
                        }

                        status = conn.getResponseCode();

                        InputStream responseStream = status >= 400
                                ? conn.getErrorStream()
                                : conn.getInputStream();

                        raw = "";

                        if (responseStream != null) {
                            try (InputStream in = responseStream;
                                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                                byte[] chunk = new byte[4096];
                                int count;

                                while ((count = in.read(chunk)) != -1) {
                                    buffer.write(chunk, 0, count);
                                }

                                raw = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                            }
                        }
                        if (status >= 400) {
                            throw new IOException(
                                    "HTTP " + status + ": "
                                            + raw.substring(0, Math.min(raw.length(), 300))
                            );
                        }
                    } finally {
                        conn.disconnect();
                    }
                    System.out.println(raw);
                    byte[] key = ResponseDecryptor.derivePaesKey(bodyEnvironment.getKeyData()[0],bodyEnvironment.getKeyData()[1],bodyEnvironment.getKeyData()[2],bodyEnvironment.getKeyData()[3]);
                    ResponseDecryptor.Decrypted dec = ResponseDecryptor.decryptResponse(raw.getBytes(StandardCharsets.UTF_8),key,EnvelopeCrypto.rsaPublicKey());
                    System.out.println(dec.getBusiness());
                    JsonNode resultNode = MAPPER.readTree(dec.getBusiness());
                    long rrid = resultNode.findPath("data").get("rrid").longValue();
                    System.out.printf("rrid:[%d]\n",rrid);
                    if (rrid <= 0) {
                        throw new IOException("提交未返回有效 rrid：" + resultNode);
                    }

                    System.out.printf(
                            "[record] 提交成功 rrid=%d uuid=%s%n",
                            rrid,
                            result.runUuid
                    );

                    PostSubmitOutcome outcome = uploadObsAndVerify(
                            params.track,
                            rrid,
                            result.runUuid,
                            params.uid,
                            points
                    );

                    System.out.printf(
                            "[runningProcessor] rrid=%d OBS=%d/2 详情查询=%s%n",
                            rrid,
                            outcome.obsOk,
                            outcome.detailOk ? "成功" : "失败"
                    );

                    if (outcome.detail != null) {
                        System.out.println("[runningProcessor] 记录详情：" + outcome.detail);
                        JsonNode finalResult = outcome.detail;
                        StringBuilder sb = new StringBuilder();
                        String statusInfo = finalResult.get("statusInfo").textValue();
                        sb.append(statusInfo).append("\n");
                        sb.append("\n");
                        JsonNode reasonList = finalResult.findPath("reasonList");
                        ArrayList<Reasons> reasons = MAPPER.readValue(reasonList.traverse(),new TypeReference<ArrayList<Reasons>>(){});
                        for(Reasons reason : reasons){
                            sb.append(reason.complete ? "完成" : "未达标").append(": ").append(reason.reason).append("\n");
                        }
                        JOptionPane.showMessageDialog(null,sb.toString());
                    }
                }

            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        null,
                        "无法获取Point：" + e.getMessage()
                );
                e.printStackTrace();
            }
            if(points != null) {
                System.out.println(String.format("[runningProcessor] 获取到%d个点位", points.size()));
            }
        }
    }
    private static double clamp(
            double value,
            double min,
            double max
    ) {
        return Math.max(min, Math.min(max, value));
    }
    public static long calculateCalories(
            double weight,
            double km,
            long totalTime
    ) {
        double met;

        if (km <= 0.0) {
            met = 12.0;
        } else {
            // 配速，单位：分钟/公里
            double pace = (totalTime / 60.0) / km;

            if (pace < 10.0) {
                met = 33.0 / pace + 4.1;
            } else if (pace < 12.0) {
                met = 102.0 / pace - 3.7;
            } else {
                met = 33.6 / pace + 1.0;
            }
        }

        // MET先按0.1粒度四舍五入
        met = Math.round(met * 10.0) / 10.0;

        // 千卡 = 体重 × MET × 运动小时数，最后四舍五入
        return Math.round(
                weight * met * (totalTime / 3600.0)
        );
    }

    public static long avgPower(
            double weight,
            double totalDistance,
            long totalTime
    ) {
        if (totalTime <= 0) {
            return 0L;
        }

        return Math.round(
                1.1 * weight * (totalDistance / totalTime)
        );
    }
    public static double totalAscent(List<TrackModels.GenPoint> locations) {
        if (locations == null || locations.size() < 2) {
            return 0.0;
        }

        double ascent = 0.0;

        for (int i = 1; i < locations.size(); i++) {
            double difference =
                    locations.get(i).bdA
                            - locations.get(i - 1).bdA;

            if (difference > 0.0) {
                ascent += difference;
            }
        }

        return ascent;
    }
    private static PostSubmitOutcome uploadObsAndVerify(
            TrackModels.Track track,
            long rrid,
            String uuid,
            long uid,
            List<Point> points
    ) throws InterruptedException {

        Thread.sleep(1_000L);

        int obsOk = 0;

        try {
            System.out.println("[obs] 组装 OBS 对象（10 个字段分别 gzip+Base64）…");

            // 使用提交时的 track.startTime，不重新生成时间或 UUID。
            ObjectNode obsObject = ObsBuilder.buildObsObject(
                    track,
                    rrid,
                    uuid,
                    uid,
                    points
            );

            // 外层发送普通 JSON；
            // ObsBuilder 已经对每个字段完成 gzip+Base64。
            byte[] payload = MAPPER.writeValueAsBytes(obsObject);

            List<String> keys = ObsBuilder.obsKeys(
                    track,
                    rrid,
                    uuid
            );

            for (String objectKey : keys) {
                try {
                    String signedUrl = requestObsSignedUrl(objectKey);
                    putObsObject(signedUrl, payload);

                    obsOk++;
                    System.out.println("[obs] 上传成功：" + objectKey);

                } catch (Exception e) {
                    // 一个 key 失败后继续另一个，与 Rust 一致。
                    System.err.println(
                            "[obs] 上传失败：" + objectKey
                                    + "，原因：" + e.getMessage()
                    );
                }
            }

        } catch (Exception e) {
            // 记录已经提交成功，OBS 失败后仍继续查询详情。
            System.err.println("[obs] 对象构造或上传失败：" + e.getMessage());
        }

        System.out.printf("[obs] 上传成功 %d/2%n", obsOk);

        Thread.sleep(2_000L);

        JsonNode detail = null;
        boolean detailOk = false;

        System.out.println("[verify] 拉取详情验证…");

        try {
            detail = fetchOneRecord(rrid);
            detailOk = true;

            System.out.printf(
                    "[verify] rrid=%d complete=%s dis=%s time=%s%n",
                    rrid,
                    detail.path("complete"),
                    detail.path("totalDis"),
                    detail.path("totalTime")
            );

            // 查询成功和跑步达标分别记录。
            if (detail.path("complete").isBoolean()
                    && !detail.path("complete").booleanValue()) {

                System.out.println("[verify] 记录已保存，当前判定未达标");

                JsonNode reasons = detail.path("reasonList");

                if (reasons.isArray()) {
                    for (JsonNode reason : reasons) {
                        if (reason.path("complete").isBoolean()
                                && !reason.path("complete").booleanValue()) {

                            System.out.println(
                                    "[verify] 未达标原因："
                                            + reason.path("reason").asText()
                            );
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.printf(
                    "[verify] 详情查询失败（记录已提交 rrid=%d）：%s%n",
                    rrid,
                    e.getMessage()
            );
        }

        return new PostSubmitOutcome(obsOk, detailOk, detail);
    }

    /**
     * 对应 obs.rs::sign_url()。
     */
    private static String requestObsSignedUrl(
            String objectKey
    ) throws Exception {

        ObjectNode body = MAPPER.createObjectNode();
        body.put("bucketName", "iydsj-hbase-hot");
        body.put("objectKey", objectKey);
        body.put("method", "Put");
        body.put("contentType", "application/json");

        JsonNode business = new NetworkHandler().request(
                EnumRequestMethod.POST,
                OBS_SIGN_PATH,
                body.toString(),
                AdditionalHeader.EMPTY_HEADER
        );

        // 服务端 data 可能是对象，也可能是 JSON 字符串。
        JsonNode data = parseDataField(business);
        JsonNode signedUrl = data.get("signedUrl");

        // 与 Rust 一样，兼容顶层 signedUrl。
        if (signedUrl == null || !signedUrl.isTextual()) {
            signedUrl = business.get("signedUrl");
        }

        if (signedUrl == null
                || !signedUrl.isTextual()
                || signedUrl.asText().trim().isEmpty()) {

            throw new IOException("OBS 签名响应缺少 signedUrl");
        }

        return signedUrl.asText();
    }

    /**
     * 对应 obs.rs::put_object()。
     *
     * signedUrl 原样使用，不能重新编码其中的签名参数。
     */
    private static void putObsObject(
            String signedUrl,
            byte[] payload
    ) throws IOException {

        HttpURLConnection conn =
                (HttpURLConnection) new URL(signedUrl).openConnection();

        try {
            conn.setRequestMethod("PUT");
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(30_000);
            conn.setDoOutput(true);

            conn.setRequestProperty("Content-Type", "application/json");
            conn.setFixedLengthStreamingMode(payload.length);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int status = conn.getResponseCode();

            InputStream responseStream = status >= 400
                    ? conn.getErrorStream()
                    : conn.getInputStream();

            String response = readObsResponse(responseStream);

            if (status < 200 || status >= 300) {
                throw new IOException(
                        "OBS PUT HTTP " + status + "："
                                + response.substring(
                                0,
                                Math.min(response.length(), 300)
                        )
                );
            }

            System.out.println("[obs] PUT HTTP " + status);

        } finally {
            conn.disconnect();
        }
    }

    /**
     * 对应 records.rs::fetch_one_record()。
     * 返回解包后的记录详情。
     */
    private static JsonNode fetchOneRecord(
            long rrid
    ) throws Exception {

        ObjectNode body = MAPPER.createObjectNode();
        body.put("rrid", rrid);
        body.putNull("uuid");
        body.put("calculateBadge", false);

        JsonNode business = new NetworkHandler().request(
                EnumRequestMethod.POST,
                GET_ONE_RECORD_PATH,
                body.toString(),
                AdditionalHeader.EMPTY_HEADER
        );

        JsonNode detail = parseDataField(business);

        // 兼容 data 内部还有一层 data 的响应形式。
        if (detail.has("data")) {
            detail = parseDataField(detail);
        }

        if (detail.isNull() || detail.isMissingNode()) {
            throw new IOException("记录详情为空");
        }

        return detail;
    }

    /**
     * 对应 client.rs::parse_data_field()。
     */
    private static JsonNode parseDataField(
            JsonNode business
    ) throws IOException {

        JsonNode data = business.get("data");

        if (data == null || data.isNull()) {
            return MAPPER.nullNode();
        }

        if (data.isTextual()) {
            JsonNode parsed = MAPPER.readTree(data.textValue());
            return parsed == null ? MAPPER.nullNode() : parsed;
        }

        return data;
    }

    private static String readObsResponse(
            InputStream stream
    ) throws IOException {

        if (stream == null) {
            return "";
        }

        try (InputStream in = stream;
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] chunk = new byte[4096];
            int count;

            while ((count = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
            }

            return new String(
                    buffer.toByteArray(),
                    StandardCharsets.UTF_8
            );
        }
    }

    private static final class PostSubmitOutcome {

        final int obsOk;
        final boolean detailOk;
        final JsonNode detail;

        PostSubmitOutcome(
                int obsOk,
                boolean detailOk,
                JsonNode detail
        ) {
            this.obsOk = obsOk;
            this.detailOk = detailOk;
            this.detail = detail;
        }
    }
}
