#!/bin/bash
# MoodCopilot 生产环境启动脚本
# 用法: ./start-prod.sh

set -e

cd "$(dirname "$0")"

# 从 .env 加载环境变量（如果存在）
if [ -f .env ]; then
    set -a
    source .env
    set +a
fi

# 关键变量兜底检查
required_vars="JWT_SECRET TURNSTILE_SECRET_KEY TURNSTILE_SITE_KEY"
missing=""
for var in $required_vars; do
    if [ -z "${!var}" ]; then
        missing="$missing $var"
    fi
done

if [ -n "$missing" ]; then
    echo "❌ 缺少必需的环境变量:$missing"
    echo "   请在 .env 文件中配置后重试"
    exit 1
fi

echo "✅ 环境变量检查通过"
echo "🚀 启动 MoodCopilot..."

java -jar backend/moodcopilot/target/moodcopilot-backend-*.jar
