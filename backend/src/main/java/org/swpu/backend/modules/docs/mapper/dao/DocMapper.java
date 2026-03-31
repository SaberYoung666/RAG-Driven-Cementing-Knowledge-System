package org.swpu.backend.modules.docs.mapper.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.swpu.backend.modules.docs.entity.DocEntity;

// 文档表 Mapper
@Mapper
public interface DocMapper extends BaseMapper<DocEntity> {
}
