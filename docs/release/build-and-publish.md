# 构建与平台发布

## 发布前

1. 确认 `git status` 只包含本次计划内容，随后提交到 clean tree。
2. 检查 `gradle.properties`、mods.toml、README 版本表和更新 JSON 一致。
3. 运行 `./gradlew clean test check build`；`check` 会验证版本元数据、许可证、图标、贴图和 JAR 不夹带上游类。
4. 启动无 JEI 专服、完整客户端和发布测试存档。
5. 核对 `build/libs/aeallpattern-1.20.1-forge-0.2.1.jar` 与 SHA-256。
6. 记录 commit SHA、Java、Forge、AE2、Mekanism 与 JEI 版本。

## GitHub Release

- 使用语义版本 tag，例如 `v0.1.0`。
- Release notes 写用户可见变化、兼容矩阵、升级注意和已知限制。
- 文件名包含 MC 与 loader。
- 如果同一 Release 放多个 MC 版本，分别标明依赖，不把一个 JAR 标为 universal。

## Modrinth / CurseForge

- AE2 标 required。
- Mekanism 和 JEI 按实际功能标 optional。
- 游戏版本和 loader 必须与 JAR 一致。
- 中文名放简介或本地化描述；项目主标题保持易搜索的英文名。
- 上传后从平台下载一次并核对 SHA-256，再用下载文件启动。

## 自动一致性

Mekanical Create 曾发生代码/JAR 已更新但 `docs/update.json` 仍旧的问题。本项目发布任务应加入版本一致性测试，避免 GitHub、更新 JSON、平台和 JAR 元数据漂移。
