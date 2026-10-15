package com.patent.disclosure.ocr;

import com.patent.disclosure.automation.MirrorRegionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "automation.ocr.engine", havingValue = "tesseract", matchIfMissing = true)
public class TesseractOcrService implements OcrService {
    private final String command;
    private final String language;
    private final int psm;
    private final long timeoutMs;

    public TesseractOcrService(
            @Value("${automation.ocr.tesseract-command:tesseract}") String command,
            @Value("${automation.ocr.language:chi_sim+eng}") String language,
            @Value("${automation.ocr.psm:11}") int psm,
            @Value("${automation.ocr.timeout-ms:15000}") long timeoutMs) {
        this.command = command;
        this.language = language;
        this.psm = psm;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public OcrResult recognize(BufferedImage image, Rectangle screenRegion) {
        long start = System.currentTimeMillis();
        try {
            // 小尺寸投屏截图（如 343x599）白色小字识别率差，先放大 2 倍再识别，
            // 坐标解析时按比例映射回原图。
            int scale = 2;
            BufferedImage input = scale > 1 ? upscale(image, scale) : image;
            byte[] png = png(input);
            Process process = new ProcessBuilder(
                    command, "stdin", "stdout", "-l", language, "--psm", String.valueOf(psm), "tsv")
                    .redirectErrorStream(false)
                    .start();

            try (var output = process.getOutputStream()) {
                output.write(png);
            }

            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return failure(start, "Tesseract 超时（" + timeoutMs + "ms），请检查截图区域或 OCR 配置");
            }
            int exit = process.exitValue();
            String err = new String(stderr, StandardCharsets.UTF_8).trim();
            String tsv = new String(stdout, StandardCharsets.UTF_8);
            if (exit != 0) {
                return failure(start, "Tesseract 执行失败(exit=" + exit + "): " + (err.isEmpty() ? tsv : err));
            }

            List<OcrElement> elements = parseTsv(tsv, screenRegion, scale);
            StringBuilder allText = new StringBuilder();
            for (OcrElement e : elements) {
                if (!e.text().isBlank()) {
                    if (allText.length() > 0) allText.append('\n');
                    allText.append(e.text());
                }
            }
            return new OcrResult(true, "tesseract", language, allText.toString(), elements,
                    System.currentTimeMillis() - start, "OCR 识别成功");
        } catch (IOException e) {
            return failure(start, "无法启动 Tesseract：" + e.getMessage()
                    + "。macOS 可尝试安装 tesseract，并确认 automation.ocr.tesseract-command 配置正确。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(start, "OCR 线程被中断");
        } catch (Throwable e) {
            return failure(start, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public String command() { return command; }
    public String language() { return language; }
    public int psm() { return psm; }

    @Override
    public String engineName() { return "tesseract"; }

    @Override
    public Map<String, Object> status() {
        return Map.of(
                "engine", "tesseract",
                "command", command,
                "language", language,
                "psm", psm,
                "hint", "如果 command 找不到，请安装 Tesseract 或修改 automation.ocr.tesseract-command");
    }

    /**
     * 解析 Tesseract TSV：按视觉行合并单词（block/par/line 分组）。
     * 这样多词按钮（如 "Erase All Content and Settings"）可整体匹配，
     * 坐标取整行包围盒，点击中心即为按钮中心。
     * scale：识别前放大倍数，坐标按比例映射回原图。
     */
    private List<OcrElement> parseTsv(String tsv, Rectangle screenRegion, int scale) {
        Map<String, Line> lines = new LinkedHashMap<>();
        String[] rows = tsv.split("\\R");
        for (int i = 1; i < rows.length; i++) {
            String[] f = rows[i].split("\\t", -1);
            if (f.length < 12 || !"5".equals(f[0])) continue;
            String text = f[11].trim();
            if (text.isEmpty()) continue;
            try {
                double confidence = Double.parseDouble(f[10]);
                if (confidence < 0) continue;
                int left = Integer.parseInt(f[6]);
                int top = Integer.parseInt(f[7]);
                int width = Integer.parseInt(f[8]);
                int height = Integer.parseInt(f[9]);
                String key = f[2] + ":" + f[3] + ":" + f[4];
                lines.computeIfAbsent(key, k -> new Line())
                        .words.add(new Word(Integer.parseInt(f[5]), left, top, width, height, text, confidence));
            } catch (NumberFormatException ignored) {
                // Ignore malformed OCR rows.
            }
        }

        List<OcrElement> result = new ArrayList<>();
        for (Line line : lines.values()) {
            line.words.sort(Comparator.comparingInt(w -> w.wordNum));
            int minLeft = Integer.MAX_VALUE, minTop = Integer.MAX_VALUE;
            int maxRight = Integer.MIN_VALUE, maxBottom = Integer.MIN_VALUE;
            double minConf = Double.MAX_VALUE;
            StringBuilder sb = new StringBuilder();
            for (Word w : line.words) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(w.text);
                minLeft = Math.min(minLeft, w.left);
                minTop = Math.min(minTop, w.top);
                maxRight = Math.max(maxRight, w.left + w.width);
                maxBottom = Math.max(maxBottom, w.top + w.height);
                minConf = Math.min(minConf, w.confidence);
            }
            int x = minLeft / scale, y = minTop / scale;
            int width = (maxRight - minLeft) / scale, height = (maxBottom - minTop) / scale;
            result.add(new OcrElement(sb.toString(), x, y, width, height,
                    screenRegion.x + x, screenRegion.y + y, width, height, minConf));
        }
        return result;
    }

    /** 双线性放大图片，提升小字号识别率。 */
    private BufferedImage upscale(BufferedImage src, int factor) {
        int w = src.getWidth() * factor, h = src.getHeight() * factor;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static final class Word {
        final int wordNum, left, top, width, height;
        final String text;
        final double confidence;
        Word(int wordNum, int left, int top, int width, int height, String text, double confidence) {
            this.wordNum = wordNum; this.left = left; this.top = top;
            this.width = width; this.height = height; this.text = text; this.confidence = confidence;
        }
    }

    private static final class Line {
        final List<Word> words = new ArrayList<>();
    }

    private byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private OcrResult failure(long start, String message) {
        return new OcrResult(false, "tesseract", language, "", List.of(),
                System.currentTimeMillis() - start, message);
    }
}
