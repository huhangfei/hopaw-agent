package com.agent.hopaw.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * 验证码 Controller：生成随机字符图片验证码，答案存入 Session（不区分大小写）。
 * 通过 hopaw.captcha.enabled 开关控制是否启用。
 */
@Controller
public class CaptchaController {

    public static final String SESSION_CAPTCHA_KEY = "captcha_answer";
    /** 验证码有效期（毫秒）：5 分钟 */
    private static final long CAPTCHA_TTL_MS = 5 * 60 * 1000L;
    /** 字符集：去掉易混淆的 0/O/1/I/L */
    private static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    /** 验证码字符数 */
    private static final int CAPTCHA_LEN = 4;

    @Value("${hopaw.captcha.enabled:false}")
    private boolean captchaEnabled;

    /**
     * 获取验证码开关状态（供前端判断是否需要加载验证码）
     */
    @GetMapping("/api/auth/captcha-enabled")
    @ResponseBody
    public java.util.Map<String, Object> captchaEnabled() {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("enabled", captchaEnabled);
        return result;
    }

    /**
     * 生成随机字符图片验证码并返回 PNG 流。
     * Session 中写入验证码原文（不区分大小写）+ 过期时间戳。
     */
    @GetMapping(value = "/api/auth/captcha", produces = "image/png")
    public void generateCaptcha(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setDateHeader("Expires", 0);

        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CAPTCHA_LEN; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String text = sb.toString();

        // 存入 Session（不区分大小写）
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_CAPTCHA_KEY, text.toLowerCase());
        session.setAttribute(SESSION_CAPTCHA_KEY + "_expire", System.currentTimeMillis() + CAPTCHA_TTL_MS);

        // 绘制验证码图片
        int width = 130, height = 44;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景
        g.setColor(new Color(245, 245, 245));
        g.fillRect(0, 0, width, height);

        // 干扰线（8条）
        for (int i = 0; i < 8; i++) {
            g.setColor(new Color(160 + random.nextInt(80), 160 + random.nextInt(80), 160 + random.nextInt(80), 180));
            g.setStroke(new BasicStroke(1 + random.nextFloat()));
            g.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
        }

        // 绘制每个字符（随机旋转 + 不同颜色 + 不同大小）
        Font baseFont = new Font("SansSerif", Font.BOLD, 30);
        int totalWidth = 0;
        GlyphVector[] glyphs = new GlyphVector[CAPTCHA_LEN];
        FontRenderContext frc = g.getFontRenderContext();
        for (int i = 0; i < CAPTCHA_LEN; i++) {
            Font font = baseFont.deriveFont(26 + random.nextFloat() * 6);
            GlyphVector gv = font.createGlyphVector(frc, String.valueOf(text.charAt(i)));
            glyphs[i] = gv;
            totalWidth += (int) gv.getVisualBounds().getWidth() + 4;
        }
        int startX = (width - totalWidth) / 2;

        for (int i = 0; i < CAPTCHA_LEN; i++) {
            Graphics2D g2 = (Graphics2D) g.create();
            int charWidth = (int) glyphs[i].getVisualBounds().getWidth();
            int charHeight = (int) glyphs[i].getVisualBounds().getHeight();

            // 随机颜色（深色系，确保可辨认）
            g2.setColor(new Color(
                    20 + random.nextInt(100),
                    20 + random.nextInt(100),
                    80 + random.nextInt(120)));

            // 以字符中心旋转
            int x = startX;
            int y = height / 2 + charHeight / 4;
            double rotate = (random.nextDouble() - 0.5) * 0.6; // ±0.3 rad
            AffineTransform old = g2.getTransform();
            g2.rotate(rotate, x + charWidth / 2.0, y - charHeight / 4.0);
            g2.drawGlyphVector(glyphs[i], x, y);
            g2.setTransform(old);

            startX += charWidth + 4 + random.nextInt(3);
        }

        // 干扰噪点
        for (int i = 0; i < 40; i++) {
            int px = random.nextInt(width);
            int py = random.nextInt(height);
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200), 160));
            g.fillOval(px, py, 2, 2);
        }

        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }
}
