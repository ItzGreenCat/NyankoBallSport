package dev.onyxcat.track;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 轨迹几何、坐标转换和随机工具。
 */
public final class TrackGeometry {

    public static final double MET_PER_DEG_LAT = 111_132.0;
    public static final double MET_PER_DEG_LNG = 86_600.0;

    private static final double X_PI =
            Math.PI * 3000.0 / 180.0;

    private static final double WGS_A = 6378245.0;

    private static final double WGS_EE =
            0.00669342162296594323;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private TrackGeometry() {
    }

    /**
     * 经纬度点，字段顺序为纬度、经度。
     */
    public static final class GeoPoint {

        private final double lat;
        private final double lng;

        public GeoPoint(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public double lat() {
            return lat;
        }

        public double lng() {
            return lng;
        }

        public double getLat() {
            return lat;
        }

        public double getLng() {
            return lng;
        }

        @Override
        public String toString() {
            return "GeoPoint{" +
                    "lat=" + lat +
                    ", lng=" + lng +
                    '}';
        }
    }

    /**
     * 平面坐标点，单位为米。
     *
     * x：东西方向；
     * y：南北方向。
     */
    public static final class PlanePoint {

        private final double x;
        private final double y;

        public PlanePoint(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        @Override
        public String toString() {
            return "PlanePoint{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }
    }

    /**
     * 闭合曲线及弧长表。
     */
    public static final class PointRing {

        private final List<PlanePoint> dense;
        private final double[] arcs;
        private final GeoPoint center;

        public PointRing(
                List<PlanePoint> dense,
                double[] arcs,
                GeoPoint center
        ) {
            this.dense = dense;
            this.arcs = arcs;
            this.center = center;
        }

        public List<PlanePoint> dense() {
            return dense;
        }

        public double[] arcs() {
            return arcs;
        }

        public GeoPoint center() {
            return center;
        }

        public List<PlanePoint> getDense() {
            return dense;
        }

        public double[] getArcs() {
            return arcs;
        }

        public GeoPoint getCenter() {
            return center;
        }

        public double length() {
            if (arcs == null || arcs.length == 0) {
                return 0.0;
            }
            return arcs[arcs.length - 1];
        }
    }

    public static final class WeightedValue<T> {

        private final T value;
        private final int weight;

        public WeightedValue(T value, int weight) {
            this.value = value;
            this.weight = weight;
        }

        public T value() {
            return value;
        }

        public int weight() {
            return weight;
        }
    }

    /**
     * 可通过种子复现的随机数生成器。
     */
    public static final class SeededRandom {

        private final Random random;

        public SeededRandom(long seed) {
            this.random = new Random(seed);
        }

        public double random() {
            return random.nextDouble();
        }

        public double uniform(double min, double max) {
            if (!Double.isFinite(min)
                    || !Double.isFinite(max)
                    || max < min) {
                throw new IllegalArgumentException(
                        "非法随机区间：" + min + " ~ " + max
                );
            }

            if (min == max) {
                return min;
            }

            return min + random.nextDouble() * (max - min);
        }

        public long randint(long min, long max) {
            if (max < min) {
                throw new IllegalArgumentException(
                        "max不能小于min"
                );
            }

            long bound = max - min + 1L;

            if (bound > 0L && bound <= Integer.MAX_VALUE) {
                return min + random.nextInt((int) bound);
            }

            long value;

            do {
                value = random.nextLong();
            } while (value == Long.MIN_VALUE);

            value = Math.abs(value);

            return min + value % bound;
        }

        public double gauss(
                double mean,
                double standardDeviation
        ) {
            if (standardDeviation < 0.0) {
                throw new IllegalArgumentException(
                        "标准差不能小于0"
                );
            }

            return mean
                    + random.nextGaussian() * standardDeviation;
        }

        @SafeVarargs
        public final <T> T choice(T... values) {
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException(
                        "候选项不能为空"
                );
            }

            return values[random.nextInt(values.length)];
        }

        public <T> T choice(List<T> values) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException(
                        "候选项不能为空"
                );
            }

            return values.get(
                    random.nextInt(values.size())
            );
        }

        public <T> T weighted(
                List<WeightedValue<T>> values
        ) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException(
                        "权重列表不能为空"
                );
            }

            int total = 0;

            for (WeightedValue<T> item : values) {
                if (item.weight() < 0) {
                    throw new IllegalArgumentException(
                            "权重不能小于0"
                    );
                }

                total += item.weight();
            }

            if (total <= 0) {
                throw new IllegalArgumentException(
                        "总权重必须大于0"
                );
            }

            double value = uniform(0.0, total);

            for (WeightedValue<T> item : values) {
                value -= item.weight();

                if (value < 0.0) {
                    return item.value();
                }
            }

