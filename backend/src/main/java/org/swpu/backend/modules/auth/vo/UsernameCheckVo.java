package org.swpu.backend.modules.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户名可用性检查结果")
public record UsernameCheckVo(
        @Schema(description = "规范化后的用户名", example = "new_user")
        String username,
        @Schema(description = "是否可用", example = "true")
        boolean available
) {
}
