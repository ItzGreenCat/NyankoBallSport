package dev.onyxcat.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.onyxcat.AccountArray;
import dev.onyxcat.AccountContainer;
import dev.onyxcat.AccountList;
import dev.onyxcat.Main;
import dev.onyxcat.network.AdditionalHeader;
import dev.onyxcat.network.EnumRequestMethod;
import dev.onyxcat.network.HeaderIdentity;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String geeValidate = "/api/v70270/security/geevalidate";
    public static final String checkGee = "/api/v65/security/checkGeeUse";
    public static final String loginApi = "/api/v70100/login";
    public static final String logoutApi = "/api/v6/user/logout";
    public static final String captcha_salt = "8c065103d81f5fd3efec8ad3e3a84c30";

    public boolean canSkip(String username) {
        try {
            String body = MAPPER.writeValueAsString(Map.of(
                    "username", username,
                    "uuid", UUID.randomUUID().toString(),
                    "unid", 0,
                    "type", 2
            ));
            JsonNode data = Main.networkHandler.request(EnumRequestMethod.POST, checkGee, body, AdditionalHeader.EMPTY_HEADER);
            boolean skip = data.path("data").asBoolean(false);
            System.out.printf("[gee] checkGeeUse data=%b（%s）%n", skip, skip ? "跳过 GT4" : "走 GT4 流程");
            return skip;
        } catch (Exception e) {
            System.out.printf("[gee] checkGeeUse 失败（%s），按需验证码处理%n", e.getMessage());
            return false;
        }
    }

    public void login(Map.Entry<String,String> entry){
        String uuidValue = UUID.randomUUID().toString();
        String username = entry.getKey();
        String password = entry.getValue();

        try {
            if (canSkip(username)) {
                System.out.println("[login] 免验证码路径：直接登录");
            } else {
                boolean validated = false;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    System.out.println("[login] 开始 GT4 滑块验证…");
                    JsonNode creds = solveGt4(captcha_salt);
                    System.out.printf("[login] GT4 通过 lot_number=%s%n", creds.path("lotNumber").asText(""));

                    String body = MAPPER.writeValueAsString(Map.of(
                            "lotNumber", creds.path("lotNumber"),
                            "captchaOutput", creds.path("captchaOutput"),
                            "passToken", creds.path("passToken"),
                            "genTime", creds.path("genTime"),
                            "isOffline", false,
                            "osType", 1,
                            "businessType", 0,
                            "uuid", uuidValue,
                            "username", username
                    ));

                    try {
                        JsonNode gvResp = Main.networkHandler.request(EnumRequestMethod.POST, geeValidate, body, AdditionalHeader.EMPTY_HEADER);
                        long gvErr = gvResp.path("error").asLong(0L);
                        if (gvErr == 10000L) {
                            System.out.println("[geevalidate] 验证通过");
                            validated = true;
                            break;
                        } else if (gvErr == 10003L) {
                            System.out.printf("[geevalidate] 验证失败（%d/3），重试%n", attempt);
                            Thread.sleep(2000);
                        } else {
                            System.out.printf("[geevalidate] err=%d（继续尝试登录）%n", gvErr);
                            validated = true;
                            break;
                        }
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("10003")) {
                            System.out.printf("[geevalidate] 验证失败（%d/3），重试%n", attempt);
                            Thread.sleep(2000);
                        } else {
                            System.out.printf("[geevalidate] 异常=%s（继续尝试登录）%n", e.getMessage());
                            validated = true;
                            break;
                        }
                    }
                }

                if (!validated) {
                    JOptionPane.showMessageDialog(null,"GeeValidate 连续验证失败（10003），请重试!","提示",JOptionPane.WARNING_MESSAGE);
                    throw new RuntimeException("geevalidate 连续验证失败（10003），请重试");
                }
                Thread.sleep(2000);
            }

            if (Session.headerInformation == null) {
                Session.headerInformation = new HeaderIdentity();
            }
            HeaderIdentity identity = Session.headerInformation.clone();
            String deviceId = identity.getDeviceId();
            if (deviceId == null || deviceId.isEmpty()) {
                deviceId = UUID.randomUUID().toString().toUpperCase();
                Session.headerInformation.setDeviceId(deviceId);
            }

            String loginBody = MAPPER.writeValueAsString(Map.of(
                    "device_model", identity.getDeviceName(),
                    "os_version", identity.getOsVersion(),
                    "mac_address", identity.getMacAddress(),
                    "imei", "",
                    "loginType", 0,
                    "username", username,
                    "password", password,
                    "uuid", uuidValue,
                    "osType", "0"
            ));

            String credential = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            AdditionalHeader authHeader = new AdditionalHeader("Authorization", "Basic " + credential);

            JsonNode biz = Main.networkHandler.request(EnumRequestMethod.POST, loginApi, loginBody, authHeader);

            int uid = getField(biz, "uid").asInt(0);
            String token = getField(biz, "token").asText("");
            if (uid < 1 || token.isEmpty()) {
                JOptionPane.showMessageDialog(null,"登录响应缺 uid/token: " + biz.toString(),"提示",JOptionPane.WARNING_MESSAGE);
                throw new RuntimeException("登录响应缺 uid/token: " + biz.toString());
            }

            JsonNode unidNode = getField(biz, "unid");
            String unid = (unidNode.isTextual() || unidNode.isNumber()) ? unidNode.asText() : "0";

            Session.uid = uid;
            Session.token = token;
            Session.unid = unid;
            Session.name = getField(biz, "name").asText("");
            Session.weight = getField(biz, "weight").asDouble(68.0);
            Session.username = username;
            Session.profile = parseDataField(biz);

            SessionCodec.saveSession("Session-" + username);
            AccountList.accounts.containers.add(new AccountContainer(Session.name,username));
            AccountList.saveAccounts();
            Main.mainMenuInstance.accountManager.refreshAccounts();
            System.out.printf("[session] uid=%d token=%s unid=%s name=%s weight=%.1f device=%s%n",
                    Session.uid, mask(Session.token), Session.unid, Session.name, Session.weight, deviceId);
            System.out.printf("[session] profile 字段数=%d%n",
                    Session.profile != null && Session.profile.isObject() ? Session.profile.size() : 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void logout() {
        if (Session.uid > 0 && !Session.token.isEmpty()) {
            int oldUid = Session.uid;
            try {
                Main.networkHandler.request(EnumRequestMethod.POST, logoutApi, "{}", AdditionalHeader.EMPTY_HEADER);
            } catch (Exception ignored) {
            }
            System.out.printf("[logout] 已请求登出 uid=%d%n", oldUid);
        }
        Session.resetSession();
        Main.mainMenuInstance.accountManager.refreshAccounts();
        System.out.println("[logout] 本地 Session 已重置");
    }

    private JsonNode getField(JsonNode biz, String key) {
        if (biz.hasNonNull(key)) {
            return biz.get(key);
        }
        return parseDataField(biz).path(key);
    }

    private JsonNode parseDataField(JsonNode biz) {
        JsonNode data = biz.path("data");
        if (data.isTextual()) {
            try {
                return MAPPER.readTree(data.asText());
            } catch (Exception ignored) {}
        }
        return data;
    }

    private String mask(String s) {
        if (s == null || s.length() <= 8) return "***";
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }

    private JsonNode solveGt4(String captchaId) throws Exception {
        java.util.concurrent.CompletableFuture<JsonNode> future = new java.util.concurrent.CompletableFuture<>();

        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();

        server.createContext("/", exchange -> {
            String html = String.format(
                    "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>GT4 滑块验证</title>\n" +
                            "<script src=\"https://static.geetest.com/v4/gt4.js\"></script></head>\n" +
                            "<body style=\"display:flex;justify-content:center;align-items:center;height:100vh;margin:0;font-family:sans-serif;\">\n" +
                            "<h3 id=\"tip\">正在加载极验 v4 滑块，请完成验证...</h3>\n" +
                            "<script>\n" +
                            "initGeetest4({ captchaId: '%s', product: 'bind' }, function (captcha) {\n" +
                            "    captcha.onReady(function() { captcha.showCaptcha(); });\n" +
                            "    captcha.onSuccess(function() {\n" +
                            "        var result = captcha.getValidate();\n" +
                            "        document.getElementById('tip').innerText = '验证成功！正在传回程序...';\n" +
                            "        var payload = JSON.stringify({\n" +
                            "            lotNumber: result.lot_number,\n" +
                            "            captchaOutput: result.captcha_output,\n" +
                            "            passToken: result.pass_token,\n" +
                            "            genTime: result.gen_time\n" +
                            "        });\n" +
                            "        fetch('/callback?data=' + encodeURIComponent(payload))\n" +
                            "            .then(function() {\n" +
                            "                document.getElementById('tip').innerText = '已传回程序，此页面可关闭！';\n" +
                            "            })\n" +
                            "            .catch(function(err) {\n" +
                            "                document.getElementById('tip').innerText = '传回失败: ' + err;\n" +
                            "            });\n" +
                            "    });\n" +
                            "});\n" +
                            "</script></body></html>",
                    captchaId
            );
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/callback", exchange -> {
            try {
                String query = exchange.getRequestURI().getRawQuery();
                String jsonStr = "";
                if (query != null && query.startsWith("data=")) {
                    jsonStr = java.net.URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8.name());
                }

                byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(200, ok.length);
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                    os.write(ok); // 必须把 ok 写出去，否则连接会异常挂起
                }

                System.out.println("[gt4] 已收到浏览器回传凭证！");
                future.complete(MAPPER.readTree(jsonStr));
            } catch (Exception e) {
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        server.start();
        String url = "http://127.0.0.1:" + port + "/";
        System.out.println("[gt4] 请在浏览器中完成滑块验证: " + url);

        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception ignored) {}

        try {
            return future.get(120, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            server.stop(0);
        }
    }
    public void loadLocalSession(String account){
        try {
            SessionCodec.loadSession("Session-" + account);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"无法加载此账户","提示",JOptionPane.WARNING_MESSAGE);
        }
    }
    public void removeLocalSession(String account){
        try {
            AccountContainer container = AccountList.accounts.containers.stream().filter(i -> i.accountName.equals(account)).findFirst().get();
            AccountList.accounts.containers.remove(container);
            AccountList.saveAccounts();
            SessionCodec.removeSession("Session-" + account);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"无法删除此账户","提示",JOptionPane.WARNING_MESSAGE);
        }
    }
}
