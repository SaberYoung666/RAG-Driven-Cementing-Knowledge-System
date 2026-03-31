package org.swpu.backend.modules.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "创建会话请求")
public class CreateSessionRequest {
    @Schema(description = "会话标题，不传则使用默认标题", example = "水泥浆密度优化讨论")
    @Size(max = 200, message = "title length must be less than or equal to 200")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
