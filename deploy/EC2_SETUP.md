# EC2 服务器创建与登录指南

2026-10-04 当前结果：[https://52.60.174.156](https://52.60.174.156) 已按 [0010 revision 3](../ai-docs/implement-plan/0010-production-delivery.md) 完成公网技术发布。现有根盘已加密，EIP/ECR/S3/CloudFront/IAM/SSM、独立生产库、自动发布/续期/每日备份已配置并验证；账号/业务/媒体与并发由用户自行测试。第 10 节保留的是最初服务器准备阶段的历史记录，实际后续状态见配对计划与[运维手册](PRODUCTION_RUNBOOK.md)。

本阶段准备服务器管理通道及 Docker/Compose 运行环境。依据 2026-10-04 用户要求：“域名先放一下，我想先搞一个AWS ec2服务器……使用量应该没那么大……你教我一步一步怎么搞”，以及随后要求 Codex 远程操作并提供实例 ID。域名、网站部署、正式数据初始化、媒体资源和 CI/CD 留在[计划 0010](../ai-docs/implement-plan/0010-production-delivery.md)后续阶段。

第 1–9 节保留手动操作方法；第 10 节记录 Codex 对用户已创建实例的实际远程检查、安装和验证结果。服务器准备通过不代表应用容量或生产上线已验证。

## 建议起步规格

| 项目 | 设置 |
|---|---|
| Region | Canada (Central)，`ca-central-1` |
| Name | `snowboard-v2` |
| Instance type | `t4g.small`，2 vCPU、2 GiB 内存、ARM64 |
| AMI | AWS 官方 Amazon Linux 2023，标准版本、64-bit Arm |
| 数量与购买方式 | 1 台，On-Demand |
| 根磁盘 | 20 GiB、gp3、加密；IOPS/吞吐量保持默认，使用默认 AWS 管理密钥 |
| 网络 | 默认 VPC 与默认公网子网，启用自动分配公网 IPv4 |
| 登录方式 | Session Manager；挂载下述 IAM Role |
| 安全组 | `snowboard-v2-sg`，本阶段入站为空，出站保留默认 |
| CPU credits | Standard；积分耗尽后限制突发性能，不产生 Unlimited 的额外 CPU credit 费用 |
| 监控 | 默认基本监控，暂不开详细监控 |
| 实例元数据 | Enabled，IMDSv2 Required；response hop limit 2，供后续容器使用实例角色 |

低访问量适合从此规格试运行，但访问人数不能代替内存测量。Java、MySQL、媒体校验和备份仍有基础与峰值开销。上线前验证实际容量；现有基础镜像声明支持 ARM64，不代表完整应用已在这台 EC2 验证。

按当前加拿大中部 Linux On-Demand 单价 0.0184 USD/小时、730 小时/月，实例约 13.43 USD/月；一个公网 IPv4 约 3.65 USD/月。合计约 24.33 CAD/月，按计划记录的参考汇率换算。**这还未包含 EBS、税、后续媒体、备份等，30 CAD 总预算仍须核算。** 不把试用或抵扣当成长期费用保障。

来源：[AWS 实例价格数据](https://b0.p.awsstatic.com/pricing/2.0/meteredUnitMaps/ec2/USD/current/ec2-ondemand-without-sec-sel/Canada%20(Central)/Linux/index.json)、[公网 IPv4 价格](https://aws.amazon.com/vpc/pricing/)、[成本计算与限制](../ai-docs/implement-plan/0010-production-delivery.md)。

## 1. 创建登录管理所用的 IAM Role

打开 AWS 控制台的 IAM：

1. `Roles` → `Create role`。
2. `Trusted entity type` 选择 `AWS service`。
3. `Use case` 选择 `EC2`，点 `Next`。
4. 搜索并勾选 `AmazonSSMManagedInstanceCore`。
5. Role name 填 `snowboard-v2-ec2-role`，创建。

这个角色给服务器提供 Systems Manager 的管理权限。后续 ECR/S3 权限按已 review 的部署方案单独补充。

来源：[AWS 创建 Systems Manager 实例角色](https://docs.aws.amazon.com/systems-manager/latest/userguide/setup-instance-permissions.html)。

## 2. 打开创建实例页面

1. 打开 EC2 控制台，右上角选择 `Canada (Central)` / `ca-central-1`。
2. 点击 `Launch instance`，名称填 `snowboard-v2`。
3. 实例数量为 1；本次使用默认 On-Demand，保持 Spot 选项关闭。

## 3. 选择系统与规格

1. `Application and OS Images` → `Amazon Linux`。
2. 选择 AWS 官方 `Amazon Linux 2023 AMI`，标准版本。
3. Architecture 选择 `64-bit (Arm)`；不是 x86。
4. `Instance type` 选择 `t4g.small`。
5. `Key pair` 选择 `Proceed without a key pair`。本指南用 Session Manager，不依赖 SSH key pair；后面的实例角色和网络必须正确设置。

AWS 提供的 Amazon Linux 2023 AMI 通常预装 SSM Agent；连接后实际检查其运行状态。

来源：[EC2 创建流程](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-launch-instance-wizard.html)、[SSM Agent 预装 AMI 与检查](https://docs.aws.amazon.com/systems-manager/latest/userguide/ami-preinstalled-agent.html)。

## 4. 设置网络和安全组

展开 `Network settings` / `Edit`：

1. VPC 选择该区域的默认 VPC。
2. Subnet 选择默认公网子网；默认 VPC 的默认子网具备通往 Internet Gateway 的路由。
3. `Auto-assign public IP` 选择 `Enable`。
4. 创建安全组 `snowboard-v2-sg`，删除/取消勾选默认 SSH 入站规则；本阶段 HTTP/HTTPS 也先不添加，入站规则保持为空。
5. 保留默认出站规则，让 SSM Agent 可以通过 HTTPS 访问 AWS 管理端点。

若没有默认 VPC/默认子网，先核对现有网络及 Internet Gateway 路由，不创建 NAT Gateway 或 VPC Endpoint 来套用本指南。Session Manager 的此连接方式不需要入站端口；以后部署入口时再添加 80/443，数据库与 Java 端口保持内部访问。

来源：[EC2 网络参数](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-launch-parameters.html)、[SSM Agent 的出站通信](https://docs.aws.amazon.com/systems-manager/latest/userguide/ssm-agent-technical-details.html)。

## 5. 设置磁盘与高级选项

`Configure storage`：20 GiB、gp3，开启加密，IOPS/吞吐量保持默认，使用默认 AWS 管理密钥。此时只建空服务器，数据持久化和备份在后续部署方案配置。

`Advanced details`：

- `IAM instance profile` 选择 `snowboard-v2-ec2-role`；若列表未更新，刷新该字段。
- `Credit specification` 选择 `Standard`，或取消 `Unlimited` 勾选。
- `Detailed CloudWatch monitoring` 保持关闭。
- `Metadata accessible` 启用，`Metadata version` 选 `V2 only / Required`，`Metadata response hop limit` 填 2。
- `User data` 留空，其余未涉及设置保持默认。

如果创建页面没有 CPU credit 设置，创建后在实例 `Actions` → `Instance settings` → `Change credit specification` 中关闭 Unlimited，确认 Details 显示 Standard。Standard 的取舍是 CPU credits 耗尽后性能回到基线，后续容量验证需覆盖此条件。

来源：[存储与高级参数](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-launch-parameters.html)、[CPU credit 设置](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances-how-to.html)、[IMDS 与容器网络跳数](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-options.html)。

## 6. 启动实例并确认状态

核对：`t4g.small`、ARM64 Amazon Linux 2023、20 GiB gp3、1 台、角色已选、Standard、入站为空。查看创建页的估算，并知道公网 IPv4/EBS 等不一定包含在仅实例报价中。

点击 `Launch instance` 会开始创建并计费。进入 `Instances`，等状态为 `Running`，所有适用的 `Status checks` 通过。

## 7. 通过浏览器登录服务器

选中实例 → `Connect` → `Session Manager` → `Connect`。

若按钮暂不可用，等几分钟刷新。若仍提示 SSM Agent offline，核对该实例的 IAM Role、公网 IPv4、子网到 Internet Gateway 的路由和 HTTPS 出站；若是 AccessDenied，核对当前 AWS 登录身份是否有启动会话的权限。不要为解决此问题直接开放所有端口。

来源：[AWS 在 EC2 控制台启动 Session Manager 会话](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-sessions-start.html)。

## 8. 检查创建结果

在服务器的浏览器终端依次运行：

```sh
uname -m
cat /etc/os-release
free -h
df -h /
sudo systemctl is-active amazon-ssm-agent
```

预期：架构 `aarch64`，系统 Amazon Linux 2023，内存总量约 2 GiB，根磁盘对应 20 GiB 卷，SSM Agent 返回 `active`。系统和文件系统占用会让可用数值小于标称值。

能登录且这些检查符合配置，服务器基础检查完成；Docker/Compose 准备见第 9 节。网站和正式数据库需另行部署，浏览器访问公网 IP 此时不会看到项目网页。

2026-10-04 用户回传实例终端输出：`uname -m` 为 `aarch64`，系统 Amazon Linux `2023.12.20260930`，内存 total 1.8 GiB / available 1.5 GiB，根文件系统 20G / available 19G。当时仅有用户侧证据；随后独立核对结果见第 10 节，业务容量与实际账单仍未验证。

## 9. 准备 Docker 与 Compose

以下命令在 EC2 终端执行，不在本地 Mac 执行。目标实例已由 Codex 通过 SSM 完成安装和验证，结果见第 10 节；该实例无需重复安装。

先从 Amazon Linux 自带仓库安装并启动 Docker：

```sh
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo docker version
sudo docker run --rm hello-world
```

预期 Docker version 同时有 Client/Server，测试容器输出 `Hello from Docker!` 后自动移除。不需要开放入站端口；后续在服务器使用 `sudo docker`。

再安装所有用户均可发现的 ARM64 Compose 插件。固定官方稳定发布 v5.6.0，2026-10-04 已核对发布资产和独立 checksum；不改变项目业务依赖。

```sh
compose_setup_dir="$(mktemp -d)"
curl -fL https://github.com/docker/compose/releases/download/v5.6.0/docker-compose-linux-aarch64 -o "$compose_setup_dir/docker-compose"
```

下载成功后校验并安装；校验失败时 `&&` 会阻止安装：

```sh
printf '%s  %s\n' '733ec76717ceb59052a9609b9dadfb523b2df8eab57a54212872d10a58078ea2' "$compose_setup_dir/docker-compose" | sha256sum -c - && sudo install -d /usr/local/lib/docker/cli-plugins && sudo install -m 0755 "$compose_setup_dir/docker-compose" /usr/local/lib/docker/cli-plugins/docker-compose
```

同一终端会话内执行上述步骤，避免丢失临时路径变量；最后检查：

```sh
sudo docker compose version
sudo docker ps
```

预期 Compose 显示 v5.6.0，Docker ps 成功输出列表；只有刚才的临时 hello-world 测试时，列表应为空。手动插件不自动更新，升级前核对官方兼容性与项目交付配置；正式 Compose 的实际校验/启动尚待上线计划批准和实施。

来源：[AWS 的 Amazon Linux 2023 Docker 安装方法](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-container-image.html)、[Amazon Linux 的 dnf 包管理](https://docs.aws.amazon.com/linux/al2023/ug/package-management.html)、[Docker Compose 手动插件安装](https://docs.docker.com/compose/install/linux/)、[官方 v5.6.0 发布](https://github.com/docker/compose/releases/tag/v5.6.0)、[ARM64 checksum](https://github.com/docker/compose/releases/download/v5.6.0/docker-compose-linux-aarch64.sha256)。

## 10. Codex 远程准备的实际结果

2026-10-04 用户要求：“你不能远程连接我的服务器然后帮我做吗？”，提供实例 `i-0c7978984740cbd58` 并完成 AWS 浏览器认证。本次授权范围为已有服务器的检查和 Docker/Compose 准备；整体计划 0010 仍为 DRAFT。

本机通过官方安装脚本安装 AWS CLI 2.37.9 到用户目录，使用命名 profile `snowboard-v2`，执行 `aws login --profile snowboard-v2 --region ca-central-1` 完成临时凭证登录。随后通过 [SSM Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html) 的 `AWS-RunShellScript` 操作指定实例，无需开放 SSH；此方式无需 Session Manager 本地插件。临时登录到期后按 [AWS CLI 浏览器登录文档](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sign-in.html)重新认证，不在聊天或仓库保存密码、令牌、长期 Access Key。

| 核对项 | 实际结果 |
|---|---|
| 实例与系统 | `ca-central-1`，`t4g.small`，running，ARM64 / Amazon Linux 2023.12.20260930 |
| 管理通道 | SSM Online，Agent active；已挂载 `snowboard-v2-ec2-role` 实例 profile |
| 网络与元数据 | 安全组 `snowboard-v2-sg` 入站为空、IPv4 出站允许；IMDSv2 Required，hop limit 2 |
| CPU credits | Standard |
| 根盘 | 20 GiB gp3，3000 IOPS / 125 MiB/s，**Encrypted false**；未达到指南的加密建议，见 [TODO-0024](../ai-docs/todo/0024-ec2-root-volume-unencrypted.md) |
| Docker | Amazon Linux 仓库包 `25.0.16-1.amzn2023.0.4`；Engine 25.0.16 / Client 25.0.14，linux/arm64；服务 enabled / active |
| Compose | v5.6.0，ARM64 插件的 SHA-256 校验通过 |
| 容器运行 | `docker run --rm hello-world` 成功，输出 `Hello from Docker!`；临时容器自动移除 |
| Compose 实际运行 | 隔离项目 `snowboard-setup-check` 的配置检查和 `run --rm probe` 成功；`down --remove-orphans` 清理临时网络 |
| 安装后余量 | 内存 available 1.3 GiB；根文件系统 available 18G；未测试业务负载 |

SSM 检查命令 `33125046-dc19-4f7c-a633-7b49a7bb9876`、安装命令 `ac22b0f0-3432-4f1e-90d0-8e91305ce051`、Compose 验证命令 `ed0bb276-c6c4-4d19-9d33-d59eaf9692d7` 均为 Success / ResponseCode 0。可通过 `aws ssm get-command-invocation --command-id <上述 ID> --instance-id i-0c7978984740cbd58 --profile snowboard-v2 --region ca-central-1` 查看保留期内的执行记录。

最初服务器准备阶段尚未创建 ECR/S3/CloudFront、部署项目、初始化正式数据库或开放网站入口；后续 revision 3 已执行，见本页开头及配对计划。现有未加密卷不能直接原地改成加密卷，涉及新卷/快照的处理需先形成具体存储方案和费用再按授权执行；本次未改动存储。[AWS EBS 加密说明](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-encryption.html)

2026-10-04 找回会话后再次只读复核：实例仍 running，SSM Online，安全组入站为空，根盘仍是 20 GiB gp3 / Encrypted false；实例角色仅有 AmazonSSMManagedInstanceCore，无 inline policy，尚未配置 ECR/S3 业务权限。SSM 检查命令 `887d94a7-0e17-4dab-8fac-2c34fd2e1a39` 为 Success / ResponseCode 0，确认 Docker Engine 25.0.16、Compose v5.6.0、Docker active/enabled、无运行容器、可用内存 1403 MiB、根盘可用约 18G。没有重装、重启服务或修改资源，也没有进行业务容量验收。

用户已选择 main 测试通过后自动部署，以及数据库每日备份、保留 7 天。对应 [0010 revision 2](../ai-docs/implement-plan/0010-production-delivery.md) P-02 至 P-07 已获用户“开始实现”的批准并完成本地交付与隔离验证，处于 IMPLEMENTED；生产配置、自动发布、备份和后续步骤见 [运维手册](PRODUCTION_RUNBOOK.md)。真实域名、正式存储方案、完整成本和首次云端配置仍在 P-08 上线前定稿。域名仍按用户要求后置。

## 费用观察与后续

- 在 Billing and Cost Management → Budgets 设置月度 Cost budget；若以 USD 显示，可用 20 USD 作为接近 30 CAD 的提醒参考，配置邮件通知。提醒不自动停止资源，汇率与税会影响最终 CAD 金额。[创建预算](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-create.html)
- 暂时不用时可以 `Stop instance`；EBS 继续收费，保留的 Elastic IP 等资源也可能继续收费。当前使用自动分配公网 IPv4，停机后地址通常会被释放，下一次启动可能改变。
- Docker/Compose 已按第 10 节完成远程准备；正式存储加密要求见 TODO-0024。生产配置和 CI/CD 的本地实现已完成；创建 ECR/S3、正式存储处理、初始化正式数据与发布仍按计划 0010 P-08 具体方案/费用及操作授权执行。本阶段不分配 Elastic IP，固定地址在域名/正式部署时再决定。
