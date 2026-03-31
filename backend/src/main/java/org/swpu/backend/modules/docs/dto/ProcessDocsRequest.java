package org.swpu.backend.modules.docs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Schema(description = "批量开始处理文档请求")
@Getter
@Setter
public class ProcessDocsRequest {
    @Schema(description = "要处理的文档 ID 列表")
    @NotEmpty(message = "docIds为空")
    private List<String> docIds;
}
