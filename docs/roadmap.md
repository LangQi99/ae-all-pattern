# 实施路线图

## Phase 0：工程骨架

- [x] NeoForge 1.21.1 可编译项目。
- [x] Forge 1.20.1 / Java 17 完整兼容分支与独立依赖矩阵。
- [x] AE2/JEI/Mekanism 依赖分层。
- [x] 绑定器与链接器注册。
- [x] 配方指纹纯单元测试。
- [x] 架构、开发、测试、发布文档。

## Phase 1：绑定与链接器

- [x] 链接器方块、BlockEntity、IManagedGridNode、频道与能耗。
- [x] Binder Data Component 和两阶段服务端状态机。
- [x] SavedData、schema 隔离、所有者权限与解绑。
- [x] 紫色包围框同步和渲染。
- [x] 纯状态机测试和资源测试。
- [x] 基础 GameTest。

## Phase 2：原版熔炉 MVP

- [x] MachineAdapterRegistry。
- [x] RecipeManager 共享索引、规范化、指纹和 diff。
- [x] VanillaFurnaceAdapter；燃料由机器或外部物流供应。
- [x] PatternDetailsHelper + ICraftingProvider。
- [x] IncomingBuffer、跨面强制输入、PendingCraft 与确定性输出自动回收。
- [x] `/reload` 代数失效、NBT 往返、堵塞保留和断频道发布保护。

## Phase 3：Mekanism

- [x] 条件加载 Mek compat。
- [x] 充能冶炼炉与冶炼工厂适配。
- [x] 明确 RecipeType 映射、绑定面优先与合法输入面自动回退。
- [x] 粉碎/富集等确定性 ItemStackToItemStack 配方。
- [x] 无 Mek 专服和完整环境双矩阵 GameTest。

## Phase 4：展示、过滤与性能

- [x] JEI 轻量使用说明；完整目录继续只在 AE 终端显示，避免大包同步和信息越权。
- [ ] 绑定过滤 UI 和优先级（计划 0.2.x；0.1.0 使用 4096 硬上限）。
- [x] 诊断命令、缓存统计和 10000 指纹规模门禁。
- [x] GameTest 空结构自动生成与测试场清单生成器。

## Phase 5：发布

- [x] 自有 16×16 图标、128×128 模组图标和完整模型。
- [ ] README 实机截图与演示视频（发布页面素材，不阻塞代码产物）。
- [ ] GitHub Release、Modrinth、CurseForge 上传（需要维护者平台凭据）。
- [x] 版本、元数据与 JAR 内容一致性门禁。
