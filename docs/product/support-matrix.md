# 当前支持矩阵

本页描述仓库当前代码，而不是最终愿景。具体依赖版本以根目录 `gradle.properties` 为唯一构建事实来源。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| Minecraft 1.20.1 + Forge | 已实现、待发布 | 当前 `mc/1.20.1` 分支；Java 17；Forge 47.4.20；版本 0.2.1 |
| Minecraft 1.21.1 + NeoForge | 已实现 | 由 `main` 分支维护，不与本分支产物混用 |
| AE2 Grid Node/provider | 已实现 | 一个频道、2 AE/t，断频道时停止发布 |
| 两阶段绑定与持久化 | 已实现 | 64 格、同维度、所有者校验、schema 化 SavedData |
| 紫色 AE 包围框 | 已实现 | 只同步给所有者，96 格客户端渲染上限 |
| 虚拟处理样板 | 已实现 | `ICraftingProvider` + 绑定级稳定身份，不生成实体样板库存 |
| 输入安全缓冲 | 已实现 | 先持久接管，再模拟/提交；解绑和拆除可恢复尚未投出的物品 |
| 原版熔炉/高炉/烟熏炉 | 已实现 | 机器或外部物流自行供燃料，单输入确定性主产物 |
| Mekanism 冶炼/粉碎/富集 | 已实现 | 单机与对应工厂；依赖缺失时 compat 不加载 |
| JEI 聚合样板扫描 | 已实现 | 右击机器时按页上传配方，物品/流体保存为 AE 通用键 |
| Mekanism Chemical 聚合样板 | 已实现（可选） | 安装 Applied Mekanistics 后保留真实 Chemical 键和数量 |
| PackagedAuto 聚合样板 | 已实现（可选） | 聚合样板可在包装供应器工作流中展开并保持目标机器映射 |
| Advanced AE 高级样板编码器 | 已实现（可选） | 仅灌注工厂聚合样板可被高级样板编码器读取编辑；未选中的子样板不向编码器暴露 |
| 样板供应器附属 | 已实现（可选） | 已验证 ExtendedAE、ExtendedAE Plus、AdvancedAE、Neo ECO AE Extension、AE2 Crystal Science、AE2 Lightning Tech、AE2LT Packaged Provider |
| 万象合金炉聚合样板 | 已实现（可选） | 安装 Useless Mod 时启用高级合金炉兼容；缺失时对应混入不加载 |
| 诊断 | 已实现 | `/aeallpattern status` 与管理员 `/aeallpattern perf` |
| 绑定器自动投料的概率/流体/化学品/跨维度 | 暂不支持 | 聚合样板发布与机器执行是两条独立边界；执行仍需对应 AE 兼容层 |

任何 Release 都必须同步更新本表，避免“文档愿景”被误认为已发布功能。

1.20.1 测试矩阵固定使用 ExtendedAE Plus 1.6.1-f1 + ExtendedAE 1.4.6，以及 Neo ECO AE Extension 20.3.0。更高版本当前存在其自身的旧版 Mixin 目标不兼容，升级前必须重新完成启动与 GameTest 验证。
