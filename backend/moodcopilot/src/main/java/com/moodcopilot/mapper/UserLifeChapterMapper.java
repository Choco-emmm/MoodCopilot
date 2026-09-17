package com.moodcopilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodcopilot.entity.UserLifeChapterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface UserLifeChapterMapper extends BaseMapper<UserLifeChapterEntity> {

    /** 还没有归属任何章节的最早一篇日记，用来决定新建动态阶段的起点。 */
    @Select("SELECT MIN(d.created_at) FROM diaries d " +
            "LEFT JOIN life_chapter_diaries s ON s.diary_id = d.id " +
            "WHERE d.author_user_id = #{userId} AND d.is_deleted = 0 AND s.id IS NULL")
    LocalDateTime earliestUnattachedDiaryAt(@Param("userId") Long userId);

    /** 还没有归属任何章节的最早一个重要事件。 */
    @Select("SELECT MIN(e.target_date) FROM user_life_events e " +
            "LEFT JOIN life_chapter_events s ON s.event_id = e.id " +
            "WHERE e.user_id = #{userId} AND e.deleted_at IS NULL AND s.id IS NULL")
    LocalDate earliestUnattachedEventDate(@Param("userId") Long userId);
}
