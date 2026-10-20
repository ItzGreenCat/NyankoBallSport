package dev.onyxcat.track;

import dev.onyxcat.track.TrackGeometry.GeoPoint;
import dev.onyxcat.track.TrackGeometry.PlanePoint;
import dev.onyxcat.track.TrackGeometry.PointRing;
import dev.onyxcat.track.TrackGeometry.SeededRandom;
import dev.onyxcat.track.TrackGeometry.WeightedValue;
import dev.onyxcat.track.TrackModels.GenPoint;
import dev.onyxcat.track.TrackModels.Segment;
import dev.onyxcat.track.TrackModels.TenWindow;
import dev.onyxcat.track.TrackModels.Track;

import java.util.ArrayList;
import java.util.List;

import static dev.onyxcat.track.TrackGeometry.MET_PER_DEG_LAT;
import static dev.onyxcat.track.TrackGeometry.MET_PER_DEG_LNG;
import static dev.onyxcat.track.TrackGeometry.clamp;
import static dev.onyxcat.track.TrackGeometry.formatGainTime;
import static dev.onyxcat.track.TrackGeometry.makePointRing;
import static dev.onyxcat.track.TrackGeometry.ringPointAt;
import static dev.onyxcat.track.TrackGeometry.round;
import static dev.onyxcat.track.TrackGeometry.toBd;

/**
 * 基于打卡点闭合曲线生成轨迹。
 *
 * 注意：这是曲线拟合，不是基于真实道路网络的导航路由。
 */
public final class TrackGenerator {

    public static final double SPEED_FLOOR = 1.90;
    public static final double SPEED_CEIL = 6.30;

    private TrackGenerator() {
    }

    private static final class Kind {

        private final long type;
        private final long locType;

        private Kind(long type, long locType) {
            this.type = type;
            this.locType = locType;
        }

        public long type() {
            return type;
        }

        public long locType() {
            return locType;
        }
    }

    private static final class Dip {

        private final double center;
        private final double halfWidth;
        private final double depth;

        private Dip(
                double center,
                double halfWidth,
                double depth
        ) {
            this.center = center;
            this.halfWidth = halfWidth;
            this.depth = depth;
        }

        public double center() {
            return center;
        }

        public double halfWidth() {
            return halfWidth;
        }

        public double depth() {
            return depth;
        }
    }

    private static void fitSpeeds(
            double[] speeds,
            double[] deltaTimes,
            double targetDistance
    ) {
        for (int iteration = 0; iteration < 24; iteration++) {
            double currentDistance = 0.0;

            for (int i = 0; i < speeds.length; i++) {
                currentDistance +=
                        speeds[i] * deltaTimes[i];
            }

            if (Math.abs(currentDistance - targetDistance) <= 1.0) {
                break;
            }

            if (currentDistance <= 0.0) {
                break;
            }

            double ratio =
                    targetDistance / currentDistance;

            for (int i = 0; i < speeds.length; i++) {
                speeds[i] = clamp(
                        speeds[i] * ratio,
                        SPEED_FLOOR,
                        SPEED_CEIL
                );
            }
        }

        for (int i = 0; i < speeds.length; i++) {
            speeds[i] = clamp(
                    speeds[i],
                    SPEED_FLOOR,
                    SPEED_CEIL
            );
        }
    }

