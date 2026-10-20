package dev.onyxcat.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.onyxcat.track.ObsBuilder;
import dev.onyxcat.track.TrackModels.GenPoint;
import dev.onyxcat.track.TrackModels.Track;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static dev.onyxcat.track.TrackGeometry.round;

public final class RunBodyBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RunBodyBuilder() {
    }

    public static class Params {

        public Track track;
        public String fivePointJson;

        public double weight;
        public long uid;
        public long selectedUnid;
        public long policy;
        public long minDistance;
        public long faceCheck;
        public String address;

        public Params() {
        }
    }
    @FunctionalInterface
    public interface AndroidTenSecBuilder {

        ArrayNode build(
                Track track,
                long startMs,
                String kind
        ) throws Exception;
    }

    public interface Signer {

        String signature(
                JsonNode body,
                boolean option
        ) throws Exception;

        String originalSign(
                JsonNode body,
                boolean option
        ) throws Exception;
    }
    public static ArrayNode androidTenSec(
            Track track,
            long startMs,
            String kind
    ) {
        List<GenPoint> locations = track.locations;
        long totalTime = track.totalTime;

        ArrayNode output = MAPPER.createArrayNode();

        long ridSeed = 60000L;

        for (long window = 10L;
             window <= totalTime;
             window += 10L) {

            long low = window - 10L;
            long high = Math.min(window, totalTime);

            double distanceLow = 0.0;
            long stepsLow = 0L;

            double distanceHigh = 0.0;
            long stepsHigh = 0L;

            /*
             * 与Rust保持一致：
             * 按列表顺序扫描，取最后一个满足时间条件的点。
             * 不进行时间插值，也不提前结束扫描。
             */
            for (GenPoint point : locations) {
                long time = point.totalTime;

                if (time <= low) {
                    distanceLow = point.totalDis;
                    stepsLow = point.steps;
                }

                if (time <= high) {
                    distanceHigh = point.totalDis;
                    stepsHigh = point.steps;
                }
            }

            double distance = round(
                    Math.max(0.0, distanceHigh - distanceLow),
                    4
            );

            long steps = Math.max(
                    0L,
                    stepsHigh - stepsLow
            );

            long beginTime = startMs + low * 1000L;
            long endTime = startMs + high * 1000L;

            long queueNum = window / 10L - 1L;

            ObjectNode item = MAPPER.createObjectNode();

            if ("speed".equals(kind)) {
                item.put("beginTime", beginTime);
                item.put("distance", distance);
                item.put("endTime", endTime);
                item.put("flag", startMs);
                item.put("id", ridSeed + queueNum);
                item.put("queueNum", queueNum);
                item.put("state", 0);
            } else {
                item.put("avgDiff", 0.0);
                item.put("beginTime", beginTime);
                item.put("endTime", endTime);
                item.put("flag", startMs);
                item.put("id", ridSeed + queueNum);
                item.put("maxDiff", 0.0);
                item.put("minDiff", 1000.0);
                item.put("queueNum", queueNum);
                item.put("state", 0);
                item.put("stepsNum", steps);
            }

            output.add(item);
        }

        return output;
    }

    public static class Result {

        public final String runUuid;
        public final ObjectNode body;
        public final String bodyPlain;

        private Result(
                String runUuid,
                ObjectNode body,
                String bodyPlain
        ) {
            this.runUuid = runUuid;
            this.body = body;
            this.bodyPlain = bodyPlain;
        }
    }

    public static Result build(
            Params params,
            AndroidTenSecBuilder tenSecBuilder,
            Signer signer
    ) throws Exception {
        if (params == null || params.track == null) {
            throw new IllegalArgumentException("track不能为空");
        }

        if (tenSecBuilder == null || signer == null) {
            throw new IllegalArgumentException(
                    "时间窗生成器和签名器不能为空"
            );
        }

        Track track = params.track;

        track.validateConsistency();

        if (params.fivePointJson == null
                || params.fivePointJson.isEmpty()) {
            throw new IllegalArgumentException("五点轨迹不能为空");
        }

        ObsBuilder.validateFivePointWrapper(params.fivePointJson);

        long totalTime = track.totalTime;
        double totalDistance = track.totalDistance;
        long totalSteps = track.totalSteps;

        long startMs = track.startTime;
        long stopMs = startMs + totalTime * 1000L;

        double ascent = totalAscent(track.locations);

        long power = avgPower(
                params.weight,
                totalDistance,
                totalTime
        );

        long kcal = officialKcal(
                params.weight,
                totalTime,
                totalDistance
        );

        String runUuid = UUID.randomUUID()
                .toString()
                .toUpperCase(Locale.ROOT);

        // 距离向上取整到两位小数。
        double distanceCeil =
                Math.ceil(totalDistance * 100.0) / 100.0;

        long speed = (long) (
                round(
                        totalTime / distanceCeil * 50.0 / 3.0,
                        2
                ) * 1024.0
        );

        long avgStepFrequency = Math.max(
                1L,
                (long) round(
                        (double) totalSteps / totalTime * 60.0,
                        0
                )
        );

        ArrayNode speedWindows = tenSecBuilder.build(
                track,
                startMs,
                "speed"
        );

        ArrayNode stepWindows = tenSecBuilder.build(
                track,
                startMs,
                "steps"
        );

        if (speedWindows == null || stepWindows == null) {
            throw new IllegalArgumentException("10秒时间窗不能为空");
        }

        ObjectNode body = MAPPER.createObjectNode();

        body.put("allLocJson", "");
        body.put("sportType", 1);
        body.put("policy", params.policy);
        body.put("totalTime", totalTime);
        body.put("startTime", startMs);
        body.put("stopTime", stopMs);
        body.put("getPrize", false);
        body.put("status", 0);
        body.put("uuid", runUuid);
        body.put("uid", params.uid);
        body.put("selectedUnid", params.selectedUnid);
        body.put("selRunTime", totalTime);
        body.put("selDistance", params.minDistance);
        body.put("totalDis", (long) round(totalDistance, 0));
        body.put("speed", speed);
        body.put("validDis", (long) round(totalDistance, 0));
        body.put("validTime", totalTime);
        body.put("complete", true);
        body.put("unCompleteReason", 0);
        body.put("calorie", kcal);
        body.put("totalSteps", totalSteps);
        body.put("avgStepFreq", avgStepFrequency);
        body.put("useMobilityTools", 0);
        body.put("faceCheck", params.faceCheck);
        body.put("totalAscent", (long) round(ascent, 0));
        body.put("avgPower", power);

        body.set("speedPerTenSec", speedWindows);
        body.set("stepsPerTenSec", stepWindows);

        body.put("isUpload", false);
        body.put("more", false);

        // 使用校验后的轨迹顶层起点，不使用anchor替代。
        body.put("latitude", track.startLatitude);
        body.put("longitude", track.startLongitude);

        body.put("maxRunTime", 0);
        body.put("minSteps", 0);

        /*
         * 这里传入的是完整五点wrapper字符串。
         * 不要解析成对象后set进去。
         */
        body.put("fivePointJson", params.fivePointJson);

        body.put("errorCode", 0);
        body.put("geeToken", "");
        body.put("unauthorized", 0);
        body.put("themeId", 0);
        body.putNull("goalId");

        body.put(
                "address",
                params.address == null
                        ? ""
                        : params.address.strip()
        );
        String signature = signer.signature(
                body.deepCopy(),
                false
        );

        String originalSign = signer.originalSign(
                body.deepCopy(),
                false
        );

        body.put("signature", signature);
        body.put("originalSign", originalSign);

        String bodyPlain = toJson(body);

        return new Result(
                runUuid,
                body,
                bodyPlain
        );
    }

    /**
     * 累计爬升，单位米。
     */
    public static double totalAscent(List<GenPoint> locations) {
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

    /**
     * 平均功率，距离单位米，时间单位秒。
     */
    public static long avgPower(
            double weight,
            double totalDistance,
            long totalTime
    ) {
        if (totalTime <= 0L) {
            return 0L;
        }

        return Math.round(
                1.1 * weight * (totalDistance / totalTime)
        );
    }

    /**
     * 卡路里入口接收米，在方法内转换成公里。
     *
     * 注意与之前接收km的calculateCalories()区分。
     */
    public static long officialKcal(
            double weight,
            long totalTime,
            double totalDistance
    ) {
        double km = totalDistance / 1000.0;
        double met;

        if (km <= 0.0) {
            met = 12.0;
        } else {
            double pace = (totalTime / 60.0) / km;

            if (pace < 10.0) {
                met = 33.0 / pace + 4.1;
            } else if (pace < 12.0) {
                met = 102.0 / pace - 3.7;
            } else {
                met = 33.6 / pace + 1.0;
            }
        }

        met = Math.round(met * 10.0) / 10.0;

        return Math.round(
                weight * met * (totalTime / 3600.0)
        );
    }

    private static String toJson(
            JsonNode value
    ) throws JsonProcessingException {
        return MAPPER.writeValueAsString(value);
    }
}
