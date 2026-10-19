package dev.onyxcat.processor;

public class Point {
    public double lon;
    public double lat;
    public int isFixed;
    public String pointName;
    public double glon;
    public double glat;

    @Override
    public String toString() {
        return String.format("lon[%f] lat[%f] isFixed[%d] pointName[%s] glon[%f] glat[%f]",lon,lat,isFixed,pointName,glon,glat);
    }
}
