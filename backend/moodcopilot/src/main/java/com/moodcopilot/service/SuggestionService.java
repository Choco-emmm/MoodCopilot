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

@Service
public class SuggestionService {

    private final SuggestionMapper suggestionMapper;
    private final UserMapper userMapper;

    public SuggestionService(SuggestionMapper suggestionMapper, UserMapper userMapper) {
        this.suggestionMapper = suggestionMapper;
        this.userMapper = userMapper;
    }

    public void submitSuggestion(Long userId, SuggestionRequest request) {
        SuggestionEntity entity = new SuggestionEntity();
        entity.setUserId(userId);
        entity.setContent(request.content());
        entity.setStatus("PENDING");
        suggestionMapper.insert(entity);
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
