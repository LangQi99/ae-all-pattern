# 启动、调试与常用任务

## 快速任务

```bash
./gradlew test                 # 纯单元测试，不启动游戏
./gradlew runClient            # 开发客户端
./gradlew runServer            # 无界面专用服务器
./gradlew runGameTestServer    # GameTest 服务器
./gradlew runData              # 数据生成
./gradlew clean check build    # 发布前完整验证
```

可选依赖矩阵：

```bash
./gradlew -Pruntime_jei=false -Pruntime_mekanism=false runServer
./gradlew -Pruntime_jei=true  -Pruntime_mekanism=false runClient
./gradlew -Pruntime_jei=true  -Pruntime_mekanism=true  runClient
```

首次运行较慢是依赖解析、资产下载与 Forge 工作区准备；后续应命中 Gradle 缓存。

## 启动顺序

1. 先跑 `test`，快速发现纯逻辑错误。
2. 跑 `compileJava`，确认依赖 API。
3. 跑 `runServer`，确保没有客户端/JEI 类加载泄漏。
4. 跑 `runClient` 做交互与渲染。
5. 有 GameTest 后跑 `runGameTestServer`。
6. 最后 `clean check build`，排除增量缓存掩盖的问题。

## 调试建议

- 在绑定服务、目录代数切换、`requestUpdate`、`pushPattern` 接受/拒绝处打结构化日志。
- 不在每 tick 打逐配方日志；使用计数器和采样。
- 崩溃先看 `run/crash-reports`，再看 `run/logs/latest.log`，同时记录完整模组列表。
- 类加载崩溃通常发生在功能执行前；检查公共类签名是否引用了可选/客户端类。
- 网络与存档问题要测试重启，不以一次运行成功作为完成。

## 停止

测试存档写入或外部脚本操作前，必须正常关闭客户端/服务器并确认没有进程持有 `session.lock`。不要在运行中直接修改 region、level.dat 或 playerdata。
