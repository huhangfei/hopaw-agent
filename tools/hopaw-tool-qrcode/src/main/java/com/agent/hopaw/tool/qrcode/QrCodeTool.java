package com.agent.hopaw.tool.qrcode;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.tool.AgentTool;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码工具集
 * 支持生成二维码图片和解析二维码图片内容
 */
public class QrCodeTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(QrCodeTool.class);

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;

    @Override
    public String getName() {
        return "qrCodeTool";
    }

    @Override
    public String getDescription() {
        return "二维码工具集，支持生成二维码图片和解析二维码图片内容";
    }

    @Override
    public String getIcon() {
        return "qrcode-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "二维码,qrcode,QR,扫码";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"生成二维码", "将文本或链接生成二维码图片，保存到指定路径"})
    public String generateQrCode(
            @P(description = "要编码的文本或链接") String content,
            @P(description = "保存路径，如 /path/to/qr.png，不传则返回Base64") String savePath,
            @P(description = "图片宽度(像素)，默认300", required = false) Integer width,
            @P(description = "图片高度(像素)，默认300", required = false) Integer height) {
        try {
            if (content == null || content.isBlank()) {
                return "错误: 编码内容不能为空";
            }

            int w = width != null && width > 0 ? width : DEFAULT_WIDTH;
            int h = height != null && height > 0 ? height : DEFAULT_HEIGHT;

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);

            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, w, h, hints);

            if (savePath != null && !savePath.isBlank()) {
                Path path = Paths.get(savePath).toAbsolutePath().normalize();
                Path parent = path.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                String format = getFormat(savePath);
                MatrixToImageWriter.writeToPath(matrix, format, path);
                return "二维码已保存到: " + path + " (" + w + "x" + h + ")";
            } else {
                // 返回Base64
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
                ImageIO.write(image, "PNG", baos);
                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                return "二维码Base64 (data:image/png;base64," + base64 + ")";
            }
        } catch (Exception e) {
            log.error("生成二维码失败", e);
            return "错误: 生成二维码失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"解析二维码", "解析二维码图片文件，返回编码的文本内容"})
    public String parseQrCode(
            @P(description = "二维码图片文件路径") String filePath) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return "错误: 文件不存在: " + filePath;
            }

            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                return "错误: 无法读取图片文件: " + filePath;
            }

            LuminanceSource source = new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

            Result result = new MultiFormatReader().decode(bitmap, hints);

            StringBuilder sb = new StringBuilder();
            sb.append("解析结果: ").append(result.getText()).append("\n");
            sb.append("格式: ").append(result.getBarcodeFormat()).append("\n");
            if (result.getResultMetadata() != null && !result.getResultMetadata().isEmpty()) {
                sb.append("元数据: ").append(result.getResultMetadata());
            }
            return sb.toString();
        } catch (com.google.zxing.NotFoundException e) {
            return "错误: 未找到二维码 - 图片中可能不包含有效的二维码";
        } catch (Exception e) {
            log.error("解析二维码失败: {}", filePath, e);
            return "错误: 解析二维码失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"解析二维码Base64", "解析Base64编码的二维码图片，返回编码的文本内容"})
    public String parseQrCodeFromBase64(
            @P(description = "Base64编码的图片数据（支持data:image/png;base64,前缀或纯Base64）") String base64Data) {
        try {
            if (base64Data == null || base64Data.isBlank()) {
                return "错误: Base64数据不能为空";
            }

            // 去除data URL前缀
            String pureBase64 = base64Data;
            if (base64Data.contains(",")) {
                pureBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            byte[] imageBytes = Base64.getDecoder().decode(pureBase64);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return "错误: 无法解码图片数据";
            }

            LuminanceSource source = new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

            Result result = new MultiFormatReader().decode(bitmap, hints);

            return "解析结果: " + result.getText();
        } catch (com.google.zxing.NotFoundException e) {
            return "错误: 未找到二维码 - 图片中可能不包含有效的二维码";
        } catch (Exception e) {
            log.error("解析二维码失败", e);
            return "错误: 解析二维码失败 - " + e.getMessage();
        }
    }

    private String getFormat(String path) {
        if (path == null) return "PNG";
        String lower = path.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "JPEG";
        if (lower.endsWith(".gif")) return "GIF";
        if (lower.endsWith(".bmp")) return "BMP";
        return "PNG";
    }
}
