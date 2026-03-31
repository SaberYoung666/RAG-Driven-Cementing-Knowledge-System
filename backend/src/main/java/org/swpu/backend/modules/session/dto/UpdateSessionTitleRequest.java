package org.swpu.backend.modules.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "更新会话标题请求")
public class UpdateSessionTitleRequest {
    @Schema(description = "新标题", example = "固井替浆参数复盘")
    @NotBlank(message = "title is required")
    @Size(max = 200, message = "title length must be less than or equal to 200")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
