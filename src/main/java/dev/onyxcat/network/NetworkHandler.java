package dev.onyxcat.network;

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

    public void request(EnumRequestMethod method,String api,String request_body,AdditionalHeader additionalHeaders){
        String url = HOST_URL + api;
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

            if (additionalHeader != null) {
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
}
