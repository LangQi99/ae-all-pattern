# Git、工作树与多版本移植

## 默认分支

- `main`：Minecraft 1.21.1 + NeoForge，默认开发目标。
- 未经明确要求，不顺带创建或修改其他 MC 版本。

## 提交原则

- 功能、版本号、发布元数据尽量分开提交。
- 不覆盖玩家未提交的改动。
- 提交前看 `git status`、`git diff --check` 和 `git diff --cached --check`。
- 运行目录、存档、日志、下载 JAR 和个人代理文件不提交。
- 每个提交必须能说明做了什么和为什么。

## 多版本工作树

需要 1.20.1 时，用独立 branch + worktree：

```bash
git fetch origin
git worktree add -b mc/1.20.1 ../ae-all-pattern-1.20.1 origin/main
```

`origin/main` 是示例中的明确起点；如果已经批准了其他移植基线，应改成对应 commit SHA。远端维护分支已经存在时，从 `origin/mc/1.20.1` 挂载，不能省略起点并依赖当前 `HEAD`。

移植顺序：

1. 先在 main 完成并通过测试。
2. 提交并推送 main。
3. 在独立工作树逐项移植纯逻辑、API、资源和测试。
4. 适配 Java、Forge/NeoForge、NBT、Recipe API 与资源目录差异。
5. 分别构建、启动和测试。

不要整分支硬合并后批量解决冲突；这种做法容易把客户端 API、NBT 格式和依赖版本混在一起。纯 Java policy/test 可以复用，MC 集成层必须按版本改写。

完整的主辅关系、版本差异、同步步骤、反向热修复和发布门禁见 [Minecraft 1.21.1 与 1.20.1 并行开发规范](dual-version-development.md)。当前分支已经实现 1.20.1，但在平台发布完成前仍属于待发布版本；实际状态以支持矩阵为准。

## 发布文件名

一个 GitHub Release 可以包含多个版本，但文件名必须显式包含版本和加载器，例如：

```text
aeallpattern-1.21.1-neoforge-0.1.0.jar
aeallpattern-1.20.1-forge-0.1.0.jar
```
