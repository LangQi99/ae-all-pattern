# ECO / 万象样板总成 / 重进存档回归（2026-09-17）

## 本次定位与修复

- Neo ECO 21.2.0-beta3 将样板逻辑从 BlockEntity 拆到 ECOCraftingPatternBusCatalog。旧混入 shadow 的 patternDetails 字段不存在，会在启动时崩溃。现在按目标类是否存在选择新旧兼容层，不靠模糊版本字符串判断。
- 新旧 ECO 都会把聚合标记当作一条合成样板；追加展开结果前必须移除标记，否则首条配方重复、空选择也可能泄漏占位配方。
- ECO 延迟刷新时 AE 节点可能已销毁；新兼容层等待有效节点，避免上游 getGrid() 空指针。新增销毁节点后执行延迟刷新的回归。
- 无用之物 ME 样板总成的私有槽位过滤器原本只接受它自己的万象样板与 AE 合成样板，拒绝全样板。现在接受带有效聚合数据的全样板物品，仍由原有炉子逻辑验证每条子配方。不会把任意处理配方冒充万象配方，也不会绕过结构、线圈或模具约束。

## 验证范围

公开下载源核对结果：
- [Neo ECO 21.2.0-beta3](https://modrinth.com/mod/neoecoae/version/1vKpC42G)，发布于本次检查当天。GitHub、CurseForge、Modrinth 尚未找到此前提到的 beta5，不能宣称验证了 beta5。
- [无用之物 1.21.1-2.3.7.2](https://www.curseforge.com/minecraft/mc-mods/some-useless-things/files/8892864)。
- NeoForge 测试环境提升到 21.1.233，以满足 ECO 对 MixinExtras >= 0.5.3 的要求。21.1.219 自带的 0.5.0 会先被 ECO 拒绝。
- 1.20.1 回归使用仓库已固定的 ECO 20.3.0。20.4.2 的上游 Forge Mixin 重载问题不在本次新版本 NeoForge 兼容修复中。
- 无用之物公开 Forge 26.7.13 包没有 ME 样板总成与新的万象多方块。兼容代码同步了 Forge 分支，但对应实机用例在目标方块不存在时跳过；不能称为该 Forge 多方块已实测。

测试在隔离 GameTest 存档、独立 JVM、nogui 环境进行，不使用玩家存档：
1. 真正的 ECO 库存插入聚合样板；两条子配方各发布一次；连续刷新不重复；取出后清空。
2. ECO 待执行刷新遇到已销毁节点不崩溃、不丢库存。
3. 真正的 ME 样板总成库存接收全样板、拒绝泥土。
4. 搭建完整万象多方块，经正常成型后插入全样板、在 AE 网络查到两条子配方、实际投料、核对回收产物数量。
5. seed 进程保存后退出，verify 在新进程打开同一目录，不拿出/放回样板，也不主动刷新供应器。
6. 原版供应器和 ECO 冷加载位于启动区以外，使用非持久的临时区块票，专用 provider_compat 批次开启实际异步展开；万象多方块位于正常启动区。verify 检查供应器列表与 AE 网络的可合成状态，万象还再次实际投料并核对产物。
7. 双版本 check/build 与原有测试一起回归。无模组场景保持可选兼容，不引入硬依赖。

“下线重进必须拿出再放回”的反馈在上述存档中未复现。新增的是防回归与更严格复现环境，不能把此次修复等同于确认解决所有整合包的该现象。

## 自动化与手动复跑

最终本地结果：

| 配置 | seed / verify（各独立 JVM） | 构建与单测 |
| --- | --- | --- |
| 1.21.1 + ECO beta3 + 无用之物 2.3.7.2 | 各 73 项 GameTest 全通过；含实际万象合成与回收 | check/build 通过，263 项单测 |
| 1.21.1 + ECO 21.1.1 | 各 73 项通过；不存在的新 Catalog、万象用例按条件跳过 | 同上 |
| 1.20.1 + ECO 20.3.0 | 各 73 项通过；不存在的新 Catalog、万象用例按条件跳过 | check/build 通过，257 项单测 |

GameTest 的条件跳过使用 succeed 返回，因此总数不是每个附属模组的实际覆盖数。实际覆盖以本节的依赖配置及 ECO / VANILLA_ASYNC / ALLOY_RESTART 日志标记为准。

.github/workflows/provider-compat.yml 对每个兼容配置运行 seed 和 verify 两个新游戏进程，并检查实际 GameTest 成功标记。仅凭 Gradle 返回零不能算通过：开发启动器在某些加载崩溃时也会返回零。

1.21.1 最新组合：
```sh
./gradlew runGameTestServer -Pgametest_directory=run-gametest-providers \
  -Ppersistence_phase=seed -Pruntime_neoecoae=true -Pruntime_useless=true \
  -Pexpected_test_mods=neoecoae,useless_mod
./gradlew runGameTestServer -Pgametest_directory=run-gametest-providers \
  -Ppersistence_phase=verify -Pruntime_neoecoae=true -Pruntime_useless=true \
  -Pexpected_test_mods=neoecoae,useless_mod
```

旧 ECO 21.1.1 回归追加 -Pneoecoae_runtime_version=gWMhHBje，使用另一个隔离目录。
Forge 分支使用 -Pruntime_neoecoae=true 与 -Pexpected_test_mods=neoecoae，不设置 runtime_useless。

上述修复纳入 0.2.6 / 0.2.6-beta.1 发布；平台发布状态以对应 Release 页面为准。
