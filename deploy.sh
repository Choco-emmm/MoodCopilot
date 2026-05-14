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

echo "[3/5] 构建后端 JAR"
cd "$BACKEND_DIR"
./mvnw -DskipTests clean package

echo "[4/5] 重建并启动容器"
cd "$PROJECT_DIR"
docker compose up -d --build

echo "[5/5] 输出服务状态"
docker compose ps

echo "\n最近后端日志（最后 80 行）"
docker compose logs --tail=80 backend || true

echo "\n最近前端日志（最后 40 行）"
docker compose logs --tail=40 frontend || true

echo "\n部署完成。"
