#!/usr/bin/env bash
set -euo pipefail

# 一键部署脚本（适用于云服务器）
# 用法：bash deploy.sh

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend/moodcopilot"

echo "[1/5] 进入项目目录: $PROJECT_DIR"
cd "$PROJECT_DIR"

echo "[2/5] 拉取最新代码"
git pull --ff-only

# 记录变更范围（与上一次部署比较）
CHANGED=$(git diff --name-only HEAD~1 2>/dev/null || echo "")
FRONTEND_CHANGED=$(echo "$CHANGED" | grep -q '^frontend/' && echo true || echo false)
BACKEND_CHANGED=$(echo "$CHANGED" | grep -q '^backend/' && echo true || echo false)

# [3/5] 构建后端 JAR（精准指向 pom.xml 所在目录）
if $BACKEND_CHANGED || [ ! -f "$BACKEND_DIR/target/"*.jar ]; then
  echo "[3/5] 后端有变更，正在通过 Docker 编译..."
  docker run --rm \
    -v "$PWD/backend/moodcopilot:/app" \
    -v "$HOME/.m2:/root/.m2" \
    -w /app \
    maven:3.9-eclipse-temurin-21 \
    mvn clean package -Dmaven.test.skip=true
else
  echo "[3/5] 后端无变更，跳过编译"
fi

# [4/5] 重建并启动容器（仅重建有变更的服务，不重启 MySQL/Redis）
echo "[4/5] 重建容器..."
if $FRONTEND_CHANGED; then
  echo "  前端有变更，重建 frontend..."
  docker compose up -d --build --no-deps frontend
  # 先启动旧版前端，避免前端中断
fi

echo "  重建 backend..."
docker compose up -d --build --no-deps backend

if ! $FRONTEND_CHANGED; then
  echo "  前端无变更，跳过"
fi

echo "[5/5] 输出服务状态"
docker compose ps

echo
echo "最近后端日志（最后 80 行）"
docker compose logs --tail=80 backend || true

echo
echo "最近前端日志（最后 40 行）"
docker compose logs --tail=40 frontend || true

echo
echo "部署完成。后端从旧→新切换期间约 15-30 秒不可用，用户重试即可。"
