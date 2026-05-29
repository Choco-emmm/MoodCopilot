#!/usr/bin/env bash
set -euo pipefail

# MoodCopilot 日志清理脚本
# 清空 Docker 容器日志 + 应用日志文件
# 用法：bash clear-logs.sh

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

echo "========== MoodCopilot 日志清理 =========="
echo ""

# 1. 清理 Docker Compose 容器日志（stdout/stderr 累积）
echo "[1/3] 清理 Docker 容器日志..."
docker compose down 2>/dev/null || true

# 删除 Docker 日志文件
DOCKER_LOG_DIR="/var/lib/docker/containers"
if [ -d "$DOCKER_LOG_DIR" ]; then
  find "$DOCKER_LOG_DIR" -name "*.log" -type f -exec sh -c 'echo "" > "$1"' _ {} \; 2>/dev/null || true
  echo "  ✅ Docker 容器日志已清空"
else
  echo "  ⚠️  Docker 日志目录不存在，跳过"
fi

# 2. 清理 Docker 日志驱动（json-file 累积）
if command -v docker &> /dev/null; then
  # 获取 MoodCopilot 相关容器并清理
  for container in $(docker ps -a --filter "name=moodcopilot" --format "{{.Names}}" 2>/dev/null); do
    docker inspect "$container" --format '{{.LogPath}}' 2>/dev/null | while read -r logpath; do
      if [ -n "$logpath" ] && [ -f "$logpath" ]; then
        sudo sh -c "echo '' > '$logpath'" 2>/dev/null || sh -c "echo '' > '$logpath'" 2>/dev/null || true
        echo "  ✅ 已清空容器日志: $container"
      fi
    done
  done
fi

# 3. 清理应用日志文件（如果后端有文件日志输出）
# Spring Boot 默认输出到 stdout，由 Docker 收集，但如果配置了文件日志则一并清理
BACKEND_DIR="$PROJECT_DIR/backend/moodcopilot"
if [ -d "$BACKEND_DIR/logs" ]; then
  find "$BACKEND_DIR/logs" -name "*.log" -type f -exec sh -c 'echo "" > "$1"' _ {} \;
  echo "  ✅ 后端应用日志文件已清空"
fi
if [ -f "$BACKEND_DIR/moodcopilot.log" ]; then
  echo "" > "$BACKEND_DIR/moodcopilot.log"
  echo "  ✅ moodcopilot.log 已清空"
fi

echo ""
echo "[2/3] 重新启动服务..."
docker compose up -d 2>/dev/null || true

echo ""
echo "[3/3] 清理完成，当前日志状态："
docker compose logs --tail=3 2>/dev/null || true

echo ""
echo "========== 日志清理完成 =========="
