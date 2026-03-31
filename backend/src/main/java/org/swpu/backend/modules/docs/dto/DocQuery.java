package org.swpu.backend.modules.docs.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 文档列表查询参数
@Schema(description = "文档列表查询参数")
public class DocQuery {
    @Schema(description = "页码(从 1 开始)", example = "1")
    private Integer page;
    @Schema(description = "每页数量", example = "10")
    private Integer pageSize;
    @Schema(description = "关键字(标题或来源)")
    private String keyword;
    @Schema(description = "文档状态", example = "READY")
    private String status;
    @Schema(description = "文档分类，可重名", example = "固井基础")
    private String category;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
