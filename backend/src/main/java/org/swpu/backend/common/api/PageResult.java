package org.swpu.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// 分页返回对象
@Schema(description = "分页数据")
public final class PageResult<T> {
    @Schema(description = "当前页数据")
    private final List<T> items;
    @Schema(description = "总记录数", example = "100")
    private final long total;
    @Schema(description = "当前页码(从 1 开始)", example = "1")
    private final int page;
    @Schema(description = "每页大小", example = "10")
    private final int size;

    // 构造分页结果，items 会被复制以防外部修改
    public PageResult(List<T> items, long total, int page, int size) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    // 静态工厂方法
    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        return new PageResult<>(items, total, page, size);
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
