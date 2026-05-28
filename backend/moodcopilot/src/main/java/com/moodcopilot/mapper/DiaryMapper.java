package com.moodcopilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moodcopilot.entity.DiaryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DiaryMapper extends BaseMapper<DiaryEntity> {

    @Select("<script>" +
            "SELECT d.* FROM diaries d " +
            "INNER JOIN diary_collection_relation r ON d.id = r.diary_id " +
            "WHERE r.collection_id = #{collectionId} " +
            "<choose>" +
            "  <when test=\"sortBy == 'DIARY_CREATE_TIME_ASC'\">" +
            "    ORDER BY d.created_at ASC" +
            "  </when>" +
            "  <when test=\"sortBy == 'DIARY_CREATE_TIME_DESC'\">" +
            "    ORDER BY d.created_at DESC" +
            "  </when>" +
            "  <otherwise>" +
            "    ORDER BY r.sort_order DESC" +
            "  </otherwise>" +
            "</choose>" +
            "</script>")
    IPage<DiaryEntity> selectDiariesByCollectionId(IPage<DiaryEntity> page,
                                                   @Param("collectionId") Long collectionId,
                                                   @Param("sortBy") String sortBy);
}
