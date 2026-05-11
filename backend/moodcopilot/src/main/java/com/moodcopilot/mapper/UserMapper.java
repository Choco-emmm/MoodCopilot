package com.moodcopilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodcopilot.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT DISTINCT d.author_user_id FROM diaries d " +
            "JOIN users u ON d.author_user_id = u.id " +
            "WHERE DATE(d.created_at) = CURDATE() - INTERVAL 1 DAY " +
            "AND u.daily_notify_enabled = 1")
    List<Long> findActiveUsersWithDiariesYesterday();
}
