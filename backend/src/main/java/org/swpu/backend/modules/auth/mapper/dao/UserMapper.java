package org.swpu.backend.modules.auth.mapper.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.swpu.backend.modules.auth.entity.UserEntity;

// 用户表 Mapper
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
