package com.moodcopilot.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {

    private final UserMapper userMapper;

    public AdminUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Page<UserEntity> searchUsers(String keyword, String sortBy, int pageNum, int pageSize) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(UserEntity::getDisplayName, keyword)
                   .or()
                   .like(UserEntity::getEmail, keyword);
        }
        if ("createdAt".equals(sortBy)) {
            wrapper.orderByDesc(UserEntity::getCreatedAt);
        } else {
            wrapper.orderByDesc(UserEntity::getLastActiveTime)
                   .orderByDesc(UserEntity::getCreatedAt);
        }
        Page<UserEntity> page = new Page<>(pageNum, pageSize);
        return userMapper.selectPage(page, wrapper);
    }

    public void updateUserStatus(Long userId, Integer status) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (status != 0 && status != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }
}
