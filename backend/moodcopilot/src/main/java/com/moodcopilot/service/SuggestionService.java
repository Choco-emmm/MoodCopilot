package com.moodcopilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.common.ContentFilter;
import com.moodcopilot.common.TextSnippetUtil;
import com.moodcopilot.dto.CreateSuggestionReplyRequest;
import com.moodcopilot.dto.SuggestionDTO;
import com.moodcopilot.dto.SuggestionReplyDTO;
import com.moodcopilot.dto.SuggestionRequest;
import com.moodcopilot.entity.SuggestionEntity;
import com.moodcopilot.entity.SuggestionReplyEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.SuggestionMapper;
import com.moodcopilot.mapper.SuggestionReplyMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "READ", "RESOLVED");
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final SuggestionMapper suggestionMapper;
    private final SuggestionReplyMapper suggestionReplyMapper;
    private final UserMapper userMapper;
    private final JavaMailSender javaMailSender;
    private final NotificationService notificationService;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public SuggestionService(
            SuggestionMapper suggestionMapper,
            SuggestionReplyMapper suggestionReplyMapper,
            UserMapper userMapper,
            JavaMailSender javaMailSender,
            NotificationService notificationService) {
        this.suggestionMapper = suggestionMapper;
        this.suggestionReplyMapper = suggestionReplyMapper;
        this.userMapper = userMapper;
        this.javaMailSender = javaMailSender;
        this.notificationService = notificationService;
    }

    public static boolean isAdmin(UserEntity user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String role = user.getRole().trim();
        return "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    @Transactional
    public SuggestionDTO submitSuggestion(UserEntity user, SuggestionRequest request) {
        requireAuthenticatedUser(user);
        String content = normalizeText(request != null ? request.content() : null, "建议内容不能为空");

        LocalDateTime now = LocalDateTime.now();
        SuggestionEntity entity = new SuggestionEntity();
        entity.setUserId(user.getId());
        entity.setContent(ContentFilter.filter(content));
        entity.setStatus("PENDING");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        suggestionMapper.insert(entity);

        sendSuggestionEmailToAdmins(user, entity.getContent());

        return new SuggestionDTO(
                entity.getId(),
                entity.getUserId(),
                resolveDisplayName(user),
                user.getAvatar(),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                0,
                List.of()
        );
    }

    public Map<String, Object> listMySuggestions(UserEntity user, int page, int size) {
        requireAuthenticatedUser(user);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));

        Page<SuggestionEntity> p = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<SuggestionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SuggestionEntity::getUserId, user.getId())
                .orderByDesc(SuggestionEntity::getUpdatedAt)
                .orderByDesc(SuggestionEntity::getCreatedAt);

        suggestionMapper.selectPage(p, wrapper);
        return buildPagedResult(p);
    }

    public Map<String, Object> listAllSuggestions(UserEntity user, int page, int size, String status) {
        requireAdmin(user);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));

        Page<SuggestionEntity> p = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<SuggestionEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            String normalizedStatus = normalizeStatus(status);
            wrapper.eq(SuggestionEntity::getStatus, normalizedStatus);
        }
        wrapper.orderByDesc(SuggestionEntity::getUpdatedAt)
                .orderByDesc(SuggestionEntity::getCreatedAt);

        suggestionMapper.selectPage(p, wrapper);
        return buildPagedResult(p);
    }

    public SuggestionDTO getSuggestion(UserEntity user, Long suggestionId) {
        requireAuthenticatedUser(user);
        SuggestionEntity suggestion = findSuggestionOrThrow(suggestionId);
        requireParticipantOrAdmin(user, suggestion);
        return hydrateSuggestions(List.of(suggestion)).get(0);
    }

    @Transactional
    public SuggestionDTO addReply(UserEntity user, Long suggestionId, CreateSuggestionReplyRequest request) {
        requireAuthenticatedUser(user);
        SuggestionEntity suggestion = findSuggestionOrThrow(suggestionId);
        requireParticipantOrAdmin(user, suggestion);

        String content = normalizeText(request != null ? request.content() : null, "回复内容不能为空");
        Long parentReplyId = request != null ? request.parentReplyId() : null;
        SuggestionReplyEntity parentReply = null;
        Long rootReplyId = null;

        if (parentReplyId != null) {
            parentReply = suggestionReplyMapper.selectById(parentReplyId);
            if (parentReply == null || !suggestionId.equals(parentReply.getSuggestionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "被回复的留言不存在");
            }
            rootReplyId = parentReply.getRootReplyId() != null ? parentReply.getRootReplyId() : parentReply.getId();
        }

        boolean actorIsAdmin = isAdmin(user);
        LocalDateTime now = LocalDateTime.now();

        SuggestionReplyEntity reply = new SuggestionReplyEntity();
        reply.setSuggestionId(suggestionId);
        reply.setParentReplyId(parentReplyId);
        reply.setRootReplyId(rootReplyId);
        reply.setAuthorUserId(user.getId());
        reply.setAuthorName(resolveDisplayName(user));
        reply.setAuthorRole(actorIsAdmin ? "ADMIN" : "USER");
        reply.setContent(ContentFilter.filter(content));
        reply.setIsDeleted(false);
        reply.setCreatedAt(now);
        reply.setUpdatedAt(now);
        suggestionReplyMapper.insert(reply);

        boolean statusChanged = false;
        if (actorIsAdmin && "PENDING".equals(suggestion.getStatus())) {
            suggestion.setStatus("READ");
            statusChanged = true;
        } else if (!actorIsAdmin && "RESOLVED".equals(suggestion.getStatus())) {
            suggestion.setStatus("PENDING");
            statusChanged = true;
        }
        suggestion.setUpdatedAt(now);
        if (statusChanged || suggestion.getId() != null) {
            suggestionMapper.updateById(suggestion);
        }

        notifyParticipantsOnReply(user, actorIsAdmin, suggestion, parentReply, reply.getContent());
        if (!actorIsAdmin) {
            sendReplyEmailToAdmins(user, suggestion, parentReply, reply.getContent());
        }
        return hydrateSuggestions(List.of(suggestion)).get(0);
    }

    @Transactional
    public SuggestionDTO deleteReply(UserEntity user, Long suggestionId, Long replyId) {
        requireAuthenticatedUser(user);
        SuggestionEntity suggestion = findSuggestionOrThrow(suggestionId);
        requireParticipantOrAdmin(user, suggestion);

        SuggestionReplyEntity reply = suggestionReplyMapper.selectById(replyId);
        if (reply == null || !suggestionId.equals(reply.getSuggestionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "回复不存在");
        }
        if (!reply.getAuthorUserId().equals(user.getId()) && !isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能删除自己的回复或由管理员操作");
        }

        suggestionReplyMapper.deleteById(replyId);
        return hydrateSuggestions(List.of(suggestion)).get(0);
    }

    @Transactional
    public SuggestionDTO updateStatus(UserEntity user, Long suggestionId, String status) {
        requireAdmin(user);
        SuggestionEntity suggestion = findSuggestionOrThrow(suggestionId);
        String normalizedStatus = normalizeStatus(status);
        suggestion.setStatus(normalizedStatus);
        suggestion.setUpdatedAt(LocalDateTime.now());
        suggestionMapper.updateById(suggestion);
        return hydrateSuggestions(List.of(suggestion)).get(0);
    }

    private Map<String, Object> buildPagedResult(Page<SuggestionEntity> p) {
        List<SuggestionEntity> records = p.getRecords();
        Map<String, Object> result = new HashMap<>();
        result.put("total", p.getTotal());
        result.put("items", records == null || records.isEmpty() ? List.of() : hydrateSuggestions(records));
        return result;
    }

    private List<SuggestionDTO> hydrateSuggestions(List<SuggestionEntity> records) {
        List<Long> suggestionIds = records.stream()
                .map(SuggestionEntity::getId)
                .toList();

        List<SuggestionReplyEntity> allReplies = suggestionIds.isEmpty()
                ? List.of()
                : suggestionReplyMapper.selectList(
                        new LambdaQueryWrapper<SuggestionReplyEntity>()
                                .in(SuggestionReplyEntity::getSuggestionId, suggestionIds)
                                .orderByAsc(SuggestionReplyEntity::getCreatedAt)
                                .orderByAsc(SuggestionReplyEntity::getId)
                );

        Set<Long> userIds = new HashSet<>();
        for (SuggestionEntity s : records) {
            if (s.getUserId() != null) {
                userIds.add(s.getUserId());
            }
        }
        for (SuggestionReplyEntity r : allReplies) {
            if (r.getAuthorUserId() != null) {
                userIds.add(r.getAuthorUserId());
            }
        }

        Map<Long, UserEntity> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<UserEntity> users = userMapper.selectBatchIds(userIds);
            if (users != null) {
                for (UserEntity u : users) {
                    userMap.put(u.getId(), u);
                }
            }
        }

        Map<Long, List<SuggestionReplyEntity>> repliesBySuggestion = allReplies.stream()
                .collect(Collectors.groupingBy(SuggestionReplyEntity::getSuggestionId));

        return records.stream().map(s -> {
            UserEntity owner = userMap.get(s.getUserId());
            List<SuggestionReplyEntity> suggestionReplies = repliesBySuggestion.getOrDefault(s.getId(), List.of());
            List<SuggestionReplyDTO> replyTree = buildReplyTree(suggestionReplies, userMap);
            return new SuggestionDTO(
                    s.getId(),
                    s.getUserId(),
                    owner != null ? resolveDisplayName(owner) : "未知用户",
                    owner != null ? owner.getAvatar() : null,
                    s.getContent(),
                    s.getStatus(),
                    s.getCreatedAt(),
                    s.getUpdatedAt() != null ? s.getUpdatedAt() : s.getCreatedAt(),
                    suggestionReplies.size(),
                    replyTree
            );
        }).toList();
    }

    private List<SuggestionReplyDTO> buildReplyTree(List<SuggestionReplyEntity> entities, Map<Long, UserEntity> userMap) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        List<SuggestionReplyEntity> sorted = entities.stream()
                .sorted(Comparator
                        .comparing(SuggestionReplyEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SuggestionReplyEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Set<Long> activeTopLevelIds = sorted.stream()
                .filter(r -> r.getRootReplyId() == null)
                .map(SuggestionReplyEntity::getId)
                .collect(Collectors.toSet());

        Map<Long, String> authorNameByReplyId = new HashMap<>();
        for (SuggestionReplyEntity r : sorted) {
            authorNameByReplyId.put(r.getId(), resolveReplyAuthorName(r, userMap));
        }

        List<SuggestionReplyEntity> topLevel = new ArrayList<>();
        Map<Long, List<SuggestionReplyEntity>> childrenByRoot = new HashMap<>();

        for (SuggestionReplyEntity r : sorted) {
            if (r.getRootReplyId() == null || !activeTopLevelIds.contains(r.getRootReplyId())) {
                topLevel.add(r);
            } else {
                childrenByRoot.computeIfAbsent(r.getRootReplyId(), k -> new ArrayList<>()).add(r);
            }
        }

        return topLevel.stream().map(root -> {
            List<SuggestionReplyDTO> children = childrenByRoot.getOrDefault(root.getId(), List.of()).stream()
                    .map(child -> toReplyDto(
                            child,
                            userMap,
                            child.getParentReplyId() != null ? authorNameByReplyId.get(child.getParentReplyId()) : null,
                            List.of()
                    ))
                    .toList();
            String rootReplyTo = root.getParentReplyId() != null
                    ? authorNameByReplyId.get(root.getParentReplyId())
                    : null;
            return toReplyDto(root, userMap, rootReplyTo, children);
        }).toList();
    }

    private SuggestionReplyDTO toReplyDto(
            SuggestionReplyEntity entity,
            Map<Long, UserEntity> userMap,
            String replyToUserName,
            List<SuggestionReplyDTO> children) {
        UserEntity author = userMap.get(entity.getAuthorUserId());
        String authorName = resolveReplyAuthorName(entity, userMap);
        String authorAvatar = author != null ? author.getAvatar() : null;
        String authorRole = entity.getAuthorRole() != null ? entity.getAuthorRole() : "USER";
        return new SuggestionReplyDTO(
                entity.getId(),
                entity.getSuggestionId(),
                entity.getParentReplyId(),
                entity.getRootReplyId(),
                entity.getAuthorUserId(),
                authorName,
                authorAvatar,
                authorRole,
                replyToUserName,
                entity.getContent(),
                entity.getCreatedAt(),
                children
        );
    }

    private void notifyParticipantsOnReply(
            UserEntity actor,
            boolean actorIsAdmin,
            SuggestionEntity suggestion,
            SuggestionReplyEntity parentReply,
            String replyContent) {
        String snippet = TextSnippetUtil.generateSnippet(replyContent, 36);
        String actorName = resolveDisplayName(actor);

        if (actorIsAdmin) {
            if (!actor.getId().equals(suggestion.getUserId())) {
                notificationService.notifyFeedbackReply(
                        actor,
                        suggestion.getId(),
                        suggestion.getUserId(),
                        "管理员 " + actorName + " 回复了你的反馈：" + snippet
                );
            }
            return;
        }

        Set<Long> recipientAdminIds = new LinkedHashSet<>();
        if (parentReply != null && parentReply.getAuthorUserId() != null
                && !parentReply.getAuthorUserId().equals(actor.getId())) {
            recipientAdminIds.add(parentReply.getAuthorUserId());
        }

        List<UserEntity> admins = findAdmins();
        for (UserEntity admin : admins) {
            if (admin.getId() != null && !admin.getId().equals(actor.getId())) {
                recipientAdminIds.add(admin.getId());
            }
        }

        String msg = actorName + " 回复了意见反馈：" + snippet;
        for (Long adminId : recipientAdminIds) {
            notificationService.notifyFeedbackReply(actor, suggestion.getId(), adminId, msg);
        }
    }

    private void sendSuggestionEmailToAdmins(UserEntity submitter, String content) {
        try {
            if (javaMailSender == null || mailFrom == null || mailFrom.isBlank()) {
                return;
            }
            List<UserEntity> admins = findAdmins();
            for (UserEntity admin : admins) {
                if (admin.getEmail() != null && admin.getEmail().contains("@")) {
                    MimeMessage mimeMessage = javaMailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                    helper.setFrom(mailFrom);
                    helper.setTo(admin.getEmail());
                    helper.setSubject("MoodCopilot 新的意见反馈");
                    helper.setText("收到来自用户 " + resolveDisplayName(submitter) + " (ID: " + submitter.getId() + ") 的新反馈：\n\n" + content);
                    javaMailSender.send(mimeMessage);
                }
            }
        } catch (Exception e) {
            log.error("发送意见反馈邮件给管理员失败", e);
        }
    }

    private void sendReplyEmailToAdmins(
            UserEntity replier,
            SuggestionEntity suggestion,
            SuggestionReplyEntity parentReply,
            String replyContent) {
        try {
            if (javaMailSender == null || mailFrom == null || mailFrom.isBlank()) {
                return;
            }
            List<UserEntity> admins = findAdmins();
            StringBuilder body = new StringBuilder();
            body.append("用户 ")
                    .append(resolveDisplayName(replier))
                    .append(" (ID: ")
                    .append(replier.getId())
                    .append(") 在反馈 #")
                    .append(suggestion.getId())
                    .append(" 中发布了新回复：\n\n")
                    .append(replyContent)
                    .append("\n\n---\n原反馈内容：\n")
                    .append(suggestion.getContent());
            if (parentReply != null && parentReply.getContent() != null && !parentReply.getContent().isBlank()) {
                body.append("\n\n被回复的留言（")
                        .append(parentReply.getAuthorName() != null ? parentReply.getAuthorName() : "未知")
                        .append("）：\n")
                        .append(parentReply.getContent());
            }

            String text = body.toString();
            for (UserEntity admin : admins) {
                if (admin.getEmail() != null && admin.getEmail().contains("@")) {
                    MimeMessage mimeMessage = javaMailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                    helper.setFrom(mailFrom);
                    helper.setTo(admin.getEmail());
                    helper.setSubject("MoodCopilot 意见反馈新回复");
                    helper.setText(text);
                    javaMailSender.send(mimeMessage);
                }
            }
        } catch (Exception e) {
            log.error("发送意见反馈回复邮件给管理员失败 suggestionId={}", suggestion.getId(), e);
        }
    }

    private List<UserEntity> findAdmins() {
        List<UserEntity> admins = userMapper.selectList(
                new LambdaQueryWrapper<UserEntity>().in(UserEntity::getRole, List.of("ADMIN", "ROLE_ADMIN"))
        );
        return admins != null ? admins : List.of();
    }

    private SuggestionEntity findSuggestionOrThrow(Long suggestionId) {
        if (suggestionId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在");
        }
        SuggestionEntity suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在");
        }
        return suggestion;
    }

    private void requireAuthenticatedUser(UserEntity user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
    }

    private void requireAdmin(UserEntity user) {
        requireAuthenticatedUser(user);
        if (!isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可访问全部反馈");
        }
    }

    private void requireParticipantOrAdmin(UserEntity user, SuggestionEntity suggestion) {
        if (!suggestion.getUserId().equals(user.getId()) && !isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看或回复该反馈");
        }
    }

    private String normalizeText(String raw, String emptyMessage) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, emptyMessage);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容不能超过" + MAX_CONTENT_LENGTH + "字");
        }
        return trimmed;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        String upper = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的反馈状态: " + status);
        }
        return upper;
    }

    private String resolveReplyAuthorName(SuggestionReplyEntity reply, Map<Long, UserEntity> userMap) {
        UserEntity user = userMap.get(reply.getAuthorUserId());
        if (user != null) {
            return resolveDisplayName(user);
        }
        return reply.getAuthorName() != null && !reply.getAuthorName().isBlank() ? reply.getAuthorName() : "未知用户";
    }

    private String resolveDisplayName(UserEntity user) {
        if (user == null) {
            return "未知用户";
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return "用户#" + user.getId();
    }
}
