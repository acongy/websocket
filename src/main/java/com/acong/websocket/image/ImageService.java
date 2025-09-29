package com.acong.websocket.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

/**
 * 图片压缩
 *
 * @author acong
 * @since 2025-08-22
 */
public class ImageService {

    // 主压缩方法
    public static void compressImage(String inputPath, String outputPath, float quality) throws IOException {
        BufferedImage image = ImageIO.read(new File(inputPath));
        if (image == null) {
            return;
        }

        // 获取原始格式
        String formatName = inputPath.substring(inputPath.lastIndexOf('.') + 1).toLowerCase();

        // JPEG 强制转换成 RGB
        if ((formatName.equals("jpg") || formatName.equals("jpeg")) && image.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = converted.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
            image = converted;
        }

        // PNG 压缩：转换成索引颜色
        if (formatName.equals("png")) {
            image = convertToIndexed(image);
        }

        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
        if (!writers.hasNext()) {
            // 找不到 writer，降级使用 PNG
            ImageIO.write(image, "png", outputFile);
            return;
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    // 扫描目录并压缩
    public static void scanAndCompress(File rootDir, File current, File targetDir, float quality) throws IOException {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanAndCompress(rootDir, file, targetDir, quality);
            } else {
                String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
                File outputFile = new File(targetDir, relativePath);
                compressImage(file.getAbsolutePath(), outputFile.getAbsolutePath(), quality);
            }
        }
    }

    // PNG 压缩辅助：转换成索引颜色
    private static BufferedImage convertToIndexed(BufferedImage src) {
        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_INDEXED);
        Graphics2D g2d = dest.createGraphics();
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return dest;
    }
}