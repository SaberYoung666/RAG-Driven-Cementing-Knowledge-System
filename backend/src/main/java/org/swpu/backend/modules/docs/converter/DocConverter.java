package org.swpu.backend.modules.docs.converter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.swpu.backend.modules.docs.entity.DocEntity;
import org.swpu.backend.modules.docs.vo.DocItem;

// 文档实体与返回对象转换
public final class DocConverter {
    private DocConverter() {
    }

    public static DocItem toItem(DocEntity entity) {
        if (entity == null) {
            return null;
        }
        DocItem item = new DocItem();
        item.setDocId(entity.getDocId());
        item.setTitle(entity.getTitle());
        item.setSource(entity.getSource());
        item.setUploadTime(entity.getUploadTime() == null ? null : entity.getUploadTime().toString());
        item.setCategory(entity.getCategory());
        item.setIsDefault(entity.getIsDefault());
        item.setVersion(entity.getVersion());
        item.setHash(entity.getHash());
        item.setStatus(entity.getStatus());
        item.setChunkCount(entity.getChunkCount());
        return item;
    }

    public static List<DocItem> toItems(List<DocEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .filter(Objects::nonNull)
                .map(DocConverter::toItem)
                .collect(Collectors.toList());
    }
}
