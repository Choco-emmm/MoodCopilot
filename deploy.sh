#!/usr/bin/env bash
set -euo pipefail

# 一键部署脚本（适用于云服务器）
# 用法：bash deploy.sh

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend/moodcopilot"

echo "[1/7] 进入项目目录: $PROJECT_DIR"
cd "$PROJECT_DIR"

# 从 .env 加载环境变量
if [ -f .env ]; then
  set -a && source .env && set +a
fi

# 关键变量校验
required_vars="JWT_SECRET TURNSTILE_SECRET_KEY TURNSTILE_SITE_KEY"
missing=""
for var in $required_vars; do
  if [ -z "${!var:-}" ]; then
    missing="$missing $var"
  fi
done
if [ -n "$missing" ]; then
  echo "❌ 缺少必需的环境变量:$missing"
  echo "   请在 .env 文件中配置后重试"
  exit 1
fi
echo "✅ 环境变量检查通过"

echo "[2/7] 拉取最新代码"
git pull --ff-only

# 记录变更范围（与上一次部署比较）
CHANGED=$(git diff --name-only ORIG_HEAD HEAD 2>/dev/null || echo "")
FRONTEND_CHANGED=$(echo "$CHANGED" | grep -q '^frontend/' && echo true || echo false)
BACKEND_CHANGED=$(echo "$CHANGED" | grep -q '^backend/' && echo true || echo false)
INFRA_CHANGED=$(echo "$CHANGED" | grep -q 'docker-compose.yml' && echo true || echo false)

# [3/7] 基础设施变更检测（Redis 镜像升级等）
if $INFRA_CHANGED; then
  echo "[3/7] docker-compose.yml 有变更，重建基础设施容器..."
  docker compose up -d --build redis mysql
  echo "  等待 Redis/MySQL 就绪..."
  sleep 5
else
  echo "[3/7] 基础设施无变更，跳过"
fi

# [4/7] 构建后端 JAR
if $BACKEND_CHANGED || [ ! -f "$BACKEND_DIR/target/"*.jar ]; then
  echo "[4/7] 后端有变更，正在通过 Docker 编译..."
  docker run --rm \
    -v "$PWD/backend/moodcopilot:/app" \
    -v "$HOME/.m2:/root/.m2" \
    -w /app \
    maven:3.9-eclipse-temurin-21 \
    mvn clean package -Dmaven.test.skip=true
else
  echo "[4/7] 后端无变更，跳过编译"
fi

# [5/7] 重建并启动容器
echo "[5/7] 重建容器..."
if $FRONTEND_CHANGED; then
  echo "  前端有变更，重建 frontend..."
  docker compose up -d --build --no-deps frontend
fi

echo "  重建 backend..."
docker compose up -d --build --no-deps backend

if ! $FRONTEND_CHANGED; then
  echo "  前端无变更，跳过"
fi

echo "[6/7] 输出服务状态"
docker compose ps

echo
echo "最近后端日志（最后 80 行）"
docker compose logs --tail=80 backend || true

echo
echo "最近前端日志（最后 40 行）"
docker compose logs --tail=40 frontend || true

echo
echo "部署完成。后端从旧→新切换期间约 15-30 秒不可用，用户重试即可。"
