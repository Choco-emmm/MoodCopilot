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

    @Select("SELECT DISTINCT e.user_id FROM user_life_events e " +
            "JOIN users u ON e.user_id = u.id " +
            "WHERE e.status = 'PENDING' AND e.follow_up_completed = 0 " +
            "AND e.next_follow_up_at IS NOT NULL AND e.next_follow_up_at <= #{now} " +
            "AND u.daily_notify_enabled = 1")
    List<Long> findUsersWithDueLifeEvents(@org.apache.ibatis.annotations.Param("now") java.time.LocalDateTime now);

    @org.apache.ibatis.annotations.Update("UPDATE users SET last_active_time = #{lastActiveTime} WHERE id = #{id}")
    int updateLastActiveTime(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("lastActiveTime") java.time.LocalDateTime lastActiveTime);

    @Select("SELECT * FROM users WHERE wx_open_id = #{wxOpenId} LIMIT 1")
    UserEntity findByWxOpenId(@org.apache.ibatis.annotations.Param("wxOpenId") String wxOpenId);
}
