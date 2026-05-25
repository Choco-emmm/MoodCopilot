package com.moodcopilot.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "lastActiveTime") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<UserEntity> userPage = adminUserService.searchUsers(keyword, sortBy, page, size);
        userPage.getRecords().forEach(u -> u.setEmail(obfuscateEmail(u.getEmail())));
        return ApiResponse.ok(Map.of(
                "items", userPage.getRecords(),
                "total", userPage.getTotal(),
                "page", userPage.getCurrent(),
                "size", userPage.getSize()
        ));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Void> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return ApiResponse.error(400, "status is required");
        }
        adminUserService.updateUserStatus(id, status);
        return ApiResponse.ok();
    }

    private String obfuscateEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf("@");
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() > 4) {
            return name.substring(0, 4) + "****" + domain;
        } else if (name.length() > 1) {
            return name.substring(0, 1) + "****" + domain;
        }
        return "****" + domain;
    }
}
