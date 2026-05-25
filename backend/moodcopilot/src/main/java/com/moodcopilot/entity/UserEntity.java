package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("users")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String displayName;
    private String email;
    private String passwordHash;
    private Integer status;
    private String role;
    private Boolean isVip;
    private String avatar;
    private String signature;
    private String theme;
    private String lightTheme;
    private String darkTheme;
    private String themeMode;
    private Boolean dailyNotifyEnabled;
    private Boolean profileNotifyEnabled;
    private String inviteCode;
    private Integer inviteQuota;
    private Long invitedBy;
    private Integer exp = 0;
    private Integer level = 1;
    private LocalDateTime proExpireTime;
    private Integer nameChangeCount = 0;
    private Integer nameChangeWeek = 0;
    private LocalDateTime lastActiveTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getIsVip() {
        return isVip;
    }

    public void setIsVip(Boolean isVip) {
        this.isVip = isVip;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getLightTheme() {
        return lightTheme;
    }

    public void setLightTheme(String lightTheme) {
        this.lightTheme = lightTheme;
    }

    public String getDarkTheme() {
        return darkTheme;
    }

    public void setDarkTheme(String darkTheme) {
        this.darkTheme = darkTheme;
    }

    public String getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(String themeMode) {
        this.themeMode = themeMode;
    }

    public Boolean getDailyNotifyEnabled() {
        return dailyNotifyEnabled;
    }

    public void setDailyNotifyEnabled(Boolean dailyNotifyEnabled) {
        this.dailyNotifyEnabled = dailyNotifyEnabled;
    }

    public Boolean getProfileNotifyEnabled() {
        return profileNotifyEnabled;
    }

    public void setProfileNotifyEnabled(Boolean profileNotifyEnabled) {
        this.profileNotifyEnabled = profileNotifyEnabled;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public Integer getInviteQuota() {
        return inviteQuota;
    }

    public void setInviteQuota(Integer inviteQuota) {
        this.inviteQuota = inviteQuota;
    }

    public Long getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(Long invitedBy) {
        this.invitedBy = invitedBy;
    }

    public Integer getExp() {
        return exp;
    }

    public void setExp(Integer exp) {
        this.exp = exp;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public LocalDateTime getProExpireTime() {
        return proExpireTime;
    }

    public void setProExpireTime(LocalDateTime proExpireTime) {
        this.proExpireTime = proExpireTime;
    }

    public Integer getNameChangeCount() {
        return nameChangeCount;
    }

    public void setNameChangeCount(Integer nameChangeCount) {
        this.nameChangeCount = nameChangeCount;
    }

    public Integer getNameChangeWeek() {
        return nameChangeWeek;
    }

    public void setNameChangeWeek(Integer nameChangeWeek) {
        this.nameChangeWeek = nameChangeWeek;
    }

    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(LocalDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
