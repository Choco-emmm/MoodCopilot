package com.moodcopilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodcopilot.entity.ChatConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationEntity> {

    /** Serializes immutable conversation Persona version allocation. */
    @Select("SELECT * FROM chat_conversations WHERE id = #{id} FOR UPDATE")
    ChatConversationEntity selectByIdForUpdate(@Param("id") Long id);
}
