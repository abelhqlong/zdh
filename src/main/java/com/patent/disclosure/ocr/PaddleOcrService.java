package com.patent.disclosure.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * PaddleOCR 本地服务实现
 * 接口地址：http://127.0.0.1:8080/ocr
 */
@Service
@ConditionalOnProperty(name = "automation.ocr.engine", havingValue = "paddleocr")
public class PaddleOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${automation.ocr.paddle.url:http://127.0.0.1:8080/ocr}")
    private String paddleUrl;

    @Override
    public OcrResult recognize(BufferedImage image, Rectangle screenRegion) {
        long start = System.currentTimeMillis();
        try {
            // 直接传整个图片，不裁剪（和百度 OCR 保持一致）
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            // 构造请求
            Map<String, Object> request = Map.of(
                    "file", base64,
                    "fileType", 1
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            // 调用接口
            String response = restTemplate.postForObject(paddleUrl, entity, String.class);
            JsonNode root = objectMapper.readTree(response);

            // 解析结果
            List<OcrElement> elements = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();

            // 返回格式：root.result.ocrResults[0].prunedResult
            JsonNode resultNode = root.path("result");
            JsonNode ocrResults = resultNode.path("ocrResults");
            if (ocrResults.isArray() && ocrResults.size() > 0) {
                JsonNode prunedResult = ocrResults.get(0).path("prunedResult");
                JsonNode recTexts = prunedResult.path("rec_texts");
                JsonNode recScores = prunedResult.path("rec_scores");
                JsonNode recBoxes = prunedResult.path("rec_boxes");

                for (int i = 0; i < recTexts.size(); i++) {
                    String text = recTexts.get(i).asText();
                    double score = recScores.get(i).asDouble();
                    JsonNode box = recBoxes.get(i);
                    int x = box.get(0).asInt();
                    int y = box.get(1).asInt();
                    int w = box.get(2).asInt() - x;
                    int h = box.get(3).asInt() - y;

                    // 转换成屏幕坐标
                    int screenX = screenRegion.x + x;
                    int screenY = screenRegion.y + y;

                    elements.add(new OcrElement(
                            text, x, y, w, h,
                            screenX, screenY, w, h,
                            score
                    ));
                    fullText.append(text).append("\n");
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            return new OcrResult(true, engineName(), "zh", fullText.toString(), elements, elapsed, "");

        } catch (Exception e) {
            log.error("PaddleOCR 识别失败: {}", e.getMessage());
            long elapsed = System.currentTimeMillis() - start;
            return new OcrResult(false, engineName(), "zh", "", new ArrayList<>(), elapsed, e.getMessage());
        }
    }

    @Override
    public String engineName() {
        return "paddleocr";
    }

    @Override
    public Map<String, Object> status() {
        return Map.of("engine", engineName(), "url", paddleUrl);
    }
}
