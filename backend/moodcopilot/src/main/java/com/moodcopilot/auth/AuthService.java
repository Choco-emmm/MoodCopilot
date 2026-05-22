package com.moodcopilot.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtTokenProvider;
import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024;
    private static final int AVATAR_MAX_DIMENSION = 800;
    private static final float AVATAR_JPEG_QUALITY = 0.82f;

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final Path uploadRoot;
    private final JavaMailSender javaMailSender;
    private final StringRedisTemplate stringRedisTemplate;
    private final ImageCaptchaApplication imageCaptchaApplication;
    private final boolean captchaEnabled;

    @Autowired
    public AuthService(UserMapper userMapper, JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder, JavaMailSender javaMailSender,
            StringRedisTemplate stringRedisTemplate, ImageCaptchaApplication imageCaptchaApplication,
            @Value("${spring.mail.username}") String mailFrom,
            @Value("${captcha.secondary.enabled:false}") boolean captchaEnabled) {
        this(userMapper, jwtTokenProvider, passwordEncoder, Path.of("uploads"),
                javaMailSender, stringRedisTemplate, imageCaptchaApplication, mailFrom, captchaEnabled);
    }

    AuthService(UserMapper userMapper, JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder, Path uploadRoot,
            JavaMailSender javaMailSender, StringRedisTemplate stringRedisTemplate,
            ImageCaptchaApplication imageCaptchaApplication, String mailFrom, boolean captchaEnabled) {
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.uploadRoot = uploadRoot;
        this.javaMailSender = javaMailSender;
        this.stringRedisTemplate = stringRedisTemplate;
        this.imageCaptchaApplication = imageCaptchaApplication;
        this.captchaEnabled = captchaEnabled;
        this.mailFrom = mailFrom;
    }

    private static final int LOGIN_MAX_ATTEMPTS = 5;
    private static final int LOGIN_LOCK_MINUTES = 15;
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "login:locked:";
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
            sendCodeEmail(normalizedEmail, code, "MoodCopilot 注册验证码", "下面是你的注册验证码：");
            log.info("邮件发送成功: email={}", normalizedEmail);
        } catch (Exception e) {
            log.error("邮件发送失败: email={}", normalizedEmail, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "邮件发送失败");
        }

        stringRedisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(5));
        stringRedisTemplate.opsForValue().set(limitKey, "1", Duration.ofSeconds(60));
        log.info("验证码已写入Redis: email={}, codeKey={}, limitKey={}", normalizedEmail, codeKey, limitKey);
    }

    public void sendPasswordChangeCode(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }

        String normalizedEmail = user.getEmail().trim().toLowerCase();
        String codeKey = "email:pwd:code:" + normalizedEmail;
        String limitKey = "email:pwd:limit:" + normalizedEmail;

        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            throw new ResponseStatusException(BAD_REQUEST, "发送验证码太频繁，请 60 秒后再试");
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        try {
            sendCodeEmail(normalizedEmail, code, "MoodCopilot 修改密码验证码", "你正在修改账户密码，验证码如下：");
            log.info("改密验证码邮件发送成功: userId={}", userId);
        } catch (Exception e) {
            log.error("改密验证码邮件发送失败: userId={}", userId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "邮件发送失败");
        }

        stringRedisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(5));
        stringRedisTemplate.opsForValue().set(limitKey, "1", Duration.ofSeconds(60));
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "请求参数不能为空");
        }
        if (request.oldPassword() == null || request.oldPassword().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请输入旧密码");
        }
        if (request.newPassword() == null || request.newPassword().length() < 6) {
            throw new ResponseStatusException(BAD_REQUEST, "新密码至少6位");
        }
        if (request.confirmNewPassword() == null || request.confirmNewPassword().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请再次输入新密码");
        }
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "两次输入的新密码不一致");
        }
        if (request.verificationCode() == null || request.verificationCode().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "验证码不能为空");
        }

        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(BAD_REQUEST, "旧密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(BAD_REQUEST, "新密码不能与旧密码相同");
        }

        String normalizedEmail = user.getEmail().trim().toLowerCase();
        String codeKey = "email:pwd:code:" + normalizedEmail;
        String storedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (storedCode == null || !storedCode.equals(request.verificationCode().trim())) {
            throw new ResponseStatusException(BAD_REQUEST, "验证码无效或已过期");
        }

        stringRedisTemplate.delete(codeKey);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private void sendCodeEmail(String normalizedEmail, String code, String subject, String introLine) throws Exception {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(normalizedEmail);
        helper.setSubject(subject);
        helper.setText(
                """
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
                                    <p style="color:#888;font-size:14px;line-height:1.7;margin:0 0 28px;">%s</p>
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
                        """
                        .formatted(introLine, code),
                true);
        javaMailSender.send(mimeMessage);
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
        if (captchaEnabled) {
            if (request.captchaToken() == null || request.captchaToken().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "人机验证失败，请刷新页面后重试");
            }
            if (imageCaptchaApplication instanceof SecondaryVerificationApplication secApp) {
                if (!secApp.secondaryVerification(request.captchaToken())) {
                    throw new ResponseStatusException(BAD_REQUEST, "人机验证失败，请刷新页面后重试");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码服务未正确配置");
            }
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

        boolean nameTaken = userMapper.exists(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getDisplayName, request.displayName().trim()));
        if (nameTaken) {
            throw new ResponseStatusException(BAD_REQUEST, "该用户名已被使用");
        }

        UserEntity user = new UserEntity();
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(1);
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return response(token, user);
    }

    public AuthResponse login(LoginRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请输入邮箱");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请输入密码");
        }
        if (captchaEnabled) {
            if (request.captchaToken() == null || request.captchaToken().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "人机验证失败，请刷新页面后重试");
            }
            if (imageCaptchaApplication instanceof SecondaryVerificationApplication secApp) {
                if (!secApp.secondaryVerification(request.captchaToken())) {
                    throw new ResponseStatusException(BAD_REQUEST, "人机验证失败，请刷新页面后重试");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码服务未正确配置");
            }
        }

        String normalizedEmail = request.email().trim().toLowerCase();

        // 检查是否已被锁定
        String lockKey = LOGIN_LOCK_PREFIX + normalizedEmail;
        String lockTtl = stringRedisTemplate.opsForValue().get(lockKey);
        if (lockTtl != null) {
            throw new ResponseStatusException(UNAUTHORIZED,
                    "账户因多次登录失败已临时锁定，请 " + lockTtl + " 分钟后重试");
        }

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, normalizedEmail));
        if (user == null) {
            recordLoginFailure(normalizedEmail);
            throw new ResponseStatusException(UNAUTHORIZED, "邮箱或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordLoginFailure(normalizedEmail);
            throw new ResponseStatusException(UNAUTHORIZED, "邮箱或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ResponseStatusException(FORBIDDEN, "您的账号已被封禁，无法登录");
        }

        // 登录成功，清除失败记录
        stringRedisTemplate.delete(LOGIN_FAIL_PREFIX + normalizedEmail);
        stringRedisTemplate.delete(lockKey);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return response(token, user);
    }

    private void recordLoginFailure(String email) {
        try {
            String failKey = LOGIN_FAIL_PREFIX + email;
            Long attempts = stringRedisTemplate.opsForValue().increment(failKey);
            if (attempts == 1) {
                stringRedisTemplate.expire(failKey, Duration.ofMinutes(LOGIN_LOCK_MINUTES));
            }
            if (attempts != null && attempts >= LOGIN_MAX_ATTEMPTS) {
                String lockKey = LOGIN_LOCK_PREFIX + email;
                long remainingMinutes = stringRedisTemplate.getExpire(failKey);
                if (remainingMinutes > 0) {
                    stringRedisTemplate.opsForValue().set(lockKey,
                            String.valueOf(remainingMinutes / 60 + 1),
                            Duration.ofMillis(remainingMinutes));
                }
                log.warn("账户已临时锁定 email={} attempts={}", email, attempts);
            }
        } catch (Exception e) {
            log.warn("登录失败计数异常 email={}: {}", email, e.getMessage());
        }
    }

    private static final int MAX_WEEKLY_NAME_CHANGES = 3;

    public AuthResponse updateProfile(Long userId, String displayName, String avatar, String signature) {
        UserEntity user = userMapper.selectById(userId);
        if (displayName != null && !displayName.isBlank()) {
            String newName = displayName.trim();
            if (newName.length() > 64) {
                throw new ResponseStatusException(BAD_REQUEST, "用户名最多 64 个字符");
            }
            if (!newName.equals(user.getDisplayName())) {
                // 唯一性检查
                UserEntity existing = userMapper.selectOne(
                        new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getDisplayName, newName));
                if (existing != null && !existing.getId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被使用");
                }
                // 每周修改次数限制（ISO 周，周一~周日）
                int currentWeek = LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear());
                int currentYear = LocalDate.now().get(WeekFields.ISO.weekBasedYear());
                int weekKey = currentYear * 100 + currentWeek;
                Integer storedWeek = user.getNameChangeWeek();
                if (storedWeek == null || storedWeek != weekKey) {
                    user.setNameChangeCount(0);
                    user.setNameChangeWeek(weekKey);
                }
                int count = user.getNameChangeCount() != null ? user.getNameChangeCount() : 0;
                if (count >= MAX_WEEKLY_NAME_CHANGES) {
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "本周用户名修改次数已用完（每周限 " + MAX_WEEKLY_NAME_CHANGES + " 次）");
                }
                user.setNameChangeCount(count + 1);
                user.setNameChangeWeek(weekKey);
                user.setDisplayName(newName);
            }
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        if (signature != null) {
            String normalizedSignature = signature.trim();
            if (normalizedSignature.length() > 160) {
                throw new ResponseStatusException(BAD_REQUEST, "个性签名最多 160 字");
            }
            user.setSignature(normalizedSignature.isBlank() ? null : normalizedSignature);
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        evictPublicDiaryCaches();
        return response(null, user);
    }

    public UserProfileResponse profile(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new ResponseStatusException(NOT_FOUND, "用户不存在");
        }
        return new UserProfileResponse(user.getId(), user.getDisplayName(), normalizeAvatar(user.getAvatar()),
                user.getSignature());
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
        if (file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png")
                && !contentType.equals("image/webp"))) {
            throw new ResponseStatusException(BAD_REQUEST, "仅支持 JPEG/PNG/WebP 格式");
        }

        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像读取失败");
        }

        // 尝试压缩，失败则使用原始字节
        byte[] compressed = compressImage(imageBytes, contentType);
        if (compressed == null || compressed.length == 0) {
            compressed = imageBytes;
        }
        if (compressed.length > MAX_AVATAR_SIZE) {
            throw new ResponseStatusException(BAD_REQUEST, "文件过大，请选择更小的图片（上限 2MB）");
        }

        String filename = userId + "-" + System.currentTimeMillis() + ".jpg";
        Path uploadDir = uploadRoot.resolve("avatars");
        try {
            Files.createDirectories(uploadDir);
            Files.write(uploadDir.resolve(filename), compressed, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像保存失败");
        }
        String avatarUrl = "/api/uploads/avatars/" + filename;
        UserEntity user = userMapper.selectById(userId);
        user.setAvatar(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return avatarUrl;
    }

    /**
     * 压缩图片：缩放到 AVATAR_MAX_DIMENSION 以内，输出为 JPEG。
     * 如果 ImageIO 无法解码（如 WebP），返回 null，调用方回退到原始字节。
     */
    private byte[] compressImage(byte[] input, String contentType) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(input));
            if (original == null) {
                return null; // 无法解码（WebP 等），回退
            }

            int w = original.getWidth();
            int h = original.getHeight();
            int maxDim = Math.max(w, h);
            if (maxDim > AVATAR_MAX_DIMENSION) {
                double scale = (double) AVATAR_MAX_DIMENSION / maxDim;
                w = (int) (w * scale);
                h = (int) (h * scale);
            } else {
                // 尺寸不超标，如果原始文件已经在 2MB 以内就直接返回
                if (input.length <= MAX_AVATAR_SIZE) {
                    return input;
                }
            }

            BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(original, 0, 0, w, h, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(AVATAR_JPEG_QUALITY);
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(scaled, null, null), param);
                }
                writer.dispose();
            } else {
                ImageIO.write(scaled, "jpg", out);
            }

            byte[] result = out.toByteArray();
            log.info("头像压缩完成 origSize={} compressedSize={} origDim={}x{} newDim={}x{}",
                    input.length, result.length, original.getWidth(), original.getHeight(), w, h);
            return result;
        } catch (Exception e) {
            log.warn("头像压缩异常，回退原始文件: {}", e.getMessage());
            return null;
        }
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
        return new AuthResponse(token, user.getId(), user.getDisplayName(), user.getEmail(),
                normalizeAvatar(user.getAvatar()), user.getSignature(),
                user.getDailyNotifyEnabled(), role, user.getInviteCode(), user.getInviteQuota(),
                user.getExp(), user.getLevel(), user.getProExpireTime(),
                user.getNameChangeCount(), user.getNameChangeWeek());
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
