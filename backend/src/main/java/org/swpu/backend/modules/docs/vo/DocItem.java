package org.swpu.backend.modules.docs.vo;

import io.swagger.v3.oas.annotations.media.Schema;

// 文档列表返回对象
@Schema(description = "文档条目")
public class DocItem {
    @Schema(description = "文档 ID", example = "doc-001")
    private String docId;
    @Schema(description = "文档标题")
    private String title;
    @Schema(description = "文档来源")
    private String source;
    @Schema(description = "上传时间(ISO-8601)")
    private String uploadTime;
    @Schema(description = "文档分类，可重名")
    private String category;
    @Schema(description = "是否默认文档", example = "false")
    private Boolean isDefault;
    @Schema(description = "版本号")
    private String version;
    @Schema(description = "文件哈希")
    private String hash;
    @Schema(description = "文档状态", example = "READY")
    private String status;
    @Schema(description = "切片数量", example = "32")
    private Integer chunkCount;

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(String uploadTime) {
        this.uploadTime = uploadTime;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }
}
