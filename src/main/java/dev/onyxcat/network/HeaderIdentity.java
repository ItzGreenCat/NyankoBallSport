package dev.onyxcat.network;

import java.io.Serializable;

public class HeaderIdentity implements Serializable, Cloneable {
    public static final String[] iosVersions = new String[]{"17.5.1", "18.0.1", "18.1", "18.2",};
    public static final String[] androidVersions = new String[]{"10","11","12", "13", "14","15","16"};
    public static final String[] androidDevices = new String[]{"HLS-AL00","TET-AN00","ALT-AL10","JAD-AL00","PGP110","PGZ110","PGKM10","PJD110","PMW110","PDSM00","V1821A","V1923A","V1829A","V2046A","V2170A","V2227A","V2405A","V2612A","M1803E1A","M2011K2C","25098PN5AC","NX712J","NX907J"};
    private static final long serialVersionUID = 1L;
    private static final long DAY_MS = 86_400_000L;
    //ios/android
    private String platform = "ios";
    //random uuid
    private String deviceId = "";
    //empty,ios random uuid
    private String idfa = "";
    //version
    private String osVersion = "17.4.1";
    //机型,ios固定iPhone
    private String deviceName = "iPhone";
    //empty
    private String manufacturer = "";
    //经度
    private double anchorLon = 0.0;
    //纬度
    private double anchorLat = 0.0;

    private long appInstallTime = 0L;

    private String macAddress = "";

    private String city = "哈尔滨";

    public HeaderIdentity() {
    }

    public HeaderIdentity(String platform, String deviceId, String idfa, String osVersion,
                          String deviceName, String manufacturer, double anchorLat, double anchorLon,
                          long appInstallTime, String macAddress, String city) {
        this.platform = platform;
        this.deviceId = deviceId;
        this.idfa = idfa;
        this.osVersion = osVersion;
        this.deviceName = deviceName;
        this.manufacturer = manufacturer;
        this.anchorLat = anchorLat;
        this.anchorLon = anchorLon;
        this.appInstallTime = appInstallTime;
        this.macAddress = macAddress;
        this.city = city;
    }

    public HeaderIdentity(HeaderIdentity other) {
        if (other != null) {
            this.platform = other.platform;
            this.deviceId = other.deviceId;
            this.idfa = other.idfa;
            this.osVersion = other.osVersion;
            this.deviceName = other.deviceName;
            this.manufacturer = other.manufacturer;
            this.anchorLat = other.anchorLat;
            this.anchorLon = other.anchorLon;
            this.appInstallTime = other.appInstallTime;
            this.macAddress = other.macAddress;
            this.city = other.city;
        }
    }

    @Override
    public HeaderIdentity clone() {
        return new HeaderIdentity(this);
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform != null ? platform : "ios";
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId != null ? deviceId : "";
    }

    public String getIdfa() {
        return idfa;
    }

    public void setIdfa(String idfa) {
        this.idfa = idfa != null ? idfa : "";
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion != null ? osVersion : "";
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName != null ? deviceName : "";
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer != null ? manufacturer : "";
    }

    public double getAnchorLat() {
        return anchorLat;
    }

    public void setAnchorLat(double anchorLat) {
        this.anchorLat = anchorLat;
    }

    public double getAnchorLon() {
        return anchorLon;
    }

    public void setAnchorLon(double anchorLon) {
        this.anchorLon = anchorLon;
    }

    public long getAppInstallTime() {
        return appInstallTime;
    }

    public void setAppInstallTime(long appInstallTime) {
        this.appInstallTime = appInstallTime;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress != null ? macAddress : "";
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city != null ? city : "";
    }

    @Override
    public String toString() {
        return "HeaderIdentity{" +
                "platform='" + platform + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", idfa='" + idfa + '\'' +
                ", osVersion='" + osVersion + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                ", anchorLat=" + anchorLat +
                ", anchorLon=" + anchorLon +
                ", appInstallTime=" + appInstallTime +
                ", macAddress='" + macAddress + '\'' +
                ", city='" + city + '\'' +
                '}';
    }
    public long installTime(long ts) {
        if (this.appInstallTime > 0) {
            return this.appInstallTime;
        } else if ("android".equals(this.platform)) {
            return ts - 90L * DAY_MS;
        } else {
            return ts - 3L * DAY_MS;
        }
    }

    public static long freshInstallTime(String platform) {
        long days = "android".equals(platform) ? 90L : 3L;
        return System.currentTimeMillis() - days * DAY_MS;
    }

    public boolean hasUnconfiguredDefaultLocation(String defaultCity) {
        if (this.city == null || this.city.trim().isEmpty()) {
            return true;
        }
        return this.city.trim().equals(defaultCity);
    }
}
