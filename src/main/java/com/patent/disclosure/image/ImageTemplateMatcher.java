package com.patent.disclosure.image;

import java.awt.image.BufferedImage;

/**
 * 纯 Java 图标模板匹配器（解决"只有图标没有名字"的 App）。
 *
 * 方法：灰度化 + 降采样 + 积分图加速的归一化互相关（NCC）滑窗扫描，
 * 先粗扫（位置步进 2）再在最佳点邻近精修，控制耗时在几百毫秒内。
 * 无任何第三方依赖，可直接打进 fat jar。
 */
public final class ImageTemplateMatcher {

    /** 匹配结果：场景内坐标（px）与相似度（NCC，-1~1，通常 >0.85 视为命中）。 */
    public record Match(int x, int y, int width, int height, double score) {
        public int centerX() { return x + width / 2; }
        public int centerY() { return y + height / 2; }
    }

    private ImageTemplateMatcher() {}

    /**
     * 在 scene 中查找 template。
     *
     * @param scene     识别区域截图（完整分辨率）
     * @param template  图标模板（PNG/JPG 解码后的图像）
     * @param threshold 相似度阈值（默认 0.85；越小越容易命中、误报越多）
     * @return 找到的匹配（null 表示低于阈值）
     */
    public static Match match(BufferedImage scene, BufferedImage template, double threshold) {
        if (scene == null || template == null) return null;
        int sw = scene.getWidth(), sh = scene.getHeight();
        int tw = template.getWidth(), th = template.getHeight();
        if (tw > sw || th > sh) return null;

        int[] sceneL = luma(scene);
        int[] tplL = luma(template);

        // 降采样 2x，减少计算量（图标通常无亚像素精度需求）
        int scale = 2;
        int ssw = sw / scale, ssh = sh / scale;
        int ttw = Math.max(2, tw / scale), tth = Math.max(2, th / scale);
        int[] sSmall = downsample(sceneL, sw, sh, scale, ssw, ssh);
        int[] tSmall = downsample(tplL, tw, th, scale, ttw, tth);

        // 积分图（和 + 平方和），O(1) 求任意窗口均值/方差
        long[] sum = integral(sSmall, ssw, ssh);
        long[] sq = integralSquare(sSmall, ssw, ssh);

        // 模板统计
        double tMean = 0, tVar = 0;
        int tn = ttw * tth;
        for (int v : tSmall) tMean += v;
        tMean /= tn;
        for (int v : tSmall) tVar += (v - tMean) * (v - tMean);
        tVar = Math.max(tVar, 1e-9);

        int maxX = ssw - ttw, maxY = ssh - tth;
        if (maxX < 0 || maxY < 0) return null;

        double best = -1;
        int bestX = 0, bestY = 0;
        // 粗扫：位置步进 2
        for (int y = 0; y <= maxY; y += 2) {
            for (int x = 0; x <= maxX; x += 2) {
                double s = ncc(sSmall, ssw, ssh, tSmall, ttw, tth,
                        sum, sq, x, y, tMean, tVar, tn);
                if (s > best) {
                    best = s;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        // 精修：最佳点邻近 5x5 全位置
        int x0 = Math.max(0, bestX - 2), x1 = Math.min(maxX, bestX + 2);
        int y0 = Math.max(0, bestY - 2), y1 = Math.min(maxY, bestY + 2);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double s = ncc(sSmall, ssw, ssh, tSmall, ttw, tth,
                        sum, sq, x, y, tMean, tVar, tn);
                if (s > best) {
                    best = s;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        if (best < threshold) return null;
        // 降采样坐标还原为原图坐标
        int rx = bestX * scale, ry = bestY * scale;
        return new Match(rx, ry, tw, th, best);
    }

    private static double ncc(int[] s, int sw, int sh, int[] t, int tw, int th,
                              long[] sum, long[] sq, int ox, int oy,
                              double tMean, double tVar, int n) {
        int x1 = ox + tw, y1 = oy + th;
        // 窗口和 / 平方和（积分图）
        long wSum = sum[idx(sw, sh, x1, y1)] - sum[idx(sw, sh, ox, y1)]
                - sum[idx(sw, sh, x1, oy)] + sum[idx(sw, sh, ox, oy)];
        long wSq = sq[idx(sw, sh, x1, y1)] - sq[idx(sw, sh, ox, y1)]
                - sq[idx(sw, sh, x1, oy)] + sq[idx(sw, sh, ox, oy)];
        double sMean = (double) wSum / n;
        double sVar = wSq - n * sMean * sMean;
        if (sVar < 1e-9) return -1;
        // 交叉项 Σ(t-sMean)(s-sMean)
        double cross = 0;
        int ti = 0;
        for (int yy = oy; yy < y1; yy++) {
            int rowBase = yy * sw + ox;
            for (int xx = 0; xx < tw; xx++) {
                cross += (t[ti] - tMean) * (s[rowBase + xx] - sMean);
                ti++;
            }
        }
        return cross / Math.sqrt(tVar * sVar);
    }

    private static int[] luma(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                out[y * w + x] = (r * 299 + g * 587 + b * 114) / 1000;
            }
        }
        return out;
    }

    /** 降采样：每 scale×scale 块取左上角像素（简单快速） */
    private static int[] downsample(int[] src, int w, int h, int scale, int dw, int dh) {
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            int sy = y * scale;
            for (int x = 0; x < dw; x++) {
                out[y * dw + x] = src[sy * w + x * scale];
            }
        }
        return out;
    }

    private static long[] integral(int[] a, int w, int h) {
        long[] out = new long[(w + 1) * (h + 1)];
        for (int y = 1; y <= h; y++) {
            long row = 0;
            int base = y * (w + 1);
            int prev = base - (w + 1);
            for (int x = 1; x <= w; x++) {
                row += a[(y - 1) * w + (x - 1)];
                out[base + x] = out[prev + x] + row;
            }
        }
        return out;
    }

    private static long[] integralSquare(int[] a, int w, int h) {
        long[] out = new long[(w + 1) * (h + 1)];
        for (int y = 1; y <= h; y++) {
            long row = 0;
            int base = y * (w + 1);
            int prev = base - (w + 1);
            for (int x = 1; x <= w; x++) {
                long v = a[(y - 1) * w + (x - 1)];
                row += v * v;
                out[base + x] = out[prev + x] + row;
            }
        }
        return out;
    }

    private static int idx(int w, int h, int x, int y) {
        return y * (w + 1) + x;
    }
}
