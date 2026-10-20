package dev.onyxcat.track;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.onyxcat.processor.Point;
import dev.onyxcat.track.TrackModels.GenPoint;
import dev.onyxcat.track.TrackModels.TenWindow;
import dev.onyxcat.track.TrackModels.Track;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static dev.onyxcat.track.TrackGeometry.round;

/**
 * OBS 对象及 Five Point JSON 组装工具。
 *
 * Java 11兼容，不使用record。
 */
public final class ObsBuilder {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static final double X_PI =
            Math.PI * 3000.0 / 180.0;

    private static final DateTimeFormatter OBS_HOUR_FORMAT =
            DateTimeFormatter
                    .ofPattern("yyyyMMddHH")
                    .withZone(ZoneId.systemDefault());

    private ObsBuilder() {
    }

    /**
     * 经纬度坐标。
     */
    public static final class Coordinate {

        private final double lat;
        private final double lng;

        public Coordinate(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public double getLat() {
            return lat;
        }

        public double getLng() {
            return lng;
        }
    }

    /**
     * 百度BD-09坐标转换为高德GCJ-02坐标。
     *
     * @param bdLat BD-09纬度
     * @param bdLng BD-09经度
     * @return GCJ-02坐标
     */
    public static Coordinate bd09ToGcj02(
            double bdLat,
            double bdLng
    ) {
        double x = bdLng - 0.0065;
        double y = bdLat - 0.006;

        double z = Math.sqrt(x * x + y * y)
                - 0.00002 * Math.sin(y * X_PI);

        double theta = Math.atan2(y, x)
                - 0.000003 * Math.cos(x * X_PI);

        double gcjLat = z * Math.sin(theta);
        double gcjLng = z * Math.cos(theta);

        return new Coordinate(gcjLat, gcjLng);
    }

    /**
     * 将字节数据进行gzip压缩，再转换成Base64。
     */
    public static String gzipBase64(byte[] data) {
        if (data == null) {
            data = new byte[0];
        }

        try {
            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            try (GZIPOutputStream gzip =
                         new GZIPOutputStream(output)) {
                gzip.write(data);
            }

            return Base64.getEncoder()
                    .encodeToString(output.toByteArray());

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "gzip压缩失败",
                    exception
            );
        }
    }

    /**
     * 字符串进行gzip+Base64。
     */
    public static String gzipString(String value) {
        if (value == null) {
            value = "";
        }

        return gzipBase64(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Jackson节点序列化后进行gzip+Base64。
     *
     * 对应Rust中的：
     *
     * gz_json(v)
     */
    public static String gzipJson(JsonNode value) {
        if (value == null) {
            value = MAPPER.nullNode();
        }

        /*
         * JsonNode.toString()输出紧凑JSON，
         * 对应serde_json::Value::to_string()。
         */
        return gzipString(value.toString());
    }

    /**
     * 将生成器中的轨迹点转换为OBS所需的27键协议点。
     *
     * 轨迹生成器中的gLat/gLng是BD-09，
     * 写入OBS前转换成GCJ-02。
     */
    public static ObjectNode convertPoint(
            GenPoint point,
            long startMs
    ) {
        if (point == null) {
            throw new IllegalArgumentException(
                    "轨迹点不能为空"
            );
        }

        Coordinate gcj = bd09ToGcj02(
                point.gLat,
                point.gLng
        );

        ObjectNode result = MAPPER.createObjectNode();

        result.put("avgSpeed", round(point.avgSpeed, 4));
        result.put("bdA", round(point.bdA, 2));
        result.put("bdD", round(point.bdD, 2));
        result.put("bdG", point.bdG);
        result.put("bdS", round(point.bdS, 4));
        result.put("coorType", "gcj02");
        result.put("count", point.count);
        result.put("dtr", 0.0);
        result.put("flag", startMs);
        result.put("gLat", round(gcj.getLat(), 7));
        result.put("gLng", round(gcj.getLng(), 7));
        result.put(
                "gainTime",
                point.gainTime == null ? "" : point.gainTime
        );
        result.put("id", point.id);
        result.put("lat", -1.0);
        result.put("lng", -1.0);
        result.put("locType", point.locType);
        result.put("locationId", "");
        result.put("queueNum", point.queueNum);
        result.put("radius", round(point.radius, 2));
        result.put("speed", round(point.speed, 4));
        result.put("state", point.state);
        result.put("stepDistance", 0.0);
        result.put("totalDis", round(point.totalDis, 4));
        result.put("totalTime", point.totalTime);
        result.put("type", point.ptype);
        result.put("validDis", round(point.validDis, 4));
        result.put("validTime", point.validTime);

        return result;
    }

    /**
     * 根据实时打卡点生成跑完状态的Five Point数组。
     *
     * 注意：
     * 1. 这里不进行BD→GCJ转换；
     * 2. 直接使用Point中的lat/lon/glat/glon；
     * 3. 所有点的flag统一为track.startTime；
     * 4. isPass固定为true；
     * 5. position固定为999；
     * 6. state固定为0。
     */
    public static ArrayNode fivePointPayload(
            List<Point> points,
            long startMs
    ) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException(
                    "Five Point点位不能为空"
            );
        }

        ArrayNode array = MAPPER.createArrayNode();

        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);

