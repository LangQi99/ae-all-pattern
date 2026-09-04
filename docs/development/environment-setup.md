# 开发环境

## 要求

- macOS、Linux 或 Windows。
- JDK 17；Minecraft 1.20.1 Forge 使用 Java 17。
- Git 2.40+。
- 预留至少 6 GB 磁盘用于 Gradle 与 Minecraft 依赖缓存。
- IDE 可选：IntelliJ IDEA，导入 Gradle 项目而不是手动新建 Java 项目。

## 首次设置

```bash
git clone https://github.com/LangQi99/ae-all-pattern.git
cd ae-all-pattern
./gradlew --version
./gradlew test
./gradlew build
```

Gradle Toolchain 会选择/下载合适 JDK。IDEA 中开启 Gradle source/javadoc 下载；源码用于阅读 API，不复制到仓库。

## 版本集中管理

所有 Minecraft、Forge、AE2、JEI、Mekanism 与模组版本都放在 `gradle.properties`。元数据模板由构建时展开，禁止同时在 Java、TOML、README 多处手工维护不同版本。发布前用测试检查这些事实来源一致。

## 本地文件

- `run/`：客户端/服务端运行目录、存档、配置、日志，始终 gitignore。
- `run-data/`：可选数据生成工作目录，始终 gitignore。
- `AGENTS.md`：个人代理约定，默认 gitignore；公开协作原则写入 `docs/`。
- `local.properties`、`secrets.properties`：本地路径与密钥，绝不提交。

## 内存与日志

默认 Gradle 最大堆为 3 GB。大型整合运行可用环境或命令行临时提高，不直接把个人机器配置提交。开发日志默认 INFO；只有定位注册和网络生命周期时短期启用 DEBUG，避免日志本身掩盖性能问题。
