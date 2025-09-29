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

    public static void compressImage(String inputPath, String outputPath, float quality) throws IOException {
        BufferedImage image = ImageIO.read(new File(inputPath));
        if (image == null) {
            return;
        }

        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage convertedImg = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = convertedImg.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
            image = convertedImg;
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("No JPEG writer found");
        ImageWriter writer = writers.next();

        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        try (ImageOutputStream output = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

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
}