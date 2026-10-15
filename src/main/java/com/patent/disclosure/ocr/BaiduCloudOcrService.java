package com.patent.disclosure.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 百度智能云 OCR 引擎（通用文字识别 · 含位置信息版 general）。
 *
 * 无需在本机安装 Tesseract，截图会上传到百度智能云识别。
 * 配置示例（application.yml）：
 * <pre>
 * automation:
 *   ocr:
 *     engine: baidu
 *     baidu:
 *       api-key: 你的APIKey
 *       secret-key: 你的SecretKey
 * </pre>
 * 免费额度：个人认证 1,000 次/月（控制台-免费资源领取页领取），详见百度智能云官网。
 */
@Service
@ConditionalOnProperty(name = "automation.ocr.engine", havingValue = "baidu")
public class BaiduCloudOcrService implements OcrService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final String apiKey;
    private final String secretKey;
    private final String tokenUrl;
    private final String endpoint;
    private final long timeoutMs;
    private final ObjectMapper objectMapper;

    /** 缓存的 access_token（有效期默认 30 天，提前 5 分钟刷新）。 */
    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    public BaiduCloudOcrService(
            @Value("${automation.ocr.baidu.api-key:}") String apiKey,
            @Value("${automation.ocr.baidu.secret-key:}") String secretKey,
            @Value("${automation.ocr.baidu.token-url:https://aip.baidubce.com/oauth/2.0/token}") String tokenUrl,
            @Value("${automation.ocr.baidu.endpoint:https://aip.baidubce.com/rest/2.0/ocr/v1/general}") String endpoint,
            @Value("${automation.ocr.timeout-ms:15000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.tokenUrl = tokenUrl;
        this.endpoint = endpoint;
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
    }

    @Override
    public String engineName() {
        return "baidu";
    }

    @Override
    public Map<String, Object> status() {
        return Map.of(
                "engine", "baidu",
                "endpoint", endpoint,
                "apiKeyConfigured", !apiKey.isEmpty() && !secretKey.isEmpty(),
                "hint", "在线 OCR：截图将上传至百度智能云。请在 application.yml 配置 automation.ocr.baidu.api-key / secret-key");
    }

    @Override
    public OcrResult recognize(BufferedImage image, Rectangle screenRegion) {
        long start = System.currentTimeMillis();
        try {
            if (apiKey.isEmpty() || secretKey.isEmpty()) {
                return failure(start, "未配置百度 OCR 的 api-key / secret-key（automation.ocr.baidu.*）");
            }
            String token = accessToken();
            String base64 = Base64.getEncoder().encodeToString(png(image));
            String body = "image=" + URLEncoder.encode(base64, StandardCharsets.UTF_8)
                    + "&language_type=CHN_ENG&detect_direction=false";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "?access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            int errorCode = root.path("error_code").asInt(0);
            if (errorCode != 0) {
                String msg = root.path("error_msg").asText("未知错误");
                if (errorCode == 17) {
                    msg += "（今日免费调用量已用完，请明日再试或购买次数包）";
                } else if (errorCode == 18) {
                    msg += "（QPS 超限，请降低流程步频）";
                } else if (errorCode == 110 || errorCode == 111) {
                    msg += "（access_token 失效，请检查 api-key / secret-key）";
                }
                return failure(start, "百度 OCR 调用失败(error=" + errorCode + "): " + msg);
            }

            List<OcrElement> elements = new ArrayList<>();
            StringBuilder allText = new StringBuilder();
            JsonNode words = root.path("words_result");
            for (JsonNode item : words) {
                String text = item.path("words").asText("");
                if (text.isBlank()) continue;
                JsonNode loc = item.path("location");
                int left = loc.path("left").asInt(0);
                int top = loc.path("top").asInt(0);
                int width = loc.path("width").asInt(0);
                int height = loc.path("height").asInt(0);
                if (allText.length() > 0) allText.append('\n');
                allText.append(text);
                elements.add(new OcrElement(text, left, top, width, height,
                        screenRegion.x + left, screenRegion.y + top, width, height, 0));
            }
            OcrResult result = new OcrResult(true, "baidu", "CHN_ENG", allText.toString(), elements,
                    System.currentTimeMillis() - start, "OCR 识别成功");
            log("[OCR] recognize OK " + elements.size() + " elements "
                    + (System.currentTimeMillis() - start) + "ms text=" + truncate(allText.toString(), 100));
            return result;
        } catch (Exception e) {
            return failure(start, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 获取 access_token（缓存 30 天，失效前自动刷新）。 */
    private String accessToken() throws Exception {
        String token = cachedToken;
        if (token != null && System.currentTimeMillis() < tokenExpireAt) {
            return token;
        }
        String url = tokenUrl + "?grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secretKey, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.has("access_token")) {
            throw new IllegalStateException("获取 access_token 失败: " + response.body());
        }
        long expiresIn = root.path("expires_in").asLong(2592000L);
        token = root.path("access_token").asText();
        // 提前 5 分钟过期，避免边界失效
        tokenExpireAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
        cachedToken = token;
        log("access_token 刷新成功，有效期 " + expiresIn + "s");
        return token;
    }

    private byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private OcrResult failure(long start, String message) {
        log("[OCR] recognize FAIL " + (System.currentTimeMillis() - start) + "ms " + message);
        return new OcrResult(false, "baidu", "CHN_ENG", "", List.of(),
                System.currentTimeMillis() - start, message);
    }

    private static final DateTimeFormatter OCR_LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private void log(String line) {
        System.out.println("[OCR] " + LocalTime.now().format(OCR_LOG_TIME) + " " + line);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        String oneLine = s.replace('\n', '|');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }
}
