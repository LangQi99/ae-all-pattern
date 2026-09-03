# AE All Pattern | AE 全样板

[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62b47a?style=flat-square)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.219%2B-e96d4f?style=flat-square)](https://neoforged.net/)
[![AE2](https://img.shields.io/badge/Applied%20Energistics%202-19.2.17-7b62a3?style=flat-square)](https://github.com/AppliedEnergistics/Applied-Energistics-2)
[![Release](https://img.shields.io/github/v/release/LangQi99/ae-all-pattern?style=flat-square)](https://github.com/LangQi99/ae-all-pattern/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

**Put an entire machine's recipe catalog into one AE pattern, then let AE choose the right recipe for each order.**

**把一台机器的整套配方装进一张 AE 样板，再让 AE 为每次订单选择合适的路线。**

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ae-all-pattern-ae) · [Releases](https://github.com/LangQi99/ae-all-pattern/releases) · [Changelog](CHANGELOG.md) · [Issues](https://github.com/LangQi99/ae-all-pattern/issues) · [中文说明](#中文说明)

## English

AE All Pattern removes the repetitive work of encoding and maintaining hundreds—or thousands—of AE2 patterns. It provides three complementary tools:

- **All Pattern Generator + Aggregate Pattern:** capture the recipes associated with a machine in JEI or EMI/TMRV and store them as one manageable pattern item.
- **All Pattern Linker + Binder:** bind machines directly to an AE network and publish their recipes as live virtual processing patterns.
- **Tianshu Pattern Router:** resolve recipe conflicts dynamically when one output has several possible crafting paths.

### Quick start: Aggregate Patterns

1. Hold an **All Pattern Generator** and sneak-right-click a machine.
2. The generated **Aggregate Pattern** contains every encodable recipe discovered for that machine. Very large catalogs are transferred and stored in bounded pages instead of one oversized packet or item tag. Existing catalogs are rescanned once per server startup by the first available JEI or EMI/TMRV client, and their server-library UUID is updated in place.
3. Insert it into an AE2 Pattern Provider or a supported provider add-on. AE sees the selected child recipes as normal crafting or processing patterns.
4. Hold the Aggregate Pattern and right-click to search its contents, enable or disable individual recipes, and change its encoding rules.

Crafting-table recipes remain crafting patterns and work with Molecular Assemblers. Machine recipes remain processing patterns. Ingredient tags and candidate inputs are preserved so recipes such as chests can use any valid plank instead of being locked to the first JEI example.

### Quick start: Live machine binding

1. Place an **All Pattern Linker** on the AE network.
2. Hold an **All Pattern Binder** and sneak-right-click the Linker to select it.
3. Keep sneak-right-clicking machines to bind them. A purple outline marks a successful connection.
4. The Linker publishes the machines' deterministic recipes as virtual processing patterns and safely buffers submitted inputs before transfer.

The Linker is a real AE node: it uses one channel and 2 AE/t. The Binder and Generator are reusable tools and are not consumed when used.

### Aggregate Pattern controls

Each Aggregate Pattern keeps its own settings. The management screen also lets you search the complete catalog by input or output and publish only the recipes you want.

Recipe checkboxes are stored on the physical item as recipe IDs only. The item automatically keeps whichever list is shorter—enabled recipes or disabled recipes. After a catalog refresh, removed IDs are discarded; newly added recipes follow the previous majority default and the surviving choices keep their meaning.

| Setting | Effect | Default |
| --- | --- | :---: |
| Skip chance-based main outputs | Does not encode a recipe whose main result is probabilistic | On |
| Skip chance-based byproducts | Keeps probabilistic secondary results out of the pattern outputs | On |
| Skip durability-consuming recipes | Excludes recipes that damage an input tool; returned containers and unchanged catalysts remain valid | On |
| Split identical items | Expands an amount of `n` into `n` independent input slots of one item each | Off |
| Ignore output NBT/components | Matches the base output item during AE planning | Off |
| Remove processing catalysts | Removes inputs identified as reusable processing catalysts | Off |
| Item/fluid substitution | Uses AE2's native ingredient and contained-fluid substitution rules | Item: Off · Fluid: On |
| Remove fluids or chemicals | Independently removes fluid/chemical inputs or outputs from processing patterns | Off |
| Swap first/last inputs | Reverses the first and last processing inputs for order-sensitive subnet setups | Off |

### Dynamic routing

The **Tianshu Pattern Router is not a crafting CPU**. It adds an order-planning layer while the actual job still runs on the player's normal AE crafting CPU.

When a Router is online on the same ME network, the crafting confirmation screen exposes per-order preferences. Feasibility is always evaluated first, so an unavailable route cannot win merely because it scores well elsewhere. The remaining criteria can be dragged into any order and reversed or disabled:

- shorter or longer dependency path;
- more or less whole-chain material surplus;
- higher or lower output per operation;
- fewer waits / more immediately available machines.

The default Aggregate Pattern priority is `-1`, so a player's explicitly encoded patterns at priority `0` remain preferred. Router defaults are stored on the block, while a single order can temporarily override and recalculate them in the confirmation screen. Secondary outputs cannot trigger an expensive recipe by themselves unless **Independent byproduct orders** is explicitly enabled.

The routing engine is bundled inside this mod. Thunderbolt and AE2 Lightning Tech are not dependencies. If either is installed, its ordinary CPUs continue to use that installed mod's own behavior; AE All Pattern routing applies only while an online Tianshu Router is present.

### Compatibility

| Component | Support |
| --- | --- |
| AE All Pattern | 0.2.1 |
| Minecraft | 1.21.1 |
| Mod loader | NeoForge 21.1.219+ |
| Java | 21 |
| Applied Energistics 2 | 19.2.17 (required) |
| Recipe viewers | JEI 19.x, or EMI + TooManyRecipeViewers |
| Generic AE keys | Items and fluids; Mekanism chemicals through Applied Mekanistics + AE2 JEI Integration |
| Machine ecosystems | Vanilla, Mekanism and tested factory add-ons, Create, Mystical Agriculture, Industrial Foregoing |
| Crafting ecosystems | Vanilla crafting/stonecutting/smithing, Extended Crafting, PackagedAuto/PackagedExCrafting |
| Pattern-provider add-ons | ExtendedAE, ExtendedAE Plus, AdvancedAE, Neo ECO AE Extension, AE2 Crystal Science, AE2 Lightning Tech, AE2LT Packaged Provider |

Optional integrations are isolated: their absence does not prevent the game or a dedicated server from starting. The release JAR does not bundle AE2, JEI, EMI, Mekanism, or any other external mod.

For precise boundaries and tested versions, see the [support matrix](docs/product/support-matrix.md) and [known limitations](docs/product/limitations.md).

### Installation

1. Install Minecraft 1.21.1, NeoForge, Java 21, and Applied Energistics 2.
2. Add JEI, or EMI together with TooManyRecipeViewers, if you want the universal Aggregate Pattern Generator workflow.
3. Put the AE All Pattern JAR in the `mods` folder on both client and server.
4. Add only the optional machine and AE add-ons used by your pack.

## 中文说明

AE 全样板用于减少 AE2 自动化中重复编码、整理和维护成百上千张样板的工作。它提供三套可以独立使用、也可以互相配合的功能：

- **全样板生成器 + 聚合样板：** 从 JEI 或 EMI/TMRV 读取一台机器对应的配方，并把它们收进一张可管理的样板。
- **全样板链接器 + 全样板绑定器：** 直接把机器绑定到 AE 网络，将机器配方实时发布为虚拟处理样板。
- **天枢样板路由器：** 当同一产物存在多种配方时，根据当前订单动态选择路线。

### 聚合样板：一张装下一整套配方

1. 手持**全样板生成器**，潜行右击一台机器。
2. 生成的**聚合样板**会包含这台机器可编码的全部配方。面对数百、数千条配方时，数据会分批传输并分页保存在服务端，不会全部塞进一次网络包或物品 NBT。每次服务端启动后，首个可用的 JEI 或 EMI/TMRV 客户端会扫描一次已有目录，并在不改变服务端 UUID 的前提下更新内容。
3. 把聚合样板直接放入 AE2 样板供应器或受支持的附属供应器；其中启用的子样板会像普通 AE 样板一样参与合成。
4. 手持聚合样板右击，可按输入或输出搜索完整配方库、单独启用或禁用配方，并调整编码规则。

工作台配方仍会生成合成样板，可交给分子装配室；机器配方仍是处理样板。矿物词典/物品标签与候选输入会被保留，例如箱子配方可以自动使用网络中实际充足的任意有效木板，而不是锁死 JEI 展示的第一种木板。

### 绑定机器：不生成实体样板

1. 在 AE 网络上放置**全样板链接器**。
2. 手持**全样板绑定器**，潜行右击链接器完成选择。
3. 继续潜行右击任意数量的机器；紫色框代表连接成功。
4. 链接器会把机器的确定性配方发布为虚拟处理样板，并在投料前完整、安全地接管输入。

链接器是真实 AE 节点，占用一个频道并消耗 2 AE/t。绑定器和生成器都是可重复使用的工具，使用时不会被消耗。

### 聚合样板配置

每张聚合样板独立保存自己的设置。管理界面还可以搜索全部子样板，并只发布玩家真正需要的配方。

配方勾选状态只在实体物品中保存配方 ID，并自动在“已启用列表”和“已禁用列表”中选择较短的一边。目录刷新后，已删除的 ID 会被清理，新增配方按原有的多数默认状态处理，其余勾选语义保持不变。

| 选项 | 作用 | 默认 |
| --- | --- | :---: |
| 不编码概率主产物 | 主产物为概率产出时跳过整条配方 | 开启 |
| 不编码概率副产物 | 概率副产物不再标记为样板产物 | 开启 |
| 不编码耐久消耗配方 | 输入工具每次执行都会损失耐久时跳过；返还容器和不消耗的催化剂不受影响 | 开启 |
| 分裂同种物品 | 数量为 `n` 的同类输入展开成 `n` 个数量为 1 的独立输入槽 | 关闭 |
| 忽略产物 NBT/组件 | AE 规划时只匹配产物的基础物品类型 | 关闭 |
| 移除处理配方催化剂 | 从处理样板输入中移除识别为可重复使用的催化剂 | 关闭 |
| 物品/流体替换 | 使用 AE2 原生原料替换与容器流体替换 | 物品：关闭 · 流体：开启 |
| 删除流体或化学品 | 分别删除处理样板的流体/化学品输入或输出 | 关闭 |
| 输入材料首尾互换 | 交换处理样板的第一个和最后一个输入，适配重视输入顺序的子网机器 | 关闭 |

### 动态配方路由

**天枢样板路由器不是合成 CPU。** 它只负责优化下单计算与冲突配方选择，真正的任务仍由玩家搭建的普通 AE 合成 CPU 执行。

同一 ME 网络中有在线路由器时，合成确认界面会出现本次订单的路线偏好。可行性永远固定在第一位，保证最终采用的路线材料充足、能够完成。其余规则可以拖动排序、反向选择或关闭：

- 依赖路径更短或更长；
- 整条生产链的材料余量更多或更少；
- 单次产出更多或更少；
- 等待更少，优先选择有空闲设备的路线。

聚合样板默认优先级为 `-1`，因此玩家手动编码、优先级为 `0` 的普通样板始终优先。方块界面保存全局默认偏好，下单界面则允许临时覆盖并实时重新计算。默认情况下副产物只能随主产物生产，不能单独触发一整套昂贵配方；只有主动开启**副产物可独立下单**后，副产物才与主产物拥有相同的下单资格。

路由引擎已经完整内置，本模组不依赖 Thunderbolt 或 AE2 Lightning Tech。即使玩家安装了它们，普通闪电科技 CPU 仍由玩家安装的对应版本自行管理；只有网络中存在在线的天枢路由器时，AE 全样板才会应用自己的路由逻辑。

### 兼容与依赖

| 组件 | 支持情况 |
| --- | --- |
| AE 全样板 | 0.2.1 |
| Minecraft | 1.21.1 |
| 模组加载器 | NeoForge 21.1.219+ |
| Java | 21 |
| Applied Energistics 2 | 19.2.17（必需） |
| 配方查看器 | JEI 19.x，或 EMI + TooManyRecipeViewers |
| AE 通用键 | 原生物品与流体；安装 Applied Mekanistics + AE2 JEI Integration 后支持 Mekanism 化学品 |
| 机器生态 | 原版、Mekanism 及已测试的工厂附属、机械动力、神秘农业、工业先锋 |
| 合成生态 | 原版合成/切石/锻造、Extended Crafting、PackagedAuto/PackagedExCrafting |
| 样板供应器附属 | ExtendedAE、ExtendedAE Plus、AdvancedAE、Neo ECO AE Extension、AE2 Crystal Science、AE2 Lightning Tech、AE2LT Packaged Provider |

所有兼容模组均为可选依赖：不安装它们时，客户端和专用服务器也能正常启动。发布 JAR 不会把 AE2、JEI、EMI、Mekanism 或其他外部模组打包进去。

更精确的能力边界与测试版本见[支持矩阵](docs/product/support-matrix.md)和[已知限制](docs/product/limitations.md)。

### 安装

1. 安装 Minecraft 1.21.1、NeoForge、Java 21 与 Applied Energistics 2。
2. 若要使用通用聚合样板扫描，请安装 JEI，或同时安装 EMI 与 TooManyRecipeViewers。
3. 把 AE 全样板 JAR 同时放入客户端与服务端的 `mods` 文件夹。
4. 再按整合包需求添加机器模组和 AE 附属；它们都不是本模组的强制依赖。

## Development / 开发

```bash
./gradlew test
./gradlew runGameTestServer
./gradlew runClient
./gradlew runServer
./gradlew clean check build
```

The CI matrix covers unit tests, real client startup smoke tests, a two-process save/reopen persistence test, and no-GUI GameTests across minimal AE2, JEI, EMI/TMRV, machine-mod, provider-add-on, packaging, and Mekanism-add-on profiles. Test fixtures are downloaded at pinned versions during CI and are not bundled into the release JAR.

CI 会执行单元测试、真实客户端启动冒烟测试、跨两个独立游戏进程的存档重开测试，以及最小 AE2、JEI、EMI/TMRV、机器模组、供应器附属、打包合成和 Mekanism 附属等多组无界面 GameTest；测试依赖在流水线中按固定版本下载，不会进入发布 JAR。

Start with the [documentation index](docs/index.md). Architecture decisions, environment setup, testing strategy, release steps, troubleshooting, and the project roadmap are maintained under [`docs/`](docs/).

完整文档从[文档索引](docs/index.md)开始；架构决策、开发环境、测试策略、发布流程、排障说明和路线图均维护在 [`docs/`](docs/) 中。

## License / 许可证

Original AE All Pattern code is released under the [MIT License](LICENSE). The bundled routing-core portions derived from Thunderbolt Core and the single-block host code derived from AE2 Lightning Tech retain LGPL-3.0 notices. The Tianshu controller model and textures retain CC BY-NC-SA 3.0 terms. See [NOTICE.md](NOTICE.md) and the [licensing and assets policy](docs/development/licensing-and-assets.md) for full attribution.

AE 全样板自有代码使用 [MIT License](LICENSE)。内置路由核心中移植自 Thunderbolt Core 的部分，以及源自 AE2 Lightning Tech 的单方块宿主代码，保留 LGPL-3.0 声明；天枢控制器模型与贴图保留 CC BY-NC-SA 3.0 条款。完整来源与署名见 [NOTICE.md](NOTICE.md) 和[许可与素材政策](docs/development/licensing-and-assets.md)。
