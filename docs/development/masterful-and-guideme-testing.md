# Masterful Machinery 与 GuideME 双版本回归

## 修复边界

- 使用 MM 的 `StructureManager.getStructuresForController` 查找结构归属，不把 JEI 蓝图图标当作工作站，也不只依赖 JEI 注册的第一个控制器。
- 合并控制器对应的所有 MM 处理配方分类。每条配方再按完整的结构 ID 过滤，避免混入其他机器或同路径、不同命名空间的结构。
- 共用一次扫描的去重集合、数量限制、分帧预算和最终上传，不为每个分类单独生成覆盖结果。
- FE 输入属于外部机器供能，不能编码成 AE 物品材料。仅跳过 MM 已知的 EnergyStack 输入槽；其他无法转换的材料不擅自忽略。
- 这是**生成器/聚合样板**兼容，不是新增 MM 的 Linker 投料适配器。
- EMI/TMRV 入口回退到结构感知的 JEI 桥；本轮真实客户端端到端验证使用 JEI，不能当作 EMI 实机覆盖。

## 固定测试版本

| Minecraft | 加载器 | AE2 | GuideME | Masterful Machinery |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.20 | 15.4.10 | 20.1.7 | Fork 3-1.20.1-0.1.34.7，CF 8803045 |
| 1.21.1 | NeoForge 21.1.219 | 19.2.17 | 21.1.1 | Upgraded 1.21.1-0.6.2，CF 8852200 |

来源：[Forge Fork](https://www.curseforge.com/minecraft/mc-mods/masterful-machinery-fork/files/8803045)、[NeoForge Upgraded](https://www.curseforge.com/minecraft/mc-mods/masterful-machinery-upgraded/files/8852200)。
没有把第三方代码或 JAR 加入仓库。

## 自建最小复现

`tools/testworld/masterful` 是原生 MM JSON 配置与数据包，不依赖反馈者的整合包：

- 三个控制器：primary、secondary、unrelated。
- alpha、beta 两个结构均允许 primary 和 secondary。
- 第三个结构仅允许 unrelated，用于确认不串配方。
- 结构由输入口、控制器、输出口三个方块组成，真实 GameTest 会放置并验证成型。
- alpha：2 铁锭 + 1000 FE → 1 金锭；beta：2 铜锭 → 1 钻石。
- unrelated：2 煤 → 1 绿宝石，不能进入前两个控制器的目录。
- 专服测试验证归属、真实配方加载、排除无关配方、结构成型。
- 客户端测试验证旧 JEI 查找只有 primary 拥有两个 MM 处理分类，secondary 为零；新编码路径两者均得到两个正确配方，物品数量保持 2 → 1。

测试不会给正式存档增加机器或配方；测试数据只复制进指定测试运行目录，不加入发布资源。

在**对应版本工作树**执行：

```sh
./gradlew runGameTestServer \
  -Pruntime_masterful=true -Pmasterful_fixture=true \
  -Pgametest_directory=run-gametest-mm \
  -Pexpected_test_mods=mm,jei,guideme \
  -Pruntime_jei=true -Pruntime_emi=false -Pruntime_mekanism=false \
  -Pruntime_mystical_agriculture=false -Pruntime_create=false \
  -Pruntime_industrial_foregoing=false -Pruntime_neoecoae=false \
  -Pruntime_ae_provider_addons=false -Pruntime_extendedae_plus=false \
  -Pruntime_packaged_addons=false -Pruntime_mekanism_addons=false
```

现有 CI 的 No-GUI GameTest 矩阵已增加 `Masterful-Machinery` 档。
正常构建默认不启用 `runtime_masterful` / `masterful_fixture`。

## GuideME

新增 `aeallpattern:guide` 物品（书 + 赛特斯石英水晶，无序合成）。
使用 GuideME 的 common API 打开指南，专服不会调用客户端屏幕类。

两版本包含相同的五页英文与五页简体中文：

1. 功能概览与选择流程。
2. 生成器、聚合样板及输入/输出管理。
3. Linker/Binder 与机器适配器的能力边界。
4. 天枢路由器、普通 CPU、路线偏好与高级资格。
5. 多方块与日志排查。

页面有目录、互相链接及全部六个相关物品的快捷键索引。
翻译目录必须是 GuideME 的 `_zh_cn/`，而不是普通 `zh_cn/`；后者会错误地把中英文当作十个独立页面。
客户端自动断言最终只有五个页面、语言正确、六个物品入口均存在，并逐页实际打开、渲染、截图。
单元测试使用每个版本安装的 GuideME 解析器验证 Markdown、导航、链接和物品锚点。

## 客户端复现方式

`tools/compatibility_client_smoke.py --help` 列出参数。
需要 Python 的 `minecraft-launcher-lib==8.0`、对应 Java 与官方加载器安装目录。
在 Linux 使用 `xvfb-run -a`，无需桌面窗口，也不打开公网游戏端口。
每次必须指定新的 `--directory`，世界从已完成的本版本 GameTest 存档复制，禁止跨版本复用存档。

依赖按公开发行文件与哈希锁定，不能拿开发 Maven JAR 代替正式 Forge 模组。
脚本对缺失完成标记、非零退出和截图不完整返回失败；启动错误屏幕不算通过。

## 结果与未覆盖范围

- 双版本单元测试各 251 项通过。
- 双版本带真实 MM 的专服 GameTest 各 57 项通过，包含指南合成配方加载验证。
- 1.20.1 正式加载环境的中英文 JEI 编码与指南渲染通过。
- 1.21.1 正式加载环境的中英文 JEI 编码与指南渲染通过；双版本均逐页截图，确认五页导航无重复、语言正确。
- 双版本不安装 MM/JEI 的专服 GameTest 也各 57 项通过。单元测试中的 JEI 专项仍须使用启用 JEI 的测试配置，不能将无 JEI 专服配置直接用于整套单元测试。
- 合并 JEI 分类（未按结构拆分类）的过滤规则有单元测试，但本轮真实 MM 使用其默认拆分类配置。
- 没有声称测试整个 Project Infinity 或 ATM10，也没有声称覆盖所有自定义材料、随机数量、每 tick 物品消耗或自定义投料逻辑。
- 之前 ATM10 初始化崩溃仍需原失败启动的 latest.log；本次结果不能代替该报告的根因分析。
