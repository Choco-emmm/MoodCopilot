# GitHub Actions：检查与部署

## 日常使用

仓库的 **Actions → CI** 显示每次检查和发布的步骤与日志。

- 任意目标分支的 PR：自动检查 Web 构建、小程序类型/单测/微信构建、后端测试与打包、部署脚本回归。
- 推送 `develop` / `master` / `codex/**`：自动检查。文档改动也运行，避免必需检查一直处于等待状态。
- **Actions → CI → Run workflow**：选择部署分支并勾选“检查通过后发布镜像并部署到服务器”。检查全部通过后，发布两份以完整提交 SHA 命名的 GHCR 镜像，再通过 SSH 更新现有服务。
- 默认不自动上线。仓库变量 `AUTO_DEPLOY=true` 可开启部署分支 push 后自动上线；`DEPLOY_BRANCH` 默认 `develop`（目前包含较新的应用代码），可在正式发布流程改为 `master` 后调整。

普通 PR 不执行镜像推送、SSH 或 production 环境任务。CI 不需要生产数据库、AI Key 或生产 SSH 密钥；真实 AI 推理测试沿用项目的显式 opt-in 开关，CI 固定关闭。小程序构建包可在运行页 Artifacts 下载，仍需微信开发者工具/真机验收，不会自动发布到微信。

本项目采用 `develop` 为默认主线和部署分支，功能修改通过短期分支提交 PR；`master` 保留现有历史。GitHub 的手动 Run workflow 入口要求工作流文件已经进入默认分支，因此需要先合并 CI/CD 配置，再在 Run workflow 中选择 `develop` 发布。功能分支 push 可先验证检查任务，但不允许部署。

## 一次性配置

在 **Settings → Environments** 创建 `production`。若账户/仓库支持，可添加 Required reviewers，让生产部署等待确认。

在 production 环境的 Secrets 配置：

| Secret | 含义 |
| --- | --- |
| `DEPLOY_HOST` | 云服务器 IP 或域名 |
| `DEPLOY_USER` | SSH 用户；优先使用有 Docker 权限的专用部署用户 |
| `DEPLOY_SSH_KEY` | 与服务器公钥对应的完整 SSH 私钥，仅保存到 Secrets |
| `DEPLOY_KNOWN_HOSTS` | 已核验服务器指纹的 known_hosts 行；非 22 端口使用 `[host]:port` 格式 |

在 production 环境的 Variables 配置：

| Variable | 含义 |
| --- | --- |
| `DEPLOY_PATH` | 服务器已有项目的绝对路径，如 `/root/MoodCopilot`，以实际路径为准 |
| `DEPLOY_PORT` | SSH 端口，可省略，默认 `22` |

`DEPLOY_BRANCH` / `AUTO_DEPLOY` 必须是 **仓库级 Variables**，因为发布镜像的 job 在 production 环境外运行。

不要把 `.env` 或私钥提交到仓库。服务器继续使用原有 `.env`、证书和数据卷。SSH 强制核验 known_hosts，不接受关闭主机校验的捷径。部署账号需要 Bash、`flock`、Docker Engine 和支持 `up --wait` 的 Compose v2，以及拉取 GHCR 的网络条件。

GHCR 首次发布的 package 可能默认私有：可在两个 package 的设置中调整可见性，或在服务器用有 `read:packages` 权限的账号事先执行 `docker login ghcr.io`。Actions 推送镜像使用其临时 `GITHUB_TOKEN`，不需要自建推送 PAT。镜像是当前 GitHub Runner 的 amd64 架构，服务器需为 amd64。

先在服务器用原来的 `./deploy.sh` 完成已有安装。CD 要求现有 `backend` 和 `web` 服务正在运行，拒绝隐式初始化数据库。部署使用服务器当前 Compose 文件；基础设施、反代、数据库迁移和配置变更仍需单独审核同步。此工作流更新应用镜像，不会自动把 Git 工作目录切换到另一个分支。

## CI 失败禁止合并

在 **Settings → Rules → Rulesets** 为目标分支创建规则：

1. Require a pull request before merging。
2. Require status checks to pass，选择 **CI Ready**。此检查汇总全部四组检查，任何失败、取消或跳过均不通过。
3. 按需要启用 Require branches to be up to date / 审核要求。

先运行一次 CI，使 GitHub 能列出检查名称。仓库内的 YAML 负责检查，Ruleset 才负责拦截合并；仅提交 YAML 不会自动创建分支保护。外部 Fork 的首次运行可能需要维护者批准。

## 部署与失败恢复

部署脚本先捕获现有两个容器的真实镜像 ID，再拉取新镜像。两份镜像拉取成功后才替换应用容器；MySQL、Redis、RabbitMQ、Tunnel、数据卷都不重建。随后等待后端健康检查，验证 Web 静态内容和 `/api/health` 反代，再重新加载公共 Nginx 的上游地址。

启动或探测失败时，用捕获的本地镜像 ID 恢复两个应用服务并再做检查；即便恢复成功，Actions 仍显示部署失败。恢复失败会明确输出 `ROLLBACK FAILED`。部署日志不打印生产应用正文或 `.env`。

`.deploy/` 保存最近成功的提交和前后镜像配置；不要在发布期间清理 Docker 镜像。自动回退只恢复应用镜像，不能逆转数据库迁移。涉及不兼容 Schema 的版本必须另行安排备份和迁移；这不是零停机发布，切换期间存在短暂中断。

服务器保留原来的源码构建入口 `./deploy.sh`。镜像部署也可手动运行：

```bash
./deploy.sh --images choco-emmm <完整的40位提交SHA>
```

手动恢复上次镜像：

```bash
docker compose -f docker-compose.yml -f .deploy/previous-images.yml \
  up -d --no-deps --no-build --pull never --force-recreate --wait backend web
docker compose exec -T web wget -q -O /dev/null http://127.0.0.1:3000/api/health
docker compose exec -T frontend nginx -s reload
```

确认健康后再继续操作。不要同时运行源码部署与 Actions 部署。

## 本地验证

```bash
cd frontend && npm ci && npm run build
cd ../frontend-uniapp && npm ci && npm run type-check && npm test && npm run build:mp-weixin
cd ../backend/moodcopilot && mvn --batch-mode --no-transfer-progress clean verify
cd ../.. && bash scripts/tests/deploy-images.test.sh
git diff --check
```

前端依赖以各自的 package-lock.json 固定，CI 使用 Node 22、后端使用 Java 21，并实际构建两端 Docker 镜像。测试报告和小程序构建产物仅保留 3 天；大体积 JAR 只在准备发布时上传，保留 1 天。容器镜像在 GHCR 单独管理。
