package dev.onyxcat.track;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹相关数据结构。
 *
 * 所有字段使用 public，便于 Jackson 直接序列化和反序列化。
 */
public final class TrackModels {

    private TrackModels() {
    }

    public static class GenPoint {

        public long id;
        public long flag;

        public double lat;
        public double lng;

        public double gLat;
        public double gLng;

        public double speed;
        public double avgSpeed;

        public double radius;
        public double accuracy;

        @JsonProperty("type")
        public long ptype;

        public long locType;
        public boolean hasAltitude;

        public long totalTime;
        public double totalDis;
        public double validDis;
        public long validTime;

        public long steps;
        public double stepDistance;

        public String gainTime;
        public long gainTimeMs;

        public long queueNum;
        public String coorType;

        public double bdA;
        public double bdD;
        public double bdS;
        public long bdG;

        public long count;
        public double dtr;
        public long state;

        public String locationId;

        public GenPoint() {
        }
    }

    public static class TenWindow {

        public long time;
        public double value;

        public TenWindow() {
        }

        public TenWindow(long time, double value) {
            this.time = time;
            this.value = value;
        }
    }

    public static class Segment {

        public long totalTime;
        public long distance;
        public long startTime;
        public long endTime;

        public double avgSpeed;
        public long avgStep;
        public long state;

        public Segment() {
        }
    }

    public static class Track {

        public long totalTime;
        public double totalDistance;
        public double validDistance;
        public long validTime;

        public long startTime;
        public double startLatitude;
        public double startLongitude;

        public long totalSteps;

        public List<GenPoint> locations = new ArrayList<GenPoint>();
        public List<TenWindow> speedPerTenSec = new ArrayList<TenWindow>();
        public List<TenWindow> stepsPerTenSec = new ArrayList<TenWindow>();
        public List<Segment> segments = new ArrayList<Segment>();

        public Track() {
        }

        /**
         * 校验顶层起点和 locations 首点是否一致。
         */
        @JsonIgnore
        public void validateConsistency() {
            if (totalTime <= 0) {
                throw new IllegalStateException("轨迹时长必须为正数");
            }

            if (!Double.isFinite(totalDistance) || totalDistance <= 0.0) {
                throw new IllegalStateException("轨迹距离必须为正数");
            }

            if (locations == null || locations.isEmpty()) {
                throw new IllegalStateException("轨迹不能为空");
            }

            for (int i = 0; i < locations.size(); i++) {
                GenPoint point = locations.get(i);

                if (point == null) {
                    throw new IllegalStateException(
                            "轨迹中存在空点，索引：" + i
                    );
                }

                validateCoordinate(point.gLat, point.gLng, i);
            }

            GenPoint first = locations.get(0);

            if (Math.abs(startLatitude - first.gLat) > 0.0000001
                    || Math.abs(startLongitude - first.gLng) > 0.0000001) {
                throw new IllegalStateException(
                        "轨迹首点与顶层起点坐标不一致"
                );
            }
        }

        private static void validateCoordinate(
                double lat,
                double lng,
                int index
        ) {
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
                throw new IllegalStateException(
                        "轨迹坐标不是有限数，索引：" + index
                );
            }

            if (lat < -90.0 || lat > 90.0) {
                throw new IllegalStateException(
                        "纬度超出范围，索引：" + index + "，纬度：" + lat
                );
            }

            if (lng < -180.0 || lng > 180.0) {
                throw new IllegalStateException(
                        "经度超出范围，索引：" + index + "，经度：" + lng
                );
            }
        }
    }
}
