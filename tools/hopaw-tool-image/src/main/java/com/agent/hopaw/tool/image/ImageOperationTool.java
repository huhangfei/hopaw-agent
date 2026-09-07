package com.agent.hopaw.tool.image;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.infra.tool.AgentTool;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;

/**
 * 图片操作工具集
 * 支持读取图片为base64、SVG代码渲染保存为图片、缩放、压缩、旋转、裁剪、格式转换等图片操作
 */
public class ImageOperationTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ImageOperationTool.class);

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"读取图片", "读取指定路径的图片文件并返回base64编码，可通过质量参数压缩图片以减少数据量", "图片读取"})
    public String readImage(
            @P(description = "图片文件路径，支持 jpg/jpeg/png/bmp/gif") String filePath,
            @P(description = "图片压缩质量(0.1-1.0)，传入时按JPEG重新编码压缩，值越小体积越小；为空则直接返回原始文件的base64", required = false) Double quality) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return "错误: 文件不存在: " + filePath;
            }
            if (!Files.isRegularFile(path)) {
                return "错误: 路径不是文件: " + filePath;
            }

            long originalSize = Files.size(path);
            String mime = detectImageMime(path);
            if (mime == null) {
                return "错误: 不是支持的图片文件(支持 jpg/jpeg/png/bmp/gif): " + filePath;
            }

            // 不压缩：直接返回原始文件内容的 base64
            if (quality == null) {
                byte[] bytes = Files.readAllBytes(path);
                StringBuilder sb = new StringBuilder();
                sb.append("图片读取成功\n");
                sb.append("格式: ").append(mime).append("\n");
                BufferedImage probe = ImageIO.read(path.toFile());
                if (probe != null) {
                    sb.append("尺寸: ").append(probe.getWidth()).append("x").append(probe.getHeight()).append("\n");
                }
                sb.append("原始大小: ").append(formatFileSize(originalSize)).append("\n");
                sb.append("base64:\n").append(Base64.getEncoder().encodeToString(bytes));
                return sb.toString();
            }

            // 压缩：按质量参数重新编码为 JPEG
            float q = (float) Math.max(0.1, Math.min(1.0, quality));
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) {
                return "错误: 无法解码图片(格式可能不受支持): " + filePath;
            }
            byte[] compressed = encodeJpeg(flattenIfAlpha(img), q);

            // 压缩无收益时回退原始数据
            if (compressed.length >= originalSize) {
                byte[] bytes = Files.readAllBytes(path);
                StringBuilder sb = new StringBuilder();
                sb.append("图片读取成功(质量 ").append(q).append(" 压缩未减小体积，已返回原始数据)\n");
                sb.append("格式: ").append(mime).append("\n");
                sb.append("尺寸: ").append(img.getWidth()).append("x").append(img.getHeight()).append("\n");
                sb.append("大小: ").append(formatFileSize(originalSize)).append("\n");
                sb.append("base64:\n").append(Base64.getEncoder().encodeToString(bytes));
                return sb.toString();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("图片读取成功\n");
            sb.append("格式: image/jpeg\n");
            sb.append("尺寸: ").append(img.getWidth()).append("x").append(img.getHeight()).append("\n");
            sb.append("原始大小: ").append(formatFileSize(originalSize)).append("\n");
            sb.append("压缩后大小: ").append(formatFileSize(compressed.length))
                    .append("(质量 ").append(q).append(")\n");
            sb.append("base64:\n").append(Base64.getEncoder().encodeToString(compressed));
            return sb.toString();
        } catch (Exception e) {
            log.error("读取图片失败: {}", filePath, e);
            return "错误: 读取图片失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"保存SVG图片", "将SVG代码渲染为位图并保存到指定路径，输出格式由文件扩展名决定(.png/.jpg/.jpeg)，SVG代码需包含xmlns命名空间", "SVG转图片"})
    public String saveSvgImage(
            @P(description = "SVG代码，根元素需包含xmlns=\"http://www.w3.org/2000/svg\"，建议指定width和height属性") String svgCode,
            @P(description = "保存图片的完整文件路径，扩展名 .png 或 .jpg/.jpeg 决定输出格式，如 D:/images/logo.png") String filePath) {
        try {
            if (svgCode == null || svgCode.isBlank()) {
                return "错误: SVG代码不能为空";
            }
            if (filePath == null || filePath.isBlank()) {
                return "错误: 文件路径不能为空";
            }
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            ImageTranscoder transcoder;
            String format;
            if (name.endsWith(".png")) {
                transcoder = new PNGTranscoder();
                format = "image/png";
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                JPEGTranscoder jpeg = new JPEGTranscoder();
                // 指定默认压缩质量，避免 Batik 未设置质量时的告警日志
                jpeg.addTranscodingHint(JPEGTranscoder.KEY_QUALITY, 0.9f);
                transcoder = jpeg;
                format = "image/jpeg";
            } else {
                return "错误: 不支持的输出格式，请使用 .png 或 .jpg/.jpeg 扩展名: " + filePath;
            }

            // 创建父目录
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            long start = System.currentTimeMillis();
            TranscoderInput input = new TranscoderInput(new StringReader(svgCode));
            try (OutputStream os = Files.newOutputStream(path)) {
                TranscoderOutput output = new TranscoderOutput(os);
                transcoder.transcode(input, output);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("SVG图片保存成功\n");
            sb.append("格式: ").append(format).append("\n");
            sb.append("路径: ").append(path).append("\n");
            sb.append("大小: ").append(formatFileSize(Files.size(path))).append("\n");
            sb.append("耗时: ").append(System.currentTimeMillis() - start).append("ms");
            return sb.toString();
        } catch (Exception e) {
            log.error("保存SVG图片失败: {}", filePath, e);
            return "错误: 保存SVG图片失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"缩放图片", "按比例或目标宽高缩放图片并保存到指定路径，可只传宽或高按比例缩放", "图片缩放"})
    public String scaleImage(
            @P(description = "源图片路径，支持 jpg/jpeg/png/bmp/gif") String sourcePath,
            @P(description = "输出图片路径，扩展名(.png/.jpg/.jpeg/.bmp/.gif)决定输出格式") String targetPath,
            @P(description = "缩放比例(0.01-10)，如 0.5 缩小一半、2 放大一倍；与宽高参数二选一，同时传入时优先使用比例", required = false) Double scale,
            @P(description = "目标宽度(像素)，与高度同时传入时保持宽高比缩放以适配目标区域", required = false) Integer width,
            @P(description = "目标高度(像素)", required = false) Integer height) {
        try {
            Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path target = Paths.get(targetPath).toAbsolutePath().normalize();
            String error = checkImageFile(source, target);
            if (error != null) {
                return error;
            }
            if (scale == null && width == null && height == null) {
                return "错误: 请指定缩放比例(scale)或目标宽度/高度(width/height)";
            }
            long start = System.currentTimeMillis();
            if (scale != null) {
                Thumbnails.of(source.toFile()).scale(Math.max(0.01, Math.min(10.0, scale))).toFile(target.toFile());
            } else if (width != null && height != null) {
                Thumbnails.of(source.toFile()).size(width, height).toFile(target.toFile());
            } else if (width != null) {
                Thumbnails.of(source.toFile()).width(width).toFile(target.toFile());
            } else {
                Thumbnails.of(source.toFile()).height(height).toFile(target.toFile());
            }
            return buildImageResult("图片缩放成功", source, target, start);
        } catch (Exception e) {
            log.error("缩放图片失败: {} -> {}", sourcePath, targetPath, e);
            return "错误: 缩放图片失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"压缩图片", "按质量参数重新编码压缩图片以减小体积，输出格式由目标路径扩展名决定，建议输出为 jpg/jpeg 以获得明显压缩效果", "图片压缩"})
    public String compressImage(
            @P(description = "源图片路径，支持 jpg/jpeg/png/bmp/gif") String sourcePath,
            @P(description = "输出图片路径，扩展名(.png/.jpg/.jpeg/.bmp/.gif)决定输出格式") String targetPath,
            @P(description = "压缩质量(0.1-1.0)，值越小体积越小，默认 0.7", required = false) Double quality) {
        try {
            Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path target = Paths.get(targetPath).toAbsolutePath().normalize();
            String error = checkImageFile(source, target);
            if (error != null) {
                return error;
            }
            double q = (quality == null) ? 0.7 : Math.max(0.1, Math.min(1.0, quality));
            long start = System.currentTimeMillis();
            Thumbnails.of(source.toFile()).scale(1.0).outputQuality(q).toFile(target.toFile());
            return buildImageResult("图片压缩成功(质量 " + q + ")", source, target, start);
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", sourcePath, targetPath, e);
            return "错误: 压缩图片失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"旋转图片", "顺时针旋转图片并保存到指定路径，支持任意角度如 90、180、270、45", "图片旋转"})
    public String rotateImage(
            @P(description = "源图片路径，支持 jpg/jpeg/png/bmp/gif") String sourcePath,
            @P(description = "输出图片路径，扩展名(.png/.jpg/.jpeg/.bmp/.gif)决定输出格式") String targetPath,
            @P(description = "顺时针旋转角度(度)，如 90、180、270 或任意角度") Double angle) {
        try {
            Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path target = Paths.get(targetPath).toAbsolutePath().normalize();
            String error = checkImageFile(source, target);
            if (error != null) {
                return error;
            }
            if (angle == null) {
                return "错误: 旋转角度不能为空";
            }
            long start = System.currentTimeMillis();
            Thumbnails.of(source.toFile()).scale(1.0).rotate(angle).toFile(target.toFile());
            return buildImageResult("图片旋转成功(" + angle + "度)", source, target, start);
        } catch (Exception e) {
            log.error("旋转图片失败: {} -> {}", sourcePath, targetPath, e);
            return "错误: 旋转图片失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"裁剪图片", "按坐标和尺寸裁剪图片区域并保存到指定路径", "图片裁剪"})
    public String cropImage(
            @P(description = "源图片路径，支持 jpg/jpeg/png/bmp/gif") String sourcePath,
            @P(description = "输出图片路径，扩展名(.png/.jpg/.jpeg/.bmp/.gif)决定输出格式") String targetPath,
            @P(description = "裁剪区域左上角X坐标(像素)") Integer x,
            @P(description = "裁剪区域左上角Y坐标(像素)") Integer y,
            @P(description = "裁剪区域宽度(像素)") Integer width,
            @P(description = "裁剪区域高度(像素)") Integer height) {
        try {
            Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path target = Paths.get(targetPath).toAbsolutePath().normalize();
            String error = checkImageFile(source, target);
            if (error != null) {
                return error;
            }
            if (x == null || y == null || width == null || height == null || width <= 0 || height <= 0 || x < 0 || y < 0) {
                return "错误: 裁剪参数无效，x/y 需大于等于 0，width/height 需大于 0";
            }
            BufferedImage src = ImageIO.read(source.toFile());
            if (src == null) {
                return "错误: 无法解码图片(格式可能不受支持): " + sourcePath;
            }
            if (x + width > src.getWidth() || y + height > src.getHeight()) {
                return "错误: 裁剪区域超出图片范围，图片尺寸 " + src.getWidth() + "x" + src.getHeight()
                        + "，裁剪区域 x=" + x + " y=" + y + " w=" + width + " h=" + height;
            }
            long start = System.currentTimeMillis();
            Thumbnails.of(source.toFile()).scale(1.0).sourceRegion(x, y, width, height).toFile(target.toFile());
            return buildImageResult("图片裁剪成功", source, target, start);
        } catch (Exception e) {
            log.error("裁剪图片失败: {} -> {}", sourcePath, targetPath, e);
            return "错误: 裁剪图片失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"转换图片格式", "将图片转换为其他格式并保存到指定路径，输出格式由目标路径扩展名决定", "图片格式转换"})
    public String convertImageFormat(
            @P(description = "源图片路径，支持 jpg/jpeg/png/bmp/gif") String sourcePath,
            @P(description = "输出图片路径，扩展名(.png/.jpg/.jpeg/.bmp/.gif)决定输出格式") String targetPath) {
        try {
            Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path target = Paths.get(targetPath).toAbsolutePath().normalize();
            String error = checkImageFile(source, target);
            if (error != null) {
                return error;
            }
            long start = System.currentTimeMillis();
            Thumbnails.of(source.toFile()).scale(1.0).toFile(target.toFile());
            return buildImageResult("图片格式转换成功", source, target, start);
        } catch (Exception e) {
            log.error("转换图片格式失败: {} -> {}", sourcePath, targetPath, e);
            return "错误: 转换图片格式失败 - " + e.getMessage();
        }
    }

    /**
     * 校验源图片文件与输出路径并创建输出父目录，返回 null 表示校验通过
     */
    private String checkImageFile(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return "错误: 源图片不存在: " + source;
        }
        if (!Files.isRegularFile(source)) {
            return "错误: 源路径不是文件: " + source;
        }
        if (target.equals(source)) {
            return "错误: 输出路径不能与源路径相同: " + target;
        }
        String name = target.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".bmp") || name.endsWith(".gif"))) {
            return "错误: 不支持的输出格式，请使用 .png/.jpg/.jpeg/.bmp/.gif 扩展名: " + target;
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return null;
    }

    /**
     * 构建图片处理结果信息（原始与新文件的尺寸、大小、耗时）
     */
    private String buildImageResult(String title, Path source, Path target, long start) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(title).append("\n");
            BufferedImage src = ImageIO.read(source.toFile());
            if (src != null) {
                sb.append("原始尺寸: ").append(src.getWidth()).append("x").append(src.getHeight()).append("\n");
            }
            sb.append("原始大小: ").append(formatFileSize(Files.size(source))).append("\n");
            BufferedImage dst = ImageIO.read(target.toFile());
            if (dst != null) {
                sb.append("新尺寸: ").append(dst.getWidth()).append("x").append(dst.getHeight()).append("\n");
            }
            sb.append("新大小: ").append(formatFileSize(Files.size(target))).append("\n");
            sb.append("路径: ").append(target).append("\n");
            sb.append("耗时: ").append(System.currentTimeMillis() - start).append("ms");
            return sb.toString();
        } catch (Exception e) {
            // 结果信息构建失败不影响处理结果本身
            return title + "\n路径: " + target;
        }
    }

    private String detectImageMime(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        return null;
    }

    /**
     * 透明通道压平：JPEG 不支持透明度，带 alpha 的图片先绘制到白色背景上
     */
    private BufferedImage flattenIfAlpha(BufferedImage src) {
        if (!src.getColorModel().hasAlpha()) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /**
     * 按指定质量将图片编码为 JPEG
     */
    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("当前JVM缺少JPEG编码器");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        }
        if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    public String getName() {
        return "imageOperation";
    }

    @Override
    public String getDescription() {
        return "图片操作工具集，支持读取图片为base64、将SVG代码渲染保存为图片、缩放、压缩、旋转、裁剪、格式转换等图片操作";
    }

    @Override
    public String getIcon() {
        return "image-operation-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "图片";
    }
}
