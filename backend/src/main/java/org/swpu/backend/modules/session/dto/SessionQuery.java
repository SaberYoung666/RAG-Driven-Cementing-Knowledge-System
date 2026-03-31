package org.swpu.backend.modules.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "会话列表查询参数")
public class SessionQuery {
    @Schema(description = "页码(从 1 开始)", example = "1")
    private Integer page;

    @Schema(description = "每页数量", example = "20")
    private Integer pageSize;

    @Schema(description = "按标题关键字筛选")
    private String keyword;

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
}
