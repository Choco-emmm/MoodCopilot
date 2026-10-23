package com.moodcopilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMemoryMapper extends BaseMapper<UserProfileMemoryEntity> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM users WHERE id = #{userId} FOR UPDATE")
    Long lockOwner(@org.apache.ibatis.annotations.Param("userId") long userId);
}
