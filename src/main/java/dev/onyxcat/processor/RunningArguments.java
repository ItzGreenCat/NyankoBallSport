package dev.onyxcat.processor;

import java.math.BigDecimal;
import java.math.BigInteger;

public class RunningArguments {
    public final double distance;
    public final long duration;
    public final long startMileSeconds;
    public final long faceCheck;
    public final BigInteger seed;
    public RunningArguments(double distance,long duration,long startMileSeconds,long faceCheck,BigInteger seed){
        this.distance = distance;
        this.duration = duration;
        this.faceCheck = faceCheck;
        this.seed = seed;
        this.startMileSeconds = startMileSeconds;
    }
}
