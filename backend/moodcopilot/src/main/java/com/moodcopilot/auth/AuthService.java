package com.moodcopilot.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private static final long MAX_AVATAR_SIZE = 10L * 1024 * 1024;

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final Path uploadRoot;
    private final JavaMailSender javaMailSender;
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public AuthService(UserMapper userMapper, JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder, JavaMailSender javaMailSender,
            StringRedisTemplate stringRedisTemplate,
            @Value("${spring.mail.username}") String mailFrom) {
        this(userMapper, jwtTokenProvider, passwordEncoder, Path.of("uploads"),
                javaMailSender, stringRedisTemplate, mailFrom);
    }

    AuthService(UserMapper userMapper, JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder, Path uploadRoot,
            JavaMailSender javaMailSender, StringRedisTemplate stringRedisTemplate,
            String mailFrom) {
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.uploadRoot = uploadRoot;
        this.javaMailSender = javaMailSender;
        this.stringRedisTemplate = stringRedisTemplate;
        this.mailFrom = mailFrom;
    }

    private static final String MASTER_INVITE_CODE = "MOOD-MASTER-2026";
    private static final int DEFAULT_INVITE_QUOTA = 3;
    private static final int INVITE_CODE_LENGTH = 6;
    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_MAX_RETRIES = 10;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final String mailFrom;

    public void sendVerificationCode(String email) {
        log.info("发送验证码请求: email={}", email);
        if (email == null || !email.contains("@")) {
            throw new ResponseStatusException(BAD_REQUEST, "邮箱格式不正确");
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (userMapper.exists(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, normalizedEmail))) {
            log.info("发送验证码拒绝-邮箱已注册: email={}", normalizedEmail);
            throw new ResponseStatusException(BAD_REQUEST, "该邮箱已注册，请直接登录");
        }

        String codeKey = "email:code:" + normalizedEmail;
        String limitKey = "email:limit:" + normalizedEmail;

        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            log.info("发送验证码被限流: email={}", normalizedEmail);
            throw new ResponseStatusException(BAD_REQUEST, "发送验证码太频繁，请 60 秒后再试");
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        log.info("生成验证码: email={}, code={}", normalizedEmail, code);

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(email.trim());
            helper.setSubject("MoodCopilot 登录验证码");
            helper.setText("""
                    <!DOCTYPE html>
                    <html><head><meta charset="UTF-8"></head>
                    <body style="margin:0;padding:0;background-color:#f7f3eb;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f7f3eb;padding:40px 0;">
                    <tr><td align="center">
                    <table width="460" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.06);">
                      <tr>
                        <td style="background-color:#4a7c62;padding:28px 36px;text-align:center;">
                          <span style="color:#ffffff;font-size:22px;font-weight:600;letter-spacing:1px;">MoodCopilot</span>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:36px;">
                          <p style="color:#444;font-size:15px;line-height:1.7;margin:0 0 8px;">嗨，欢迎来到 MoodCopilot ⊂(◉‿◉)つ</p>
                          <p style="color:#888;font-size:14px;line-height:1.7;margin:0 0 28px;">下面是你的登录验证码，输入即可完成注册：</p>
                          <div style="background-color:#f5f0e8;border-radius:8px;padding:22px 16px;text-align:center;margin-bottom:28px;">
                            <span style="font-family:'SF Mono',Menlo,Monaco,monospace;font-size:34px;font-weight:700;letter-spacing:8px;color:#4a7c62;">%s</span>
                          </div>
                          <p style="color:#aaa;font-size:12px;line-height:1.6;text-align:center;margin:0;">验证码 5 分钟内有效，请勿转发给他人。</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="border-top:1px solid #eee;padding:16px 36px;text-align:center;">
                          <p style="color:#ccc;font-size:11px;margin:0;">这是自动发送的系统邮件，无需回复。</p>
                        </td>
                      </tr>
                    </table>
                    </td></tr>
                    </table>
                    </body></html>
                    """.formatted(code), true);
            javaMailSender.send(mimeMessage);
            log.info("邮件发送成功: email={}", normalizedEmail);
        } catch (Exception e) {
            log.error("邮件发送失败: email={}", normalizedEmail, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "邮件发送失败");
        }

        stringRedisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(5));
        stringRedisTemplate.opsForValue().set(limitKey, "1", Duration.ofSeconds(60));
        log.info("验证码已写入Redis: email={}, codeKey={}, limitKey={}", normalizedEmail, codeKey, limitKey);
    }

    @org.springframework.transaction.annotation.Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "用户名不能为空");
        }
        if (request.email() == null || !request.email().contains("@")) {
            throw new ResponseStatusException(BAD_REQUEST, "邮箱格式不正确");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new ResponseStatusException(BAD_REQUEST, "密码至少6位");
        }
        if (request.inviteCode() == null || request.inviteCode().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "内测阶段需要邀请码才能注册");
        }

        if (request.verificationCode() == null || request.verificationCode().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "验证码不能为空");
        }
        String codeKey = "email:code:" + request.email().trim().toLowerCase();
        String storedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (storedCode == null || !storedCode.equals(request.verificationCode().trim())) {
            log.info("验证码校验失败: email={}, received={}, stored={}",
                    request.email().trim().toLowerCase(), request.verificationCode().trim(), storedCode);
            throw new ResponseStatusException(BAD_REQUEST, "验证码无效或已过期");
        }
        stringRedisTemplate.delete(codeKey);
        log.info("验证码校验成功并已销毁: email={}", request.email().trim().toLowerCase());

        boolean exists = userMapper.exists(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, request.email()));
        if (exists) {
            throw new ResponseStatusException(BAD_REQUEST, "邮箱已被注册");
        }

        // 验证邀请码
        boolean isMasterCode = MASTER_INVITE_CODE.equals(request.inviteCode().trim());
        UserEntity inviter = null;
        if (!isMasterCode) {
            inviter = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getInviteCode, request.inviteCode().trim()));
            if (inviter == null) {
                throw new ResponseStatusException(BAD_REQUEST, "邀请码无效");
            }
            if (inviter.getInviteQuota() == null || inviter.getInviteQuota() <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "该邀请码的名额已用完");
            }
            // 扣减邀请人名额
            inviter.setInviteQuota(inviter.getInviteQuota() - 1);
            userMapper.updateById(inviter);
        }

        UserEntity user = new UserEntity();
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(1);
        user.setRole("USER");
        user.setInviteCode(generateUniqueInviteCode());
        user.setInviteQuota(DEFAULT_INVITE_QUOTA);
        user.setInvitedBy(isMasterCode ? null : inviter.getId());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return response(token, user);
    }

    private String generateUniqueInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int attempt = 0; attempt < INVITE_CODE_MAX_RETRIES; attempt++) {
            sb.setLength(0);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                sb.append(INVITE_CODE_CHARS.charAt(secureRandom.nextInt(INVITE_CODE_CHARS.length())));
            }
            String code = sb.toString();
            boolean codeExists = userMapper.exists(
                    new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getInviteCode, code));
            if (!codeExists) {
                return code;
            }
        }
        // 重试耗尽后使用 UUID 兜底，确保永不失败
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, INVITE_CODE_LENGTH).toUpperCase();
    }

    public AuthResponse login(LoginRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请输入邮箱");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请输入密码");
        }

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, request.email().trim().toLowerCase()));
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "邮箱或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "邮箱或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return response(token, user);
    }

    public AuthResponse updateProfile(Long userId, String displayName, String avatar) {
        UserEntity user = userMapper.selectById(userId);
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        evictPublicDiaryCaches();
        return response(null, user);
    }

    private void evictPublicDiaryCaches() {
        try {
            for (int page = 0; page <= 5; page++) {
                for (int size : java.util.List.of(10, 20, 50)) {
                    stringRedisTemplate.delete("public:diaries:%d:%d".formatted(page, size));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to evict public diary caches", e);
        }
    }

    public void updateSettings(Long userId, Boolean dailyNotifyEnabled) {
        UserEntity user = userMapper.selectById(userId);
        user.setDailyNotifyEnabled(dailyNotifyEnabled);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_AVATAR_SIZE) {
            throw new ResponseStatusException(BAD_REQUEST, "文件大小不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png")
                && !contentType.equals("image/webp"))) {
            throw new ResponseStatusException(BAD_REQUEST, "仅支持 JPEG/PNG/WebP 格式");
        }
        String ext = contentType.equals("image/png") ? "png" : contentType.equals("image/webp") ? "webp" : "jpg";
        String filename = userId + "-" + System.currentTimeMillis() + "." + ext;
        Path uploadDir = uploadRoot.resolve("avatars");
        try {
            Files.createDirectories(uploadDir);
            Files.write(uploadDir.resolve(filename), file.getBytes(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像上传失败");
        }
        String avatarUrl = "/api/uploads/avatars/" + filename;
        UserEntity user = userMapper.selectById(userId);
        user.setAvatar(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return avatarUrl;
    }

    public AuthResponse me(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        return response(null, user);
    }

    private AuthResponse response(String token, UserEntity user) {
        String role = user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole();
        return new AuthResponse(token, user.getId(), user.getDisplayName(), normalizeAvatar(user.getAvatar()),
                user.getDailyNotifyEnabled(), role, user.getInviteCode(), user.getInviteQuota());
    }

    private String normalizeAvatar(String avatar) {
        if (avatar == null || avatar.isBlank()) {
            return avatar;
        }
        String normalized = avatar.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
            return normalized;
        }
        if (normalized.startsWith("/api/uploads/")) {
            return normalized;
        }
        if (normalized.startsWith("/uploads/")) {
            return "/api" + normalized;
        }
        if (normalized.startsWith("uploads/")) {
            return "/api/" + normalized;
        }
        return normalized;
    }
}
