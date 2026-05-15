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

# [3/5] 构建后端 JAR (精准指向 pom.xml 所在目录)
echo "[3/5] 正在进入 moodcopilot 目录并开始通过 Docker 编译..."
docker run --rm \
  -v "$PWD/backend/moodcopilot:/app" \
  -v "$HOME/.m2:/root/.m2" \
  -w /app \
  maven:3.9-eclipse-temurin-21 \
  mvn clean package -Dmaven.test.skip=true

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
