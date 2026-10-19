package dev.onyxcat.processor;

import java.util.List;
import java.util.Map;

public class PolicyArgument {
    public final long timestamp;
    public final long policy;
    public final long minDistance;
    public final Map.Entry<String,String> validTime;
    public PolicyArgument(long timestamp, long policy, long minDistance, Map.Entry<String,String> validTime){
        this.timestamp = timestamp;
        this.minDistance = minDistance;
        this.policy = policy;
        this.validTime = validTime;
    }
}