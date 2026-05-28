package com.moodcopilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodcopilot.entity.DiaryCollectionRelationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DiaryCollectionRelationMapper extends BaseMapper<DiaryCollectionRelationEntity> {

    /**
     * 更新日记在合集中的排序顺序
     */
    @Update("UPDATE diary_collection_relation SET sort_order = #{sortOrder} WHERE collection_id = #{collectionId} AND diary_id = #{diaryId}")
    void updateSortOrder(@Param("collectionId") Long collectionId, @Param("diaryId") Long diaryId, @Param("sortOrder") Double sortOrder);
}