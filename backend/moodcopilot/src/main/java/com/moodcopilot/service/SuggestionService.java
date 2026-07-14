package com.moodcopilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.dto.SuggestionDTO;
import com.moodcopilot.dto.SuggestionRequest;
import com.moodcopilot.entity.SuggestionEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.SuggestionMapper;
import com.moodcopilot.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    private final SuggestionMapper suggestionMapper;
    private final UserMapper userMapper;
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    public SuggestionService(SuggestionMapper suggestionMapper, UserMapper userMapper, JavaMailSender javaMailSender) {
        this.suggestionMapper = suggestionMapper;
        this.userMapper = userMapper;
        this.javaMailSender = javaMailSender;
    }

    public void submitSuggestion(Long userId, SuggestionRequest request) {
        SuggestionEntity entity = new SuggestionEntity();
        entity.setUserId(userId);
        entity.setContent(request.content());
        entity.setStatus("PENDING");
        suggestionMapper.insert(entity);
        sendSuggestionEmailToAdmins(userId, request.content());
    }

    private void sendSuggestionEmailToAdmins(Long userId, String content) {
        try {
            // Find all admins
            List<UserEntity> admins = userMapper.selectList(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getRole, "ADMIN"));
            for (UserEntity admin : admins) {
                if (admin.getEmail() != null && admin.getEmail().contains("@")) {
                    MimeMessage mimeMessage = javaMailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                    helper.setFrom(mailFrom);
                    helper.setTo(admin.getEmail());
                    helper.setSubject("MoodCopilot 新的意见反馈");
                    helper.setText("收到来自用户 ID: " + userId + " 的新反馈：\n\n" + content);
                    javaMailSender.send(mimeMessage);
                }
            }
        } catch (Exception e) {
            log.error("发送意见反馈邮件给管理员失败", e);
        }
    }

    public Map<String, Object> listAllSuggestions(int page, int size) {
        Page<SuggestionEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<SuggestionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SuggestionEntity::getCreatedAt);

        suggestionMapper.selectPage(p, wrapper);

        List<SuggestionEntity> records = p.getRecords();
        if (records.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("total", p.getTotal());
            result.put("items", List.of());
            return result;
        }

        List<Long> userIds = records.stream().map(SuggestionEntity::getUserId).distinct().toList();
        List<UserEntity> users = userMapper.selectBatchIds(userIds);
        Map<Long, UserEntity> userMap = users.stream().collect(Collectors.toMap(UserEntity::getId, u -> u));

        List<SuggestionDTO> items = records.stream().map(s -> {
            UserEntity u = userMap.get(s.getUserId());
            return new SuggestionDTO(
                    s.getId(),
                    s.getUserId(),
                    u != null ? u.getDisplayName() : "Unknown User",
                    u != null ? u.getAvatar() : null,
                    s.getContent(),
                    s.getStatus(),
                    s.getCreatedAt()
            );
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("total", p.getTotal());
        result.put("items", items);
        return result;
    }
}
