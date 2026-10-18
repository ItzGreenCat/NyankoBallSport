package dev.onyxcat.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.onyxcat.session.Session;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class NetworkHandler {
    public static final String HOST_URL = "https://run.gxapp.iydsj.com";
    public static final String EXTRA_URL = "https://discovery.gxapp.iydsj.com";

    public JsonNode request(EnumRequestMethod method,String api,String request_body,AdditionalHeader additionalHeaders) throws Exception {
        String url = HOST_URL + api;
        RequestResult result = encryptedPacket(method,url,request_body,HeaderBuilder.iosUA,additionalHeaders);
        JsonNode business =  checkBusiness(result.data.getBusiness());
        return business;
    }
    public RequestResult encryptedPacket(EnumRequestMethod method,String urlStr,String request_body,String ua,AdditionalHeader... additionalHeader) throws IOException {
        int uid = Session.uid;
        String token = Session.token;
        HeaderBuilder.BuiltHeader header = HeaderBuilder.buildHeaderFor(Session.headerInformation,uid,token,null);
        EnvelopeCrypto.BuiltEnvelope headerEnvironment = EnvelopeCrypto.buildEnvelope(Session.envelopeSession,header.getJson(), EnvelopeCrypto.OuterOrder.OBSERVED);
        EnvelopeCrypto.BuiltEnvelope bodyEnvironment = EnvelopeCrypto.buildEnvelopeTs(Session.envelopeSession,request_body, EnvelopeCrypto.OuterOrder.INSERT,headerEnvironment.getTsMs() + 1);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(method.name().toUpperCase());
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", ua);
            conn.setRequestProperty("appVersion", "7.3.40");
            conn.setRequestProperty("headerSign", headerEnvironment.getJson());

            if (!additionalHeader[0].isEmpty()) {
                for (AdditionalHeader pair : additionalHeader) {
                    conn.setRequestProperty(pair.key, pair.value);
                }
            }

            byte[] bodyBytes = bodyEnvironment.getJson().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            String raw = readStreamToString(is);
            byte[] rawBytes = raw.getBytes(StandardCharsets.UTF_8);
            int rawLen = rawBytes.length;

            // 5. 派生 pAesKey 并解密响应
            String[] kd = bodyEnvironment.getKeyData();
            byte[] key = ResponseDecryptor.derivePaesKey(kd[0], kd[1], kd[2], kd[3]);

            ResponseDecryptor.Decrypted decrypted = null;
            try {
                decrypted = ResponseDecryptor.decryptResponse(rawBytes, key, EnvelopeCrypto.rsaPublicKey());
            } catch (Exception e) {
                e.printStackTrace();
            }

            return new RequestResult(status, decrypted, rawLen);
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            conn.disconnect();
        }
    }
    private static String readStreamToString(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
        }
        return sb.toString();
    }
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public JsonNode checkBusiness(String businessStr) throws Exception {
        // 0. 将传入的 JSON 字符串解析为 JsonNode
        JsonNode v;
        try {
            v = MAPPER.readTree(businessStr);
        } catch (Exception e) {
            throw new RuntimeException("业务 JSON 解析失败: " + businessStr, e);
        }

        // 1. 获取 error 字段，如果不存在或不是数字，默认返回 0
        long err = v.path("error").asLong(0L);

        // 2. 判断业务状态码是否为 10000
        if (err == 10000L) {
            return v; // 返回解析好的 JsonNode 供后续使用（如果你想原样返回 String，这里改写为 return businessStr; 即可）
        } else {
            // 3. 依次尝试获取 message 或 msg 字段
            JsonNode msgNode = v.get("message");
            if (msgNode == null || msgNode.isNull()) {
                msgNode = v.get("msg");
            }

            String msg = (msgNode != null && msgNode.isTextual())
                    ? msgNode.asText()
                    : "(无消息)";

            // 4. 抛出异常
            throw new RuntimeException(String.format("业务错误 %d: %s", err, msg));
        }
    }
}
