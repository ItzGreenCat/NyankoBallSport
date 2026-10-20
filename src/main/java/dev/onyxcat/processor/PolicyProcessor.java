package dev.onyxcat.processor;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.onyxcat.Main;
import dev.onyxcat.network.AdditionalHeader;
import dev.onyxcat.network.EnumRequestMethod;
import dev.onyxcat.network.NetworkHandler;
import dev.onyxcat.session.Session;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Map;

public class PolicyProcessor {
    public static final String policyAPI  = "/api/v70103/runModePolicy";

    public static final String[] availableMustPoints = new String[]{"pointList",
            "runPointList",
            "mustPointList",
            "passPointList",
            "mustPoints",
            "passPoints",
            "runPoints",
            "points",
            "nodeList",
            "checkPointList"};
    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static PolicyArgument getPolicy() throws Exception {
        String unid = Session.unid;
        String body = MAPPER.writeValueAsString(Map.of("runMode",1,
                "ruleUpdateTime",0,
                "geoFenceUpdateTime",0,
                "selectUnid",unid,
                "operateType",0));
        JsonNode result = Main.networkHandler.request(EnumRequestMethod.POST,policyAPI,body, AdditionalHeader.EMPTY_HEADER);
        if(result.get("error").intValue() != 10000){
           JOptionPane.showMessageDialog(null,"无法获取跑步策略");
           throw new RuntimeException();
        }
        long timestamp = result.findPath("data").get("timestamp").longValue();
        System.out.println(result);
        System.out.println("TimeStamp:" + timestamp);
        long policy = result.findPath("data").get("policy").longValue();
        System.out.println(policy);
        JsonNode runRuleMode = result.findPath("data").findPath("runRuleModel");
        String startTime = runRuleMode.get("validTime").get(0).get("start").textValue();
        String endTime = runRuleMode.get("validTime").get(0).get("end").textValue();
        System.out.println(result.toPrettyString());
        return new PolicyArgument(timestamp, policy, runRuleMode.get("minDistance").longValue(), new Map.Entry<String, String>() {
            @Override
            public String getKey() {
                return startTime;
            }

            @Override
            public String getValue() {
                return endTime;
            }

            @Override
            public String setValue(String value) {
                throw new IllegalCallerException();
            }
        });
    }
}
