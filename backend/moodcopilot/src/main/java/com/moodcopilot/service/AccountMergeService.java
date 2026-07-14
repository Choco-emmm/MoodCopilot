package com.moodcopilot.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountMergeService {

    private static final Logger log = LoggerFactory.getLogger(AccountMergeService.class);

    @Autowired
    private ChatConversationMapper chatConversationMapper;
    @Autowired
    private DiaryCollectionMapper diaryCollectionMapper;
    @Autowired
    private DiaryCommentMapper diaryCommentMapper;
    @Autowired
    private DiaryHideMapper diaryHideMapper;
    @Autowired
    private DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper;
    @Autowired
    private DiaryMapper diaryMapper;
    @Autowired
    private DiaryRecommendationExposureMapper diaryRecommendationExposureMapper;
    @Autowired
    private DiaryResonanceMapper diaryResonanceMapper;
    @Autowired
    private DiarySummaryMapper diarySummaryMapper;
    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired
    private SuggestionMapper suggestionMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserProfileMemoryMapper userProfileMemoryMapper;

    /**
     * Merge sourceUser's data into targetUser.
     * 
     * @param sourceUserId The temporary WeChat user account
     * @param targetUserId The existing Email user account
     */
    @Transactional(rollbackFor = Exception.class)
    public void mergeAccounts(Long sourceUserId, Long targetUserId) {
        log.info("Starting account merge. source={}, target={}", sourceUserId, targetUserId);

        UserEntity sourceUser = userMapper.selectById(sourceUserId);
        UserEntity targetUser = userMapper.selectById(targetUserId);

        if (sourceUser == null || targetUser == null) {
            throw new IllegalArgumentException("Invalid user IDs for merging");
        }

        // 1. Update all related tables
        chatConversationMapper.update(null, new LambdaUpdateWrapper<ChatConversationEntity>()
                .eq(ChatConversationEntity::getUserId, sourceUserId)
                .set(ChatConversationEntity::getUserId, targetUserId));

        diaryCollectionMapper.update(null, new LambdaUpdateWrapper<DiaryCollectionEntity>()
                .eq(DiaryCollectionEntity::getUserId, sourceUserId)
                .set(DiaryCollectionEntity::getUserId, targetUserId));

        diaryCommentMapper.update(null, new LambdaUpdateWrapper<DiaryCommentEntity>()
                .eq(DiaryCommentEntity::getAuthorUserId, sourceUserId)
                .set(DiaryCommentEntity::getAuthorUserId, targetUserId));

        diaryHideMapper.update(null, new LambdaUpdateWrapper<DiaryHideEntity>()
                .eq(DiaryHideEntity::getUserId, sourceUserId)
                .set(DiaryHideEntity::getUserId, targetUserId));

        diaryKnowledgeGraphMapper.update(null, new LambdaUpdateWrapper<DiaryKnowledgeGraphEntity>()
                .eq(DiaryKnowledgeGraphEntity::getUserId, sourceUserId)
                .set(DiaryKnowledgeGraphEntity::getUserId, targetUserId));

        diaryMapper.update(null, new LambdaUpdateWrapper<DiaryEntity>()
                .eq(DiaryEntity::getAuthorUserId, sourceUserId)
                .set(DiaryEntity::getAuthorUserId, targetUserId));

        diaryRecommendationExposureMapper.update(null, new LambdaUpdateWrapper<DiaryRecommendationExposureEntity>()
                .eq(DiaryRecommendationExposureEntity::getUserId, sourceUserId)
                .set(DiaryRecommendationExposureEntity::getUserId, targetUserId));

        diaryResonanceMapper.update(null, new LambdaUpdateWrapper<DiaryResonanceEntity>()
                .eq(DiaryResonanceEntity::getUserId, sourceUserId)
                .set(DiaryResonanceEntity::getUserId, targetUserId));

        diarySummaryMapper.update(null, new LambdaUpdateWrapper<DiarySummaryEntity>()
                .eq(DiarySummaryEntity::getUserId, sourceUserId)
                .set(DiarySummaryEntity::getUserId, targetUserId));

        followMapper.update(null, new LambdaUpdateWrapper<FollowEntity>()
                .eq(FollowEntity::getFollowerId, sourceUserId)
                .set(FollowEntity::getFollowerId, targetUserId));
        followMapper.update(null, new LambdaUpdateWrapper<FollowEntity>()
                .eq(FollowEntity::getFollowedId, sourceUserId)
                .set(FollowEntity::getFollowedId, targetUserId));

        notificationMapper.update(null, new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getRecipientUserId, sourceUserId)
                .set(NotificationEntity::getRecipientUserId, targetUserId));
        notificationMapper.update(null, new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getActorUserId, sourceUserId)
                .set(NotificationEntity::getActorUserId, targetUserId));

        suggestionMapper.update(null, new LambdaUpdateWrapper<SuggestionEntity>()
                .eq(SuggestionEntity::getUserId, sourceUserId)
                .set(SuggestionEntity::getUserId, targetUserId));

        userProfileMemoryMapper.update(null, new LambdaUpdateWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, sourceUserId)
                .set(UserProfileMemoryEntity::getUserId, targetUserId));

        // 2. Merge user stats and profile if missing
        targetUser.setExp(targetUser.getExp() + sourceUser.getExp());
        
        if (sourceUser.getWxOpenId() != null && targetUser.getWxOpenId() == null) {
            targetUser.setWxOpenId(sourceUser.getWxOpenId());
        }

        if (sourceUser.getAvatar() != null && !sourceUser.getAvatar().isBlank() && 
            (targetUser.getAvatar() == null || targetUser.getAvatar().isBlank())) {
            targetUser.setAvatar(sourceUser.getAvatar());
        }

        if (sourceUser.getSignature() != null && !sourceUser.getSignature().isBlank() && 
            (targetUser.getSignature() == null || targetUser.getSignature().isBlank())) {
            targetUser.setSignature(sourceUser.getSignature());
        }

        // 3. Delete source user FIRST to free up unique constraints like wx_open_id
        userMapper.deleteById(sourceUserId);

        userMapper.updateById(targetUser);

        log.info("Account merge completed.");
    }
}
