#!/usr/bin/env bash
set -euo pipefail

# MoodCopilot 数据备份脚本
# 用法：bash backup.sh            # 备份到默认目录 ./backups
#       bash backup.sh /mnt/backup # 备份到指定目录
#       bash backup.sh --cron      # cron 模式（静默输出，适合定时任务）
#
# 建议 cron：每天凌晨 3 点执行
#   0 3 * * * cd /path/to/MoodCopilot && bash backup.sh --cron

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_ROOT="${1:-$PROJECT_DIR/backups}"
CRON_MODE=false
if [ "${2:-}" = "--cron" ] || [ "${1:-}" = "--cron" ]; then
  CRON_MODE=true
  BACKUP_ROOT="${BACKUP_ROOT}"
fi

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DATE=$(date +%Y%m%d)
BACKUP_DIR="$BACKUP_ROOT/$DATE"
mkdir -p "$BACKUP_DIR"

log() {
  if ! $CRON_MODE; then
    echo "[$(date +%H:%M:%S)] $1"
  fi
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$BACKUP_DIR/backup.log"
}

cd "$PROJECT_DIR"

# 从 .env 加载数据库密码
if [ -f .env ]; then
  set -a && source .env && set +a
fi
DB_PASS="${DB_PASSWORD:-root}"
DB_NAME="${DB_NAME:-mood_copilot_db}"

KEEP_DAYS=7

log "========== MoodCopilot 备份开始 =========="

# ── 1. MySQL 数据库备份 ──
log "[1/4] 备份 MySQL 数据库..."
MYSQL_FILE="$BACKUP_DIR/mysql-$TIMESTAMP.sql.gz"
if docker compose exec -T mysql mysqldump \
    -uroot -p"$DB_PASS" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    "$DB_NAME" 2>> "$BACKUP_DIR/backup.log" | gzip > "$MYSQL_FILE"; then
  MYSQL_SIZE=$(du -h "$MYSQL_FILE" | cut -f1)
  log "  ✅ MySQL 备份完成 ($MYSQL_SIZE): $MYSQL_FILE"
else
  log "  ❌ MySQL 备份失败！"
fi

# ── 2. Redis RDB 备份 ──
log "[2/4] 备份 Redis 数据..."
# 触发后台 RDB 快照
docker compose exec -T redis redis-cli BGSAVE >> "$BACKUP_DIR/backup.log" 2>&1 || true
sleep 3  # 等待 BGSAVE 完成

# 从 volume 复制 RDB 文件
RDB_FILE="$BACKUP_DIR/redis-dump-$TIMESTAMP.rdb"
REDIS_DATA_PATH=$(docker volume inspect --format '{{.Mountpoint}}' "${PROJECT_DIR##*/}_redis_data" 2>/dev/null || echo "")
if [ -n "$REDIS_DATA_PATH" ] && [ -f "$REDIS_DATA_PATH/dump.rdb" ]; then
  cp "$REDIS_DATA_PATH/dump.rdb" "$RDB_FILE"
  RDB_SIZE=$(du -h "$RDB_FILE" | cut -f1)
  log "  ✅ Redis RDB 备份完成 ($RDB_SIZE): $RDB_FILE"
else
  # fallback: 直接通过 redis-cli 导出
  RDB_FILE="$BACKUP_DIR/redis-dump-$TIMESTAMP.rdb"
  # 尝试从容器内复制
  if docker compose cp redis:/data/dump.rdb "$RDB_FILE" 2>> "$BACKUP_DIR/backup.log"; then
    RDB_SIZE=$(du -h "$RDB_FILE" | cut -f1)
    log "  ✅ Redis RDB 备份完成 (via docker cp, $RDB_SIZE): $RDB_FILE"
  else
    log "  ⚠️  Redis RDB 备份失败（volume 不可访问），跳过"
  fi
fi

# ── 3. 用户头像备份 ──
log "[3/4] 备份用户头像..."
AVATAR_FILE="$BACKUP_DIR/avatars-$TIMESTAMP.tar.gz"
UPLOADS_PATH=$(docker volume inspect --format '{{.Mountpoint}}' "${PROJECT_DIR##*/}_uploads_data" 2>/dev/null || echo "")
if [ -n "$UPLOADS_PATH" ] && [ -d "$UPLOADS_PATH" ]; then
  tar -czf "$AVATAR_FILE" -C "$(dirname "$UPLOADS_PATH")" "$(basename "$UPLOADS_PATH")" 2>> "$BACKUP_DIR/backup.log"
  AVATAR_SIZE=$(du -h "$AVATAR_FILE" | cut -f1)
  log "  ✅ 头像备份完成 ($AVATAR_SIZE): $AVATAR_FILE"
else
  if docker compose cp backend:/app/uploads/avatars "$BACKUP_DIR/avatars-$TIMESTAMP" 2>> "$BACKUP_DIR/backup.log"; then
    tar -czf "$AVATAR_FILE" -C "$BACKUP_DIR" "avatars-$TIMESTAMP"
    rm -rf "$BACKUP_DIR/avatars-$TIMESTAMP"
    log "  ✅ 头像备份完成 (via docker cp): $AVATAR_FILE"
  else
    log "  ⚠️  头像备份失败，跳过"
  fi
fi

# ── 4. 清理过期备份 ──
log "[4/4] 清理 $KEEP_DAYS 天前的旧备份..."
DELETED=0
find "$BACKUP_ROOT" -maxdepth 1 -type d -mtime +$KEEP_DAYS 2>/dev/null | while read -r old_dir; do
  # 跳过非日期格式的目录
  dirname=$(basename "$old_dir")
  if [[ "$dirname" =~ ^[0-9]{8}$ ]]; then
    rm -rf "$old_dir"
    DELETED=$((DELETED + 1))
    log "  🗑️  已删除: $old_dir"
  fi
done

# ── 汇总 ──
BACKUP_COUNT=$(find "$BACKUP_DIR" -type f ! -name 'backup.log' | wc -l)
TOTAL_SIZE=$(du -sh "$BACKUP_DIR" | cut -f1)
log "========== 备份完成：$BACKUP_COUNT 个文件，总计 $TOTAL_SIZE =========="
log "备份目录: $BACKUP_DIR"

if ! $CRON_MODE; then
  echo ""
  echo "========== 备份报告 =========="
  echo "目录: $BACKUP_DIR"
  echo "文件:"
  find "$BACKUP_DIR" -type f ! -name 'backup.log' -exec ls -lh {} \; | awk '{print "  " $5 "  " $9}'
  echo "总计: $TOTAL_SIZE"
  echo ""
  echo "⚠️  注意：OSS 图片（moodcopilot bucket）不在此脚本覆盖范围内。"
  echo "   请到阿里云控制台配置 OSS 跨区域复制或生命周期备份策略。"
fi
