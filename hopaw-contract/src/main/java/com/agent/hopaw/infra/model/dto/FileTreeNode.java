package com.agent.hopaw.infra.model.dto;

import java.util.List;

/**
 * 项目空间文件树节点
 */
public class FileTreeNode {
    /** 节点名称（文件/目录名） */
    private String name;
    /** 相对项目空间根目录的路径（如 "subdir/file.txt"） */
    private String path;
    /** 节点类型：directory / file */
    private String type;
    /** 文件大小（字节），目录为 0 */
    private Long size;
    /** 最后修改时间（毫秒时间戳） */
    private Long lastModified;
    /** 子节点列表（仅目录有） */
    private List<FileTreeNode> children;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getLastModified() {
        return lastModified;
    }

    public void setLastModified(Long lastModified) {
        this.lastModified = lastModified;
    }

    public List<FileTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<FileTreeNode> children) {
        this.children = children;
    }
}