    public static Track build(
            double distance,
            long duration,
            long seed,
            long startMs,
            List<GeoPoint> bdPoints
    ) {
        validateArguments(
                distance,
                duration,
                bdPoints
        );

        SeededRandom rng = new SeededRandom(seed);
        double durationSeconds = duration;

        PointRing ring = makePointRing(bdPoints);
        GeoPoint center = ring.center();

        double direction =
                rng.choice(1.0, -1.0);

        double ringLength = ring.length();

        if (ringLength <= 0.0) {
            throw new IllegalArgumentException(
                    "打卡点不能全部重合"
            );
        }

        double ringPosition =
                rng.uniform(0.0, ringLength);

        double velocityPhase =
                rng.uniform(0.0, Math.PI * 2.0);

        double stridePhase =
                rng.uniform(0.0, Math.PI * 2.0);

        List<Double> times =
                createTimes(durationSeconds, rng);

        int pointCount = times.size();

        double[] deltaTimes =
                createDeltaTimes(times, durationSeconds);

        List<Dip> dips =
                createDips(durationSeconds, rng);

        double[] speeds = createSpeeds(
                times,
                deltaTimes,
                distance,
                durationSeconds,
                velocityPhase,
                dips,
                rng
        );

        double[] segmentDistances =
                new double[pointCount];

        for (int i = 0; i < pointCount; i++) {
            segmentDistances[i] =
                    speeds[i] * deltaTimes[i];
        }

        List<Kind> kinds =
                createKinds(pointCount, rng);

        double[] displacement =
                createDisplacements(
                        kinds,
                        segmentDistances,
                        distance
                );

        List<GenPoint> locations =
                new ArrayList<GenPoint>(pointCount);

        List<TenWindow> tenSecondSpeed =
                new ArrayList<TenWindow>();

        List<TenWindow> tenSecondSteps =
                new ArrayList<TenWindow>();

        double accumulatedTime = 0.0;
        double accumulatedDistance = 0.0;
        double accumulatedSteps = 0.0;

        double jitterX = 0.0;
        double jitterY = 0.0;

        double altitude =
                82.0 + rng.uniform(-1.0, 1.0);

        long estimatedCount = Math.max(
                1L,
                (long) (durationSeconds / 5.0)
        );

        double altitudeSigma =
                rng.uniform(3.8, 6.2)
                        / (0.40 * estimatedCount);

        double windowTime = 0.0;
        double windowDistance = 0.0;
        double windowSteps = 0.0;

        for (int i = 0; i < pointCount; i++) {
            double deltaTime = deltaTimes[i];

            Kind kind = kinds.get(i);

            long type = kind.type();
            long locType = kind.locType();

            accumulatedTime += deltaTime;

            double stepDistance = 0.0;

            PlanePoint routePoint;
            double outputX;
            double outputY;
            double radius;
            long state;

            if (type != -1L) {
                stepDistance = displacement[i];

                ringPosition +=
                        direction * stepDistance;

                routePoint = ringPointAt(
                        ring,
                        ringPosition
                );

                jitterX =
                        0.72 * jitterX
                                + rng.gauss(0.0, 0.75);

                jitterY =
                        0.72 * jitterY
                                + rng.gauss(0.0, 0.75);

                outputX = routePoint.x() + jitterX;
                outputY = routePoint.y() + jitterY;

                radius = round(
                        type == 3L
                                ? rng.uniform(1.4, 5.1)
                                : rng.uniform(1.4, 2.4),
                        2
                );

                if (type == 0L) {
                    state = rng.weighted(stateWeightsForZero());
                } else {
                    state = rng.weighted(stateWeightsForOther());
                }
            } else {
                stepDistance =
                        createDriftDistance(locType, rng);

                routePoint = ringPointAt(
                        ring,
                        ringPosition
                );

                if (stepDistance > 0.0) {
                    double angle =
                            rng.uniform(0.0, Math.PI * 2.0);

                    outputX =
                            routePoint.x()
                                    + stepDistance
                                    * Math.sin(angle);

                    outputY =
                            routePoint.y()
                                    + stepDistance
                                    * Math.cos(angle);
                } else if (!locations.isEmpty()) {
                    GenPoint last =
                            locations.get(
                                    locations.size() - 1
                            );

                    outputY =
                            (last.gLat - center.lat())
                                    * MET_PER_DEG_LAT;

                    outputX =
                            (last.gLng - center.lng())
                                    * MET_PER_DEG_LNG;
                } else {
                    outputX = routePoint.x() + jitterX;
                    outputY = routePoint.y() + jitterY;
                }

                radius =
                        createDriftRadius(locType, rng);

                state = rng.weighted(abnormalStateWeights());
            }

            if (type != -1L) {
                accumulatedDistance += stepDistance;
            }

            GeoPoint coordinate = toBd(
                    outputX,
                    outputY,
                    center
            );

            altitude +=
                    0.04 * (82.0 - altitude)
                            + rng.gauss(
                            0.0,
                            altitudeSigma
                    );

            double averageVelocity =
                    distance / durationSeconds;

            double strideTarget =
                    0.62
                            + 0.17 * averageVelocity
                            + 0.03 * Math.sin(
                            Math.PI * 2.0
                                    * accumulatedTime / 200.0
                                    + stridePhase
                    )
                            + rng.gauss(0.0, 0.008);

            strideTarget = Math.max(
                    0.1,
                    strideTarget
            );

            double cadenceVelocity =
                    averageVelocity * (
                            1.0
                                    + 0.03 * Math.sin(
                                    Math.PI * 2.0
                                            * accumulatedTime / 70.0
                                            + velocityPhase
                            )
                    );

            double cadence = clamp(
                    cadenceVelocity
                            / strideTarget
                            * 60.0,
                    100.0,
                    200.0
            );

            accumulatedSteps +=
                    cadence / 60.0 * deltaTime;

            PlanePoint next = ringPointAt(
                    ring,
                    ringPosition + direction * 2.0
            );

            double bearing = Math.toDegrees(
                    Math.atan2(
                            next.x() - routePoint.x(),
                            next.y() - routePoint.y()
                    )
            );

            bearing += rng.gauss(0.0, 35.0);
            bearing = normalizeDegrees(bearing);

            double averageSpeed;
            double gpsSpeed;

            if (type == -1L) {
                averageSpeed = round(
                        accumulatedDistance
                                / Math.max(
                                1.0,
                                accumulatedTime
                        ),
                        4
                );

                if (rng.random() < 0.12) {
                    gpsSpeed =
                            rng.uniform(15.0, 46.0);
                } else {
                    gpsSpeed =
                            rng.uniform(0.5, 6.0);
                }

                gpsSpeed = round(gpsSpeed, 4);
            } else {
                averageSpeed = round(
                        stepDistance / deltaTime,
                        4
                );

                double kmh =
                        averageSpeed * 3.6;

                double sigma =
                        Math.max(kmh * 0.08, 0.05);

                if (rng.random() < 0.20) {
                    gpsSpeed = 0.0;
                } else {
                    gpsSpeed = round(
                            Math.max(
                                    0.0,
                                    kmh + rng.gauss(
                                            0.0,
                                            sigma
                                    )
                            ),
                            4
                    );
                }
            }

            windowTime += deltaTime;

            windowDistance +=
                    speeds[i] * deltaTime;

            windowSteps +=
                    cadence / 60.0 * deltaTime;

            while (windowTime >= 10.0) {
                double ratio =
                        10.0 / windowTime;

                double outputDistance =
                        windowDistance * ratio;

                double outputSteps =
                        windowSteps * ratio;

                tenSecondSpeed.add(
                        new TenWindow(
                                10L,
                                round(outputDistance, 2)
                        )
                );

                tenSecondSteps.add(
                        new TenWindow(
                                10L,
                                round(outputSteps, 0)
                        )
                );

                windowTime -= 10.0;
                windowDistance -= outputDistance;
                windowSteps -= outputSteps;
            }

            GenPoint point = new GenPoint();

            point.id = i + 1L;
            point.flag = startMs;

            point.lat = -1.0;
            point.lng = -1.0;

            point.gLat =
                    round(coordinate.lat(), 7);

            point.gLng =
                    round(coordinate.lng(), 7);

            point.speed = round(gpsSpeed, 4);
            point.avgSpeed = averageSpeed;

            point.radius = radius;
            point.accuracy = radius;

            point.ptype = type;
            point.locType = locType;

            point.hasAltitude = true;

            point.totalTime =
                    Math.round(accumulatedTime);

            point.totalDis =
                    round(accumulatedDistance, 4);

            point.validDis =
                    round(accumulatedDistance, 4);

            point.validTime =
                    Math.round(accumulatedTime);

            point.steps =
                    (long) accumulatedSteps;

            point.stepDistance = 0.0;

            point.gainTimeMs =
                    startMs
                            + (long) (
                            accumulatedTime * 1000.0
                    );

            point.gainTime =
                    formatGainTime(point.gainTimeMs);

            point.queueNum = 0L;
            point.coorType = "gcj02";

            point.bdA = round(altitude, 2);
            point.bdD = round(bearing, 2);

            point.bdS = round(
                    Math.max(
                            0.0,
                            averageSpeed
                                    * rng.uniform(0.6, 0.95)
                    ),
                    3
            );

            point.bdG =
                    rng.choice(1L, 1L, 1L, -1L);

            point.count =
                    rng.randint(20L, 88L);

            point.dtr = 0.0;
            point.state = state;
            point.locationId = "";

            locations.add(point);
        }

        if (windowTime > 1.0) {
            double ratio = Math.min(
                    10.0 / windowTime,
                    1.4
            );

            tenSecondSpeed.add(
                    new TenWindow(
                            10L,
                            round(
                                    windowDistance * ratio,
                                    2
                            )
                    )
            );

            tenSecondSteps.add(
                    new TenWindow(
                            10L,
                            round(
                                    windowSteps * ratio,
                                    0
                            )
                    )
            );
        }

        List<Segment> segments =
                createSegments(
                        times,
                        deltaTimes,
                        segmentDistances,
                        speeds,
                        accumulatedSteps,
                        accumulatedTime
                );

        applyPostFixes(
                locations,
                rng,
                startMs
        );

        snapToCheckPoints(
                locations,
                bdPoints
        );

        Track track = new Track();

        track.totalTime =
                Math.round(accumulatedTime);

        track.totalDistance =
                round(distance, 3);

        track.validDistance =
                track.totalDistance;

        track.validTime =
                Math.round(accumulatedTime);

        track.startTime = startMs;

        track.startLatitude =
                locations.get(0).gLat;

        track.startLongitude =
                locations.get(0).gLng;

        track.totalSteps =
                (long) accumulatedSteps;

        track.locations = locations;
        track.speedPerTenSec = tenSecondSpeed;
        track.stepsPerTenSec = tenSecondSteps;
        track.segments = segments;

        track.validateConsistency();

        return track;
    }