            return values.get(values.size() - 1).value();
        }
    }

    public static double round(double value, int scale) {
        if (!Double.isFinite(value)) {
            return value;
        }

        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static double clamp(
            double value,
            double min,
            double max
    ) {
        return Math.max(min, Math.min(max, value));
    }

    public static String formatGainTime(long milliseconds) {
        return TIME_FORMATTER.format(
                Instant.ofEpochMilli(milliseconds)
        );
    }

    /**
     * 使用打卡点生成 Catmull-Rom 闭合曲线。
     */
    public static PointRing makePointRing(
            List<GeoPoint> bdPoints
    ) {
        if (bdPoints == null || bdPoints.size() < 2) {
            throw new IllegalArgumentException(
                    "至少需要两个打卡点"
            );
        }

        double centerLat = 0.0;
        double centerLng = 0.0;

        for (GeoPoint point : bdPoints) {
            if (point == null) {
                throw new IllegalArgumentException(
                        "打卡点不能为null"
                );
            }

            centerLat += point.lat();
            centerLng += point.lng();
        }

        centerLat /= bdPoints.size();
        centerLng /= bdPoints.size();

        final double finalCenterLat = centerLat;
        final double finalCenterLng = centerLng;

        List<GeoPoint> ordered =
                new ArrayList<GeoPoint>(bdPoints);

        ordered.sort(new Comparator<GeoPoint>() {
            @Override
            public int compare(GeoPoint a, GeoPoint b) {
                double angleA = Math.atan2(
                        a.lat() - finalCenterLat,
                        a.lng() - finalCenterLng
                );

                double angleB = Math.atan2(
                        b.lat() - finalCenterLat,
                        b.lng() - finalCenterLng
                );

                return Double.compare(angleA, angleB);
            }
        });

        List<PlanePoint> plane =
                new ArrayList<PlanePoint>(ordered.size());

        for (GeoPoint point : ordered) {
            double x =
                    (point.lng() - centerLng)
                            * MET_PER_DEG_LNG;

            double y =
                    (point.lat() - centerLat)
                            * MET_PER_DEG_LAT;

            plane.add(new PlanePoint(x, y));
        }

        int pointCount = plane.size();
        int samplesPerSegment = 18;

        List<PlanePoint> dense =
                new ArrayList<PlanePoint>(
                        pointCount * samplesPerSegment
                );

        for (int i = 0; i < pointCount; i++) {
            PlanePoint p0 = plane.get(
                    (i + pointCount - 1) % pointCount
            );

            PlanePoint p1 = plane.get(i);

            PlanePoint p2 = plane.get(
                    (i + 1) % pointCount
            );

            PlanePoint p3 = plane.get(
                    (i + 2) % pointCount
            );

            for (int j = 0; j < samplesPerSegment; j++) {
                double t =
                        (double) j / samplesPerSegment;

                double t2 = t * t;
                double t3 = t2 * t;

                double x = 0.5 * (
                        2.0 * p1.x()
                                + (-p0.x() + p2.x()) * t
                                + (
                                2.0 * p0.x()
                                        - 5.0 * p1.x()
                                        + 4.0 * p2.x()
                                        - p3.x()
                        ) * t2
                                + (
                                -p0.x()
                                        + 3.0 * p1.x()
                                        - 3.0 * p2.x()
                                        + p3.x()
                        ) * t3
                );

                double y = 0.5 * (
                        2.0 * p1.y()
                                + (-p0.y() + p2.y()) * t
                                + (
                                2.0 * p0.y()
                                        - 5.0 * p1.y()
                                        + 4.0 * p2.y()
                                        - p3.y()
                        ) * t2
                                + (
                                -p0.y()
                                        + 3.0 * p1.y()
                                        - 3.0 * p2.y()
                                        + p3.y()
                        ) * t3
                );

                dense.add(new PlanePoint(x, y));
            }
        }

        double[] arcs =
                new double[dense.size() + 1];

        arcs[0] = 0.0;

        for (int i = 1; i <= dense.size(); i++) {
            PlanePoint a = dense.get(i - 1);
            PlanePoint b = dense.get(i % dense.size());

            arcs[i] = arcs[i - 1] + Math.hypot(
                    b.x() - a.x(),
                    b.y() - a.y()
            );
        }

        return new PointRing(
                dense,
                arcs,
                new GeoPoint(centerLat, centerLng)
        );
    }

    /**
     * 根据闭合曲线弧长取得坐标。
     */
    public static PlanePoint ringPointAt(
            PointRing ring,
            double distance
    ) {
        if (ring == null
                || ring.dense() == null
                || ring.dense().isEmpty()) {
            throw new IllegalArgumentException(
                    "轨迹环不能为空"
            );
        }

        List<PlanePoint> dense = ring.dense();
        double[] arcs = ring.arcs();

        double total = arcs[arcs.length - 1];

        if (total <= 0.0) {
            return dense.get(0);
        }

        double normalized =
                ((distance % total) + total) % total;

        int low = 0;
        int high = arcs.length;

        while (low < high) {
            int middle = (low + high) >>> 1;

            if (arcs[middle] < normalized) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }

        int index = Math.max(1, low);

        if (index >= arcs.length) {
            index = arcs.length - 1;
        }

        PlanePoint a = dense.get(
                (index - 1) % dense.size()
        );

        PlanePoint b = dense.get(
                index % dense.size()
        );

        double segmentLength =
                arcs[index] - arcs[index - 1];

        double ratio = segmentLength > 0.0
                ? (normalized - arcs[index - 1])
                / segmentLength
                : 0.0;

        return new PlanePoint(
                a.x() + (b.x() - a.x()) * ratio,
                a.y() + (b.y() - a.y()) * ratio
        );
    }

    public static GeoPoint toBd(
            double x,
            double y,
            GeoPoint center
    ) {
        return new GeoPoint(
                center.lat() + y / MET_PER_DEG_LAT,
                center.lng() + x / MET_PER_DEG_LNG
        );
    }

    private static boolean outOfChina(
            double lat,
            double lng
    ) {
        return lng < 72.004
                || lng > 137.8347
                || lat < 0.8293
                || lat > 55.8271;
    }

    private static double transformLat(
            double x,
            double y
    ) {
        double result =
                -100.0
                        + 2.0 * x
                        + 3.0 * y
                        + 0.2 * y * y
                        + 0.1 * x * y
                        + 0.2 * Math.sqrt(Math.abs(x));

        result += (
                20.0 * Math.sin(6.0 * x * Math.PI)
                        + 20.0 * Math.sin(2.0 * x * Math.PI)
        ) * 2.0 / 3.0;

        result += (
                20.0 * Math.sin(y * Math.PI)
                        + 40.0 * Math.sin(y / 3.0 * Math.PI)
        ) * 2.0 / 3.0;

        result += (
                160.0 * Math.sin(y / 12.0 * Math.PI)
                        + 320.0 * Math.sin(y * Math.PI / 30.0)
        ) * 2.0 / 3.0;

        return result;
    }

    private static double transformLng(
            double x,
            double y
    ) {
        double result =
                300.0
                        + x
                        + 2.0 * y
                        + 0.1 * x * x
                        + 0.1 * x * y
                        + 0.1 * Math.sqrt(Math.abs(x));

        result += (
                20.0 * Math.sin(6.0 * x * Math.PI)
                        + 20.0 * Math.sin(2.0 * x * Math.PI)
        ) * 2.0 / 3.0;

        result += (
                20.0 * Math.sin(x * Math.PI)
                        + 40.0 * Math.sin(x / 3.0 * Math.PI)
        ) * 2.0 / 3.0;

        result += (
                150.0 * Math.sin(x / 12.0 * Math.PI)
                        + 300.0 * Math.sin(x / 30.0 * Math.PI)
        ) * 2.0 / 3.0;

        return result;
    }

    public static GeoPoint wgs84ToGcj02(
            double lat,
            double lng
    ) {
        if (outOfChina(lat, lng)) {
            return new GeoPoint(lat, lng);
        }

        double deltaLat =
                transformLat(lng - 105.0, lat - 35.0);

        double deltaLng =
                transformLng(lng - 105.0, lat - 35.0);

        double radLat =
                lat / 180.0 * Math.PI;

        double magic = Math.sin(radLat);

        magic = 1.0
                - WGS_EE * magic * magic;

        double sqrtMagic = Math.sqrt(magic);

        deltaLat = deltaLat * 180.0
                / (
                WGS_A * (1.0 - WGS_EE)
                        / (magic * sqrtMagic)
                        * Math.PI
        );

        deltaLng = deltaLng * 180.0
                / (
                WGS_A / sqrtMagic
                        * Math.cos(radLat)
                        * Math.PI
        );

        return new GeoPoint(
                lat + deltaLat,
                lng + deltaLng
        );
    }

    public static GeoPoint gcj02ToBd09(
            double gcjLat,
            double gcjLng
    ) {
        double z = Math.sqrt(
                gcjLng * gcjLng
                        + gcjLat * gcjLat
        ) + 0.00002 * Math.sin(gcjLat * X_PI);

        double theta =
                Math.atan2(gcjLat, gcjLng)
                        + 0.000003
                        * Math.cos(gcjLng * X_PI);

        return new GeoPoint(
                z * Math.sin(theta) + 0.006,
                z * Math.cos(theta) + 0.0065
        );
    }

    public static GeoPoint wgs84ToBd09(
            double lat,
            double lng
    ) {
        GeoPoint gcj = wgs84ToGcj02(lat, lng);

        return gcj02ToBd09(
                gcj.lat(),
                gcj.lng()
        );
    }
}
