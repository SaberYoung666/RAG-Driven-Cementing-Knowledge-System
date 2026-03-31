package org.swpu.backend.modules.logging.mapper.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.swpu.backend.modules.logging.entity.SystemLogEntity;

@Mapper
public interface SystemLogMapper extends BaseMapper<SystemLogEntity> {
}