            if (point == null) {
                throw new IllegalArgumentException(
                        "Five Point中存在空点，索引：" + i
                );
            }

            validateFivePointCoordinate(point, i);

            ObjectNode item = MAPPER.createObjectNode();

            item.put("flag", startMs);
            item.put("glat", point.glat);
            item.put("glon", point.glon);
            item.put("id", i + 1L);
            item.put("isFixed", point.isFixed);
            item.put("isPass", true);
            item.put("lat", point.lat);
            item.put("lon", point.lon);
            item.put(
                    "pointName",
                    point.pointName == null
                            ? ""
                            : point.pointName
            );
            item.put("position", 999);
            item.put("state", 0);

            array.add(item);
        }

        return array;
    }

    /**
     * 根据anchor和points生成Five Point。
     *
     * 原Rust协议中anchor不会写入Five Point JSON。
     * 这里anchor仅用于点位坐标缺失时兜底。
     *
     * 如果不希望自动兜底，可以直接调用：
     *
     * fivePointPayload(points, startMs)
     */
    public static ArrayNode fivePointPayload(
            Anchor anchor,
            List<Point> points,
            long startMs
    ) {
        List<Point> normalized =
                normalizeFivePoints(anchor, points);

        return fivePointPayload(normalized, startMs);
    }

    /**
     * 生成提交body中的fivePointJson包装字符串。
     *
     * 最终结构：
     *
     * {
     *   "useZip": false,
     *   "fivePointJson": "[{...},{...}]",
     *   "runAreaId": -1,
     *   "geoFencesJson": "[]",
     *   "freedomShowFence": false
     * }
     *
     * 注意fivePointJson的值是字符串，不是直接的JSON数组。
     */
    public static String fivePointWrapper(
            List<Point> points,
            long startMs
    ) {
        ArrayNode five = fivePointPayload(
                points,
                startMs
        );

        ObjectNode wrapper = createFivePointWrapperNode(five);

        return wrapper.toString();
    }

    /**
     * 使用anchor进行缺失坐标兜底，再生成Five Point包装字符串。
     */
    public static String fivePointWrapper(
            Anchor anchor,
            List<Point> points,
            long startMs
    ) {
        ArrayNode five = fivePointPayload(
                anchor,
                points,
                startMs
        );

        ObjectNode wrapper = createFivePointWrapperNode(five);

        return wrapper.toString();
    }

    private static ObjectNode createFivePointWrapperNode(
            ArrayNode five
    ) {
        ObjectNode wrapper = MAPPER.createObjectNode();

        wrapper.put("useZip", false);

        /*
         * 必须调用toString()，把数组变成字符串。
         */
        wrapper.put("fivePointJson", five.toString());

        wrapper.put("runAreaId", -1);
        wrapper.put("geoFencesJson", "[]");
        wrapper.put("freedomShowFence", false);

        return wrapper;
    }

    /**
     * 校验Five Point包装字符串。
     */
    public static void validateFivePointWrapper(
            String wrapper
    ) {
        if (wrapper == null || wrapper.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "五点轨迹JSON不能为空"
            );
        }

        final JsonNode outer;

        try {
            outer = MAPPER.readTree(wrapper);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "五点轨迹JSON无效："
                            + exception.getMessage(),
                    exception
            );
        }

        JsonNode rawNode = outer.get("fivePointJson");

        if (rawNode == null || !rawNode.isTextual()) {
            throw new IllegalArgumentException(
                    "五点轨迹缺少fivePointJson"
            );
        }

        final JsonNode points;

        try {
            points = MAPPER.readTree(rawNode.asText());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "五点轨迹数组无效："
                            + exception.getMessage(),
                    exception
            );
        }

        if (points == null
                || !points.isArray()
                || points.size() == 0) {
            throw new IllegalArgumentException(
                    "五点轨迹不能为空"
            );
        }

        for (int i = 0; i < points.size(); i++) {
            JsonNode point = points.get(i);

            double lat = point.path("lat").asDouble(0.0);
            double lon = point.path("lon").asDouble(0.0);
            double glat = point.path("glat").asDouble(0.0);
            double glon = point.path("glon").asDouble(0.0);

            if (lat == 0.0 && lon == 0.0) {
                throw new IllegalArgumentException(
                        "第" + (i + 1)
                                + "个五点轨迹缺少lat/lon"
                );
            }

            if (glat == 0.0 && glon == 0.0) {
                throw new IllegalArgumentException(
                        "第" + (i + 1)
                                + "个五点轨迹缺少glat/glon"
                );
            }

            validateCoordinate(
                    lat,
                    lon,
                    "第" + (i + 1) + "个五点原始坐标"
            );

            validateCoordinate(
                    glat,
                    glon,
                    "第" + (i + 1) + "个五点定位坐标"
            );
        }
    }

    /**
     * 构造10秒速度窗口和步频窗口。
     */
    private static WindowResult buildWindows(
            Track track,
            long rrid
    ) {
        ArrayNode speedArray =
                MAPPER.createArrayNode();

        ArrayNode stepFrequencyArray =
                MAPPER.createArrayNode();

        long startMs = track.startTime;
        long totalTime = track.totalTime;

        int speedSize = track.speedPerTenSec == null
                ? 0
                : track.speedPerTenSec.size();

        int stepSize = track.stepsPerTenSec == null
                ? 0
                : track.stepsPerTenSec.size();

        /*
         * Rust代码默认两个数组长度一致并直接按同一个索引读取。
         * Java这里显式校验，避免IndexOutOfBoundsException。
         */
        if (speedSize != stepSize) {
            throw new IllegalArgumentException(
                    "speedPerTenSec与stepsPerTenSec长度不一致："
                            + speedSize
                            + " != "
                            + stepSize
            );
        }

        for (int i = 0; i < speedSize; i++) {
            TenWindow speed =
                    track.speedPerTenSec.get(i);

            TenWindow steps =
                    track.stepsPerTenSec.get(i);

            long lowSeconds = i * 10L;
            long highSeconds = i * 10L + 10L;

            highSeconds = Math.min(
                    highSeconds,
                    totalTime
            );

            long id = Math.floorMod(rrid, 100000L)
                    * 1000L
                    + highSeconds;

            ObjectNode speedItem =
                    MAPPER.createObjectNode();

            speedItem.put(
                    "beginTime",
                    startMs + lowSeconds * 1000L
            );
            speedItem.put("distance", speed.value);
            speedItem.put(
                    "endTime",
                    startMs + highSeconds * 1000L
            );
            speedItem.put("flag", startMs);
            speedItem.put("id", id);
            speedItem.put("queueNum", 0);
            speedItem.put("state", 0);

            speedArray.add(speedItem);

            ObjectNode stepItem =
                    MAPPER.createObjectNode();

            stepItem.put("avgDiff", 0.0);
            stepItem.put(
                    "beginTime",
                    startMs + lowSeconds * 1000L
            );
            stepItem.put(
                    "endTime",
                    startMs + highSeconds * 1000L
            );
            stepItem.put("flag", startMs);
            stepItem.put("id", id);
            stepItem.put("maxDiff", 0.0);
            stepItem.put("minDiff", 1000.0);
            stepItem.put("queueNum", 0);
            stepItem.put("state", 0);
            stepItem.put(
                    "stepsNum",
                    (long) steps.value
            );

            stepFrequencyArray.add(stepItem);
        }

        return new WindowResult(
                speedArray,
                stepFrequencyArray
        );
    }

    /**
     * 每1000米生成一圈。
     *
     * 最后一圈即使不足1000米，也会生成，
     * 此时isFullLap=false。
     */
    public static ArrayNode buildLaps(
            Track track,
            long startMs
    ) {
        validateTrack(track);

        ArrayNode laps = MAPPER.createArrayNode();

        List<GenPoint> locations =
                track.locations;

        double previousDistance = 0.0;
        long previousTime = 0L;
        long previousSteps = 0L;
        double elevationGain = 0.0;

        double initialAltitude =
                locations.isEmpty()
                        ? 0.0
                        : locations.get(0).bdA;

        for (int i = 0; i < locations.size(); i++) {
            GenPoint point = locations.get(i);

            if (i > 0) {
                GenPoint previous =
                        locations.get(i - 1);

                double altitudeDifference =
                        point.bdA - previous.bdA;

                if (altitudeDifference > 0.0) {
                    elevationGain += altitudeDifference;
                }
            }

            double currentDistance = point.totalDis;
            long currentTime = point.totalTime;

            boolean isLast =
                    i == locations.size() - 1;

            if (currentDistance - previousDistance >= 1000.0
                    || isLast) {
                double lapDistance =
                        currentDistance - previousDistance;

                long lapTime = Math.max(
                        1L,
                        currentTime - previousTime
                );

                long lapSteps =
                        point.steps - previousSteps;

                double avgCadence =
                        lapSteps
                                / (lapTime / 60.0);

                double distanceKilometers =
                        Math.max(
                                lapDistance / 1000.0,
                                0.001
                        );

                double avgPace =
                        (lapTime / 60.0)
                                / distanceKilometers;

                long safeSteps =
                        Math.max(1L, lapSteps);

                double avgStride =
                        lapDistance
                                / safeSteps
                                * 100.0;

                long lapIndex =
                        laps.size() + 1L;

                ObjectNode lap =
                        MAPPER.createObjectNode();

                lap.put(
                        "avgCadence",
                        round(avgCadence, 2)
                );
                lap.put(
                        "avgPace",
                        round(avgPace, 2)
                );
                lap.put(
                        "avgStride",
                        round(avgStride, 2)
                );
                lap.put(
                        "cumulativeDuration",
                        currentTime
                );
                lap.put(
                        "distance",
                        round(lapDistance, 4)
                );
                lap.put("duration", lapTime);
                lap.put(
                        "elevationGain",
                        round(elevationGain, 2)
                );
                lap.put(
                        "endAltAbs",
                        round(point.bdA, 2)
                );
                lap.put(
                        "endAltRel",
                        round(
                                point.bdA - initialAltitude,
                                2
                        )
                );
                lap.put("flag", startMs);
                lap.put("id", lapIndex);
                lap.put(
                        "isFullLap",
                        lapDistance >= 1000.0
                );
                lap.put("lapIndex", lapIndex);
                lap.put("step", lapSteps);

                laps.add(lap);

                previousDistance = currentDistance;
                previousTime = currentTime;
                previousSteps = point.steps;
                elevationGain = 0.0;
            }
        }

        return laps;
    }

    /**
     * 组装OBS的10键对象。
     *
     * 每个值均为gzip+Base64字符串。
     */
    public static ObjectNode buildObsObject(
            Track track,
            long rrid,
            String uuid,
            long uid,
            List<Point> livePoints
    ) {
        validateTrack(track);

        if (uuid == null) {
            uuid = "";
        }

        if (livePoints == null || livePoints.isEmpty()) {
            throw new IllegalArgumentException(
                    "OBS Five Point不能为空"
            );
        }

        long startMs = track.startTime;

        /*
         * 轨迹点转换成OBS协议点。
         */
        ArrayNode convertedPoints =
                MAPPER.createArrayNode();

        for (GenPoint point : track.locations) {
            convertedPoints.add(
                    convertPoint(point, startMs)
            );
        }

        /*
         * allLocJson仍然是字符串，而不是直接数组。
         */
        ObjectNode runWrapper =
                MAPPER.createObjectNode();

        runWrapper.put(
                "allLocJson",
                convertedPoints.toString()
        );
        runWrapper.put("useZip", false);

        WindowResult windows =
                buildWindows(track, rrid);

        ArrayNode laps =
                buildLaps(track, startMs);

        ArrayNode five =
                fivePointPayload(
                        livePoints,
                        startMs
                );

        ObjectNode fixedPoint =
                createFivePointWrapperNode(five);

        ObjectNode result =
                MAPPER.createObjectNode();

        /*
         * 字段顺序与Rust json!保持一致。
         */
        result.put(
                "rrid",
                gzipString(Long.toString(rrid))
        );
        result.put(
                "uuid",
                gzipString(uuid)
        );
        result.put(
                "uid",
                gzipString(Long.toString(uid))
        );
        result.put(
                "run_data",
                gzipJson(runWrapper)
        );
        result.put(
                "fixed_point_json",
                gzipJson(fixedPoint)
        );
        result.put(
                "segment_json",
                gzipString("")
        );
        result.put(
                "speed_json",
                gzipJson(windows.speed)
        );
        result.put(
                "step_freq_json",
                gzipJson(windows.stepFrequency)
        );
        result.put(
                "laps_json",
                gzipJson(laps)
        );
        result.put(
                "runFaceCheck",
                gzipString("")
        );

        return result;
    }

    /**
     * 使用anchor对Five Point缺失坐标兜底，
     * 然后生成OBS对象。
     */
    public static ObjectNode buildObsObject(
            Track track,
            long rrid,
            String uuid,
            long uid,
            Anchor anchor,
            List<Point> livePoints
    ) {
        List<Point> normalized =
                normalizeFivePoints(
                        anchor,
                        livePoints
                );

        return buildObsObject(
                track,
                rrid,
                uuid,
                uid,
                normalized
        );
    }

    /**
     * 直接生成OBS JSON文本。
     */
    public static String buildObsJson(
            Track track,
            long rrid,
            String uuid,
            long uid,
            List<Point> livePoints
    ) {
        return buildObsObject(
                track,
                rrid,
                uuid,
                uid,
                livePoints
        ).toString();
    }

    /**
     * 使用anchor进行坐标兜底后生成OBS JSON文本。
     */
    public static String buildObsJson(
            Track track,
            long rrid,
            String uuid,
            long uid,
            Anchor anchor,
            List<Point> livePoints
    ) {
        return buildObsObject(
                track,
                rrid,
                uuid,
                uid,
                anchor,
                livePoints
        ).toString();
    }

    /**
     * 生成两个OBS objectKey。
     */
    public static List<String> obsKeys(
            Track track,
            long rrid,
            String uuid
    ) {
        validateTrack(track);

        if (uuid == null) {
            uuid = "";
        }

        String hour = OBS_HOUR_FORMAT.format(
                Instant.ofEpochMilli(track.startTime)
        );

        List<String> result =
                new ArrayList<String>(2);

        result.add(
                "run_data/"
                        + hour
                        + "/"
                        + uuid
                        + ".json"
        );

        result.add(
                "run_data/"
                        + (rrid / 1_000_000L)
                        + "/"
                        + rrid
                        + ".json"
        );

        return result;
    }

    /**
     * anchor只作为坐标缺失时的兜底。
     *
     * 不会把anchor额外写入Five Point JSON。
     */
    private static List<Point> normalizeFivePoints(
            Anchor anchor,
            List<Point> source
    ) {
        if (anchor == null) {
            throw new IllegalArgumentException(
                    "anchor不能为空"
            );
        }

        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException(
                    "points不能为空"
            );
        }

        validateCoordinate(
                anchor.latitude,
                anchor.longitude,
                "anchor"
        );

        List<Point> result =
                new ArrayList<Point>(source.size());

        for (int i = 0; i < source.size(); i++) {
            Point original = source.get(i);

            if (original == null) {
                throw new IllegalArgumentException(
                        "points中存在空点，索引：" + i
                );
            }

            Point copy = new Point();

            copy.isFixed = original.isFixed;
            copy.pointName = original.pointName;

            /*
             * 原始lat/lon无效时：
             * 先使用glat/glon；
             * 两者都无效时使用anchor。
             */
            if (isUsableCoordinate(
                    original.lat,
                    original.lon
            )) {
                copy.lat = original.lat;
                copy.lon = original.lon;
            } else if (isUsableCoordinate(
                    original.glat,
                    original.glon
            )) {
                copy.lat = original.glat;
                copy.lon = original.glon;
            } else {
                copy.lat = anchor.latitude;
                copy.lon = anchor.longitude;
            }

            /*
             * glat/glon无效时：
             * 回退到最终确定的lat/lon。
             */
            if (isUsableCoordinate(
                    original.glat,
                    original.glon
            )) {
                copy.glat = original.glat;
                copy.glon = original.glon;
            } else {
                copy.glat = copy.lat;
                copy.glon = copy.lon;
            }

            result.add(copy);
        }

        return result;
    }

    private static void validateFivePointCoordinate(
            Point point,
            int index
    ) {
        if (!isUsableCoordinate(point.lat, point.lon)) {
            throw new IllegalArgumentException(
                    "第" + (index + 1)
                            + "个打卡点lat/lon无效："
                            + point.lat
                            + ","
                            + point.lon
            );
        }

        if (!isUsableCoordinate(point.glat, point.glon)) {
            throw new IllegalArgumentException(
                    "第" + (index + 1)
                            + "个打卡点glat/glon无效："
                            + point.glat
                            + ","
                            + point.glon
            );
        }
    }

    private static boolean isUsableCoordinate(
            double lat,
            double lng
    ) {
        if (!Double.isFinite(lat)
                || !Double.isFinite(lng)) {
            return false;
        }

        if (lat < -90.0 || lat > 90.0) {
            return false;
        }

        if (lng < -180.0 || lng > 180.0) {
            return false;
        }

        return !(lat == 0.0 && lng == 0.0);
    }

    private static void validateCoordinate(
            double lat,
            double lng,
            String name
    ) {
        if (!Double.isFinite(lat)
                || !Double.isFinite(lng)) {
            throw new IllegalArgumentException(
                    name + "不是有限数"
            );
        }

        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException(
                    name + "纬度超出范围：" + lat
            );
        }

        if (lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException(
                    name + "经度超出范围：" + lng
            );
        }
    }

    private static void validateTrack(Track track) {
        if (track == null) {
            throw new IllegalArgumentException(
                    "track不能为空"
            );
        }

        if (track.locations == null
                || track.locations.isEmpty()) {
            throw new IllegalArgumentException(
                    "track.locations不能为空"
            );
        }

        if (track.speedPerTenSec == null) {
            throw new IllegalArgumentException(
                    "track.speedPerTenSec不能为空"
            );
        }

        if (track.stepsPerTenSec == null) {
            throw new IllegalArgumentException(
                    "track.stepsPerTenSec不能为空"
            );
        }
    }

    private static final class WindowResult {

        private final ArrayNode speed;
        private final ArrayNode stepFrequency;

        private WindowResult(
                ArrayNode speed,
                ArrayNode stepFrequency
        ) {
            this.speed = speed;
            this.stepFrequency = stepFrequency;
        }
    }
}