    /**
     * 保留和 Rust 参数形式接近的重载。
     *
     * center 参数不参与计算，实际中心由打卡点推导。
     */
    public static Track build(
            double distance,
            long duration,
            long seed,
            GeoPoint center,
            long startMs,
            List<GeoPoint> bdPoints
    ) {
        return build(
                distance,
                duration,
                seed,
                startMs,
                bdPoints
        );
    }

    private static void validateArguments(
            double distance,
            long duration,
            List<GeoPoint> points
    ) {
        if (!Double.isFinite(distance)
                || distance <= 0.0) {
            throw new IllegalArgumentException(
                    "distance必须是正数"
            );
        }

        if (duration <= 0L) {
            throw new IllegalArgumentException(
                    "duration必须是正数"
            );
        }

        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException(
                    "至少需要两个打卡点"
            );
        }

        double averageSpeed =
                distance / duration;

        if (averageSpeed < SPEED_FLOOR
                || averageSpeed > SPEED_CEIL) {
            throw new IllegalArgumentException(
                    "目标平均速度为 "
                            + round(averageSpeed, 3)
                            + "m/s，不在有效范围 "
                            + SPEED_FLOOR
                            + "～"
                            + SPEED_CEIL
                            + "m/s 内"
            );
        }
    }

    private static List<Double> createTimes(
            double duration,
            SeededRandom rng
    ) {
        List<Double> times =
                new ArrayList<Double>();

        double time = 0.0;

        while (time < duration) {
            times.add(time);

            if (rng.random() < 0.80) {
                time += 5.0;
            } else {
                time += rng.choice(
                        1.0,
                        2.0,
                        3.0,
                        4.0,
                        6.0,
                        7.0,
                        8.0
                );
            }
        }

        if (times.isEmpty()) {
            times.add(0.0);
        }

        return times;
    }

    private static double[] createDeltaTimes(
            List<Double> times,
            double duration
    ) {
        double[] result =
                new double[times.size()];

        for (int i = 0; i < times.size() - 1; i++) {
            result[i] =
                    times.get(i + 1) - times.get(i);
        }

        int last = times.size() - 1;

        result[last] = Math.max(
                1.0,
                duration - times.get(last)
        );

        return result;
    }

    private static List<Dip> createDips(
            double duration,
            SeededRandom rng
    ) {
        int count = rng.choice(1, 1, 2);

        List<Dip> result =
                new ArrayList<Dip>(count);

        for (int i = 0; i < count; i++) {
            result.add(new Dip(
                    rng.uniform(0.15, 0.75)
                            * duration,
                    rng.uniform(25.0, 55.0),
                    rng.uniform(0.08, 0.16)
            ));
        }

        return result;
    }

    private static double dipFactor(
            double time,
            List<Dip> dips
    ) {
        double factor = 1.0;

        for (Dip dip : dips) {
            if (Math.abs(time - dip.center())
                    < dip.halfWidth()) {
                factor *= 1.0
                        - dip.depth()
                        * 0.5
                        * (
                        1.0
                                + Math.cos(
                                Math.PI
                                        * (
                                        time
                                                - dip.center()
                                )
                                        / dip.halfWidth()
                        )
                );
            }
        }

        return factor;
    }

    private static double[] createSpeeds(
            List<Double> times,
            double[] deltaTimes,
            double distance,
            double duration,
            double phase,
            List<Dip> dips,
            SeededRandom rng
    ) {
        double baseSpeed =
                distance / duration;

        double[] result =
                new double[times.size()];

        for (int i = 0; i < times.size(); i++) {
            double time = times.get(i);

            double ramp;

            if (time < 8.0) {
                double denominator = Math.max(
                        1.0,
                        Math.min(8.0, duration / 20.0)
                );

                ramp = Math.min(
                        1.0,
                        0.85
                                + 0.15
                                * time / denominator
                );
            } else {
                ramp = 1.0;
            }

            if (time > duration - 8.0) {
                ramp *= 1.0
                        + 0.03
                        * (
                        time - (duration - 8.0)
                ) / 8.0;
            }

            double kilometers =
                    time / duration
                            * distance / 1000.0;

            double fatigue;

            if (kilometers <= 0.5) {
                fatigue = 1.03;
            } else {
                fatigue = Math.max(
                        0.80,
                        1.03
                                - 0.06
                                * (kilometers - 0.5)
                );
            }

            double wave =
                    (
                            1.0
                                    + 0.010 * Math.sin(
                                    Math.PI * 2.0
                                            * time / 115.0
                                            + phase
                            )
                                    + 0.035 * Math.sin(
                                    Math.PI * 2.0
                                            * time / 47.0
                                            + phase * 2.3
                            )
                                    + 0.015 * Math.sin(
                                    Math.PI * 2.0
                                            * time / 19.0
                                            + phase * 3.7
                            )
                    ) * dipFactor(time, dips);

            double noise =
                    1.0 + rng.gauss(0.0, 0.008);

            result[i] =
                    baseSpeed
                            * ramp
                            * fatigue
                            * wave
                            * noise;
        }

        fitSpeeds(
                result,
                deltaTimes,
                distance
        );

        return result;
    }

    private static List<Kind> createKinds(
            int count,
            SeededRandom rng
    ) {
        List<WeightedValue<Kind>> abnormal =
                new ArrayList<WeightedValue<Kind>>();

        abnormal.add(
                new WeightedValue<Kind>(
                        new Kind(-1L, 4L),
                        54
                )
        );

        abnormal.add(
                new WeightedValue<Kind>(
                        new Kind(-1L, 1L),
                        27
                )
        );

        abnormal.add(
                new WeightedValue<Kind>(
                        new Kind(-1L, 12L),
                        13
                )
        );

        abnormal.add(
                new WeightedValue<Kind>(
                        new Kind(-1L, 5L),
                        3
                )
        );

        abnormal.add(
                new WeightedValue<Kind>(
                        new Kind(-1L, 6L),
                        2
                )
        );

        List<Kind> result =
                new ArrayList<Kind>(count);

        for (int i = 0; i < count; i++) {
            double value = rng.random();

            if (value < 0.39) {
                result.add(new Kind(3L, 1L));
            } else if (value < 0.93) {
                result.add(new Kind(0L, 1L));
            } else if (value < 0.96) {
                result.add(rng.weighted(abnormal));
            } else {
                result.add(new Kind(
                        rng.choice(
                                1L,
                                1L,
                                1L,
                                1L,
                                2L,
                                2L
                        ),
                        1L
                ));
            }
        }

        for (int i = 1; i < result.size(); i++) {
            Kind previous = result.get(i - 1);
            Kind current = result.get(i);

            boolean previousDrift =
                    previous.type() == -1L
                            || previous.type() == 5L
                            || previous.type() == 6L;

            if (current.type() != -1L
                    && previousDrift
                    && rng.random() < 0.08) {
                result.set(
                        i,
                        new Kind(
                                rng.random() < 0.75
                                        ? 7L
                                        : 8L,
                                1L
                        )
                );
            }
        }

        for (int i = 1; i < result.size(); i++) {
            if (result.get(i).type() == -1L
                    && result.get(i - 1).type() == -1L) {
                result.set(
                        i,
                        new Kind(
                                rng.choice(3L, 0L),
                                1L
                        )
                );
            }
        }

        return result;
    }

    private static double[] createDisplacements(
            List<Kind> kinds,
            double[] segmentDistances,
            double targetDistance
    ) {
        double share = 0.0;

        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.get(i).type() != -1L
                    && i > 1) {
                share += segmentDistances[i];
            }
        }

        if (share == 0.0) {
            share = 1.0;
        }

        double[] result =
                new double[kinds.size()];

        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.get(i).type() != -1L
                    && i > 1) {
                result[i] =
                        segmentDistances[i]
                                * targetDistance
                                / share;
            }
        }

        return result;
    }

    private static double createDriftDistance(
            long locType,
            SeededRandom rng
    ) {
        if (locType == 4L) {
            if (rng.random() < 0.68) {
                return 0.0;
            }

            if (rng.random() < 0.95) {
                return rng.uniform(2.0, 60.0);
            }

            return rng.uniform(60.0, 250.0);
        }

        if (locType == 1L) {
            if (rng.random() < 0.83) {
                return 0.0;
            }

            return rng.uniform(0.5, 36.0);
        }

        if (locType == 12L) {
            if (rng.random() < 0.90) {
                return rng.uniform(5.0, 80.0);
            }

            return rng.uniform(80.0, 220.0);
        }

        if (locType == 5L) {
            return rng.uniform(5.0, 60.0);
        }

        return rng.uniform(100.0, 300.0);
    }

    private static double createDriftRadius(
            long locType,
            SeededRandom rng
    ) {
        if (locType == 4L) {
            if (rng.random() < 0.75) {
                return round(
                        rng.uniform(30.0, 100.0),
                        2
                );
            }

            return round(
                    rng.uniform(100.0, 550.0),
                    2
            );
        }

        if (locType == 1L) {
            if (rng.random() < 0.75) {
                return round(
                        rng.uniform(1.6, 12.0),
                        2
                );
            }

            return round(
                    rng.uniform(12.0, 95.0),
                    2
            );
        }

        if (locType == 12L) {
            return round(
                    rng.uniform(30.0, 125.0),
                    2
            );
        }

        if (locType == 5L) {
            return round(
                    rng.uniform(25.0, 300.0),
                    2
            );
        }

        return 550.0;
    }

    private static List<Segment> createSegments(
            List<Double> times,
            double[] deltaTimes,
            double[] segmentDistances,
            double[] speeds,
            double totalSteps,
            double totalTime
    ) {
        List<Segment> result =
                new ArrayList<Segment>();

        double segmentTime = 0.0;
        double segmentDistance = 0.0;
        double segmentSpeedSum = 0.0;
        int segmentPointCount = 0;

        double segmentStartSeconds = 0.0;
        double elapsedSeconds = 0.0;

        for (int i = 0; i < times.size(); i++) {
            if (segmentPointCount == 0) {
                segmentStartSeconds = elapsedSeconds;
            }

            segmentTime += deltaTimes[i];
            segmentDistance += segmentDistances[i];
            segmentSpeedSum += speeds[i];
            segmentPointCount++;

            elapsedSeconds += deltaTimes[i];

            if (segmentTime >= 60.0
                    || i == times.size() - 1) {
                Segment segment = new Segment();

                segment.totalTime =
                        Math.round(segmentTime);

                segment.distance =
                        Math.round(segmentDistance);

                segment.startTime =
                        Math.round(
                                segmentStartSeconds * 1000.0
                        );

                segment.endTime =
                        Math.round(
                                elapsedSeconds * 1000.0
                        );

                segment.avgSpeed = round(
                        segmentSpeedSum
                                / Math.max(
                                1,
                                segmentPointCount
                        ),
                        3
                );

                segment.avgStep = Math.round(
                        totalSteps
                                / Math.max(1.0, totalTime)
                                * 60.0
                );

                segment.state = 0L;

                result.add(segment);

                segmentTime = 0.0;
                segmentDistance = 0.0;
                segmentSpeedSum = 0.0;
                segmentPointCount = 0;
            }
        }

        return result;
    }

    private static void applyPostFixes(
            List<GenPoint> locations,
            SeededRandom rng,
            long startMs
    ) {
        if (locations.size() >= 4) {
            int index = locations.size() - 1;

            while (index > 0
                    && locations.get(index).ptype == -1L) {
                index--;
            }

            if (index > 1) {
                GenPoint point = locations.get(index);

                long stopTime =
                        point.totalTime
                                + rng.randint(6L, 14L);

                point.totalTime = stopTime;
                point.validTime = stopTime;

                double stopSpeed =
                        round(
                                rng.uniform(2.0, 2.6),
                                4
                        );

                point.speed =
                        round(stopSpeed * 3.6, 4);

                point.avgSpeed = stopSpeed;

                point.bdS = round(
                        rng.uniform(0.05, 0.15),
                        3
                );

                point.gainTimeMs =
                        startMs + stopTime * 1000L;

                point.gainTime =
                        formatGainTime(point.gainTimeMs);
            }
        }

        if (locations.size() > 2) {
            GenPoint startSentinel =
                    locations.get(1);

            startSentinel.ptype = 5L;
            startSentinel.state = 1L;
            startSentinel.locType = 1L;

            startSentinel.radius = round(
                    rng.uniform(2.0, 3.5),
                    2
            );

            startSentinel.accuracy =
                    startSentinel.radius;

            startSentinel.speed = 0.0;
            startSentinel.avgSpeed = 0.0;
            startSentinel.bdS = 0.0;

            startSentinel.totalDis = 0.0;
            startSentinel.validDis = 0.0;

            startSentinel.totalTime = 0L;
            startSentinel.validTime = 0L;
            startSentinel.steps = 0L;

            GenPoint end =
                    locations.get(
                            locations.size() - 1
                    );

            end.ptype = 6L;
            end.locType = 1L;

            end.radius = round(
                    rng.uniform(1.5, 3.0),
                    2
            );

            end.accuracy = end.radius;

            int from =
                    (int) (locations.size() * 0.67);

            int endExclusive =
                    locations.size() - 2;

            for (int i = from;
                 i < endExclusive;
                 i++) {
                if (rng.random() < 0.06) {
                    GenPoint point = locations.get(i);

                    point.ptype = rng.choice(
                            1L,
                            1L,
                            2L,
                            8L
                    );

                    point.locType = 1L;
                }
            }
        }

        if (locations.size() > 3) {
            GenPoint point = locations.get(2);

            point.avgSpeed = clamp(
                    round(
                            point.totalDis
                                    / Math.max(
                                    1.0,
                                    point.totalTime
                            ),
                            4
                    ),
                    SPEED_FLOOR,
                    SPEED_CEIL
            );

            point.speed = round(
                    point.avgSpeed * 3.6,
                    4
            );
        }

        if (!locations.isEmpty()) {
            GenPoint first = locations.get(0);

            first.totalTime = 0L;
            first.validTime = 0L;
            first.state = 1L;

            first.ptype = rng.choice(
                    0L,
                    0L,
                    0L,
                    7L
            );

            first.locType = 1L;

            first.radius = round(
                    rng.uniform(1.5, 3.0),
                    2
            );

            first.accuracy = first.radius;

            for (int i = 1;
                 i < locations.size();
                 i++) {
                GenPoint point = locations.get(i);

                if (point.avgSpeed > 0.0) {
                    first.avgSpeed =
                            point.avgSpeed;

                    first.bdS = point.bdS;
                    break;
                }
            }

            if (first.speed == 0.0) {
                first.speed = round(
                        rng.uniform(7.0, 13.0),
                        4
                );
            }
        }
    }

    private static void snapToCheckPoints(
            List<GenPoint> locations,
            List<GeoPoint> checkPoints
    ) {
        for (GeoPoint checkPoint : checkPoints) {
            int bestIndex = -1;

            double bestDistanceSquared =
                    Double.MAX_VALUE;

            for (int i = 0;
                 i < locations.size();
                 i++) {
                GenPoint point = locations.get(i);

                double deltaLat =
                        (point.gLat - checkPoint.lat())
                                * MET_PER_DEG_LAT;

                double deltaLng =
                        (point.gLng - checkPoint.lng())
                                * MET_PER_DEG_LNG;

                double distanceSquared =
                        deltaLat * deltaLat
                                + deltaLng * deltaLng;

                if (distanceSquared
                        < bestDistanceSquared) {
                    bestDistanceSquared =
                            distanceSquared;

                    bestIndex = i;
                }
            }

            if (bestIndex >= 0
                    && bestDistanceSquared
                    < 40.0 * 40.0) {
                GenPoint point =
                        locations.get(bestIndex);

                point.gLat = round(
                        checkPoint.lat(),
                        7
                );

                point.gLng = round(
                        checkPoint.lng(),
                        7
                );
            }
        }
    }

    private static List<WeightedValue<Long>>
    stateWeightsForZero() {
        List<WeightedValue<Long>> result =
                new ArrayList<WeightedValue<Long>>();

        result.add(new WeightedValue<Long>(1L, 145));
        result.add(new WeightedValue<Long>(2L, 45));
        result.add(new WeightedValue<Long>(3L, 164));

        return result;
    }

    private static List<WeightedValue<Long>>
    stateWeightsForOther() {
        List<WeightedValue<Long>> result =
                new ArrayList<WeightedValue<Long>>();

        result.add(new WeightedValue<Long>(1L, 145));
        result.add(new WeightedValue<Long>(2L, 256));
        result.add(new WeightedValue<Long>(3L, 151));

        return result;
    }

    private static List<WeightedValue<Long>>
    abnormalStateWeights() {
        List<WeightedValue<Long>> result =
                new ArrayList<WeightedValue<Long>>();

        result.add(new WeightedValue<Long>(1L, 102));
        result.add(new WeightedValue<Long>(2L, 124));
        result.add(new WeightedValue<Long>(3L, 136));

        return result;
    }

    private static double normalizeDegrees(
            double degrees
    ) {
        double result = degrees % 360.0;

        if (result < 0.0) {
            result += 360.0;
        }

        return result;
    }
}
