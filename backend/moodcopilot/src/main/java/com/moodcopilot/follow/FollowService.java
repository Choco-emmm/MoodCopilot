package com.moodcopilot.follow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.FollowEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.FollowMapper;
import com.moodcopilot.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    @jakarta.annotation.PostConstruct
    private void migrateFollowsToRedis() {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey("follow:migrated"))) {
                log.info("关注数据已迁移到 Redis，跳过");
                return;
            }
            List<FollowEntity> all = followMapper.selectList(null);
            log.info("开始迁移关注数据到 Redis，共 {} 条", all.size());
            for (FollowEntity f : all) {
                String followerKey = "following:" + f.getFollowerId();
                String followedKey = "followers:" + f.getFollowedId();
                redisTemplate.opsForSet().add(followerKey, String.valueOf(f.getFollowedId()));
                redisTemplate.opsForSet().add(followedKey, String.valueOf(f.getFollowerId()));
            }
            redisTemplate.opsForValue().set("follow:migrated", "1");
            log.info("关注数据迁移完成");
        } catch (Exception e) {
            log.warn("关注数据迁移失败，将在下次启动重试: {}", e.getMessage());
        }
    }

    public void follow(long followedUserId) {
        UserEntity actor = currentUser();
        if (actor.getId().equals(followedUserId)) {
            throw new org.springframework.web.server.ResponseStatusException(BAD_REQUEST, "不能关注自己");
        }

        String followerKey = "following:" + actor.getId();
        String followedKey = "followers:" + followedUserId;
        String uid = String.valueOf(followedUserId);
        String actorId = String.valueOf(actor.getId());

        Long added = redisTemplate.opsForSet().add(followerKey, uid);
        if (added != null && added > 0) {
            redisTemplate.opsForSet().add(followedKey, actorId);
            asyncPersistFollow(actor.getId(), followedUserId, true);
            notificationService.notifyFollow(actor, followedUserId);
        }
        evictFeedCaches(actor.getId());
    }

    public void unfollow(long followedUserId) {
        UserEntity actor = currentUser();
        String followerKey = "following:" + actor.getId();
        String followedKey = "followers:" + followedUserId;
        String uid = String.valueOf(followedUserId);
        String actorId = String.valueOf(actor.getId());

        redisTemplate.opsForSet().remove(followerKey, uid);
        redisTemplate.opsForSet().remove(followedKey, actorId);
        asyncPersistFollow(actor.getId(), followedUserId, false);
        evictFeedCaches(actor.getId());
    }

    public boolean isFollowing(long userId) {
        UserEntity actor = currentUser();
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember("following:" + actor.getId(), String.valueOf(userId)));
    }

    public List<Long> getFollowingIds(long userId) {
        Set<String> members = redisTemplate.opsForSet().members("following:" + userId);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().map(Long::valueOf).toList();
    }

    private void evictFeedCaches(long userId) {
        try {
            for (int page = 1; page <= 20; page++) {
                for (int size : List.of(10, 20, 50)) {
                    redisTemplate.delete("following:%d:%d:%d".formatted(userId, page, size));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to evict following feed cache for user {}", userId, e);
        }
    }

    @Async
    private void asyncPersistFollow(long followerId, long followedId, boolean isFollow) {
        try {
            if (isFollow) {
                FollowEntity f = new FollowEntity();
                f.setFollowerId(followerId);
                f.setFollowedId(followedId);
                f.setCreatedAt(LocalDateTime.now());
                followMapper.insert(f);
            } else {
                followMapper.delete(new LambdaQueryWrapper<FollowEntity>()
                        .eq(FollowEntity::getFollowerId, followerId)
                        .eq(FollowEntity::getFollowedId, followedId));
            }
        } catch (Exception e) {
            log.warn("异步持久化关注失败 followerId={} followedId={} isFollow={}", followerId, followedId, isFollow, e);
        }
    }

    private UserEntity currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        throw new org.springframework.web.server.ResponseStatusException(BAD_REQUEST, "用户未登录");
    }
}
