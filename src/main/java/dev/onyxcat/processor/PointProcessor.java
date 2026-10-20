package dev.onyxcat.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.onyxcat.Main;
import dev.onyxcat.network.*;
import dev.onyxcat.session.Session;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PointProcessor {
    public static final String pointApi = "/api/v560/get/1/distance/1";
    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static List<Point> getPoints() throws IOException {
        int uid = Session.uid;
        if(uid == -1){
            JOptionPane.showMessageDialog(null,"未登录账号");
            throw new RuntimeException();
        }
        String unid = Session.unid;
        double lon = Session.headerInformation.getAnchorLon();
        double lat = Session.headerInformation.getAnchorLat();
        String url = NetworkHandler.HOST_URL + pointApi;
        String runec = uid + String.format("%.6f",lon) + String.format("%.6f",lat) + ((EnvelopeCrypto.nowMs() / 1000) * 1000);
        EnvelopeCrypto.BuiltEnvelope builtEnvelope = EnvelopeCrypto.buildEnvelope(Session.envelopeSession,runec, EnvelopeCrypto.OuterOrder.OBSERVED);
        String body = MAPPER.writeValueAsString(Map.of("sportType",4,
                        "longitude",lon,
                        "latitude",lat,
                        "sign", Sign.signUrlMD5(url),
                "uuid", UUID.randomUUID().toString(),
                "selectedUnid",unid,
                "runec",builtEnvelope.getJson()));
        RequestResult result = Main.networkHandler.encryptedPacket(EnumRequestMethod.POST,url,body,HeaderBuilder.iosUA,AdditionalHeader.EMPTY_HEADER);
        JsonNode jsonPoints = MAPPER.readTree(result.data.getBusiness().getBytes()).findPath("data").findPath("pointsResModels");
        List<Point> points;
        System.out.println(result.data.getBusiness());
        points = MAPPER.readValue(jsonPoints.toString().getBytes(), new TypeReference<ArrayList<Point>>(){});
        for(Point point : points){
            System.out.println(point);
        }
        return points;
    }
}
