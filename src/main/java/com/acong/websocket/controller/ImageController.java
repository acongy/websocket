package com.acong.websocket.controller;

import com.acong.websocket.image.ImageService;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author ACong
 * @since 2025/8/22 9:52
 */
@RestController
@RequestMapping("/api/image")
public class ImageController {

    private static final Path TEMP_DIR = Paths.get("tmp");
    private static final Path COMPRESS_DIR = TEMP_DIR.resolve("compressed");

    @PostConstruct
    public void init() throws IOException {
        // 清空临时目录
        if (Files.exists(TEMP_DIR)) {
            FileSystemUtils.deleteRecursively(TEMP_DIR);
        }
        Files.createDirectories(TEMP_DIR);
        Files.createDirectories(COMPRESS_DIR);
    }

    /**
     * 上传文件到临时目录并压缩
     */
    @PostMapping("/upload")
    public String uploadFiles(@RequestParam("files") List<MultipartFile> files,
                              @RequestParam("quality") float quality) throws IOException {
        System.out.println("开始上传");
        for (MultipartFile file : files) {
            String relativePath = file.getOriginalFilename().replace("/", File.separator).replace("\\", File.separator);
            Path dest = TEMP_DIR.resolve(relativePath).normalize();
            Files.createDirectories(dest.getParent());
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 压缩到 COMPRESS_DIR
        ImageService.scanAndCompress(TEMP_DIR.toFile(), TEMP_DIR.toFile(), COMPRESS_DIR.toFile(), quality);

        return "上传并压缩完成，质量=" + quality;
    }

    /**
     * 下载整个压缩包（只包含压缩后的文件），下载完成后删除压缩目录
     */
    @GetMapping("/download/zip")
    public void downloadZip(HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"compressed.zip\"");

        // 只遍历压缩目录
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            if (Files.exists(COMPRESS_DIR)) {
                Files.walk(COMPRESS_DIR)
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                // 相对路径保存在 ZIP 里
                                zos.putNextEntry(new ZipEntry(COMPRESS_DIR.relativize(path).toString()));
                                Files.copy(path, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }

        // 下载完成后删除压缩目录
        FileSystemUtils.deleteRecursively(TEMP_DIR);
        Files.createDirectories(COMPRESS_DIR);
    }
}