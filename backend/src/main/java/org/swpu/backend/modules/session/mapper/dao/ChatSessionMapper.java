package org.swpu.backend.modules.session.mapper.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.swpu.backend.modules.session.entity.ChatSessionEntity;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
