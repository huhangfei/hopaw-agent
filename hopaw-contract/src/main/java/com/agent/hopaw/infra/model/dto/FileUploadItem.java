package com.agent.hopaw.infra.model.dto;

import java.io.InputStream;

/**
 * 项目空间文件上传项（解耦 Spring MultipartFile，供 service 层使用）
 */
public class FileUploadItem {
    /** 原始文件名 */
    private String fileName;
    /** 文件输入流 */
    private InputStream inputStream;
    /** 文件大小（字节） */
    private long size;

    public FileUploadItem() {
    }

    public FileUploadItem(String fileName, InputStream inputStream, long size) {
        this.fileName = fileName;
        this.inputStream = inputStream;
        this.size = size;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
