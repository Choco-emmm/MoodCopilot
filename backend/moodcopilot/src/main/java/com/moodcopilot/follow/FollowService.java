package com.moodcopilot.follow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.FollowEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.FollowMapper;
import com.moodcopilot.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowMapper followMapper;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    public FollowService(FollowMapper followMapper,
            NotificationService notificationService,
            StringRedisTemplate redisTemplate) {
        this.followMapper = followMapper;
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void follow(long followedUserId) {
        UserEntity actor = currentUser();
        if (actor.getId().equals(followedUserId)) {
            throw new ResponseStatusException(BAD_REQUEST, "不能关注自己");
        }

        boolean exists = followMapper.exists(new LambdaQueryWrapper<FollowEntity>()
                .eq(FollowEntity::getFollowerId, actor.getId())
                .eq(FollowEntity::getFollowedId, followedUserId));
        if (exists)
            return;

        FollowEntity follow = new FollowEntity();
        follow.setFollowerId(actor.getId());
        follow.setFollowedId(followedUserId);
        follow.setCreatedAt(LocalDateTime.now());
        followMapper.insert(follow);
        evictFollowingCache(actor.getId());

        notificationService.notifyFollow(actor, followedUserId);
    }

    @Transactional
    public void unfollow(long followedUserId) {
        UserEntity actor = currentUser();
        followMapper.delete(new LambdaQueryWrapper<FollowEntity>()
                .eq(FollowEntity::getFollowerId, actor.getId())
                .eq(FollowEntity::getFollowedId, followedUserId));
        evictFollowingCache(actor.getId());
    }

    public boolean isFollowing(long userId) {
        UserEntity actor = currentUser();
        return followMapper.exists(new LambdaQueryWrapper<FollowEntity>()
                .eq(FollowEntity::getFollowerId, actor.getId())
                .eq(FollowEntity::getFollowedId, userId));
    }

    public List<Long> getFollowingIds(long userId) {
        List<FollowEntity> follows = followMapper.selectList(
                new LambdaQueryWrapper<FollowEntity>()
                        .eq(FollowEntity::getFollowerId, userId));
        return follows.stream().map(FollowEntity::getFollowedId).toList();
    }

    private void evictFollowingCache(long userId) {
        try {
            for (int page = 1; page <= 20; page++) {
                for (int size : List.of(10, 20, 50)) {
                    redisTemplate.delete("following:%d:%d:%d".formatted(userId, page, size));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to evict following cache for user {}", userId, e);
        }
    }

    private UserEntity currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        throw new ResponseStatusException(BAD_REQUEST, "用户未登录");
    }
}
