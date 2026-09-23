# 沉浸工程催化剂槽回归

本测试安装真正的 IE 和 JEI，在客户端创建真实配方布局，通过生产扫描器编码，
再在内置服务器线程展开 AE 样板。不是伪造槽位的单元测试。

固定测试依赖（仅在显式启用时加载，不进入发布包）：

- 1.20.1 Forge：IE 10.2.0-183，Modrinth 版本 WUzj4tgJ。
- 1.21.1 NeoForge：IE 12.4.2-194，Modrinth 版本 uNRARSH2。

## 运行

在各版本仓库下，将该版本的测试存档副本放到
`run-immersive-catalyst-test/saves/CatalystTest`，不要使用玩家的原始存档。
1.21.1 首次启动可在此测试目录的 options.txt 设置 `onboardAccessibility:false`，
避免首次启动引导挡住快速进入存档。

```sh
./gradlew runClient --console=plain \
  -Pimmersive_catalyst_test=true \
  -Pruntime_jei=true -Pruntime_emi=false \
  -Pruntime_mekanism=false -Pruntime_mystical_agriculture=false \
  -Pruntime_create=false -Pruntime_industrial_foregoing=false \
  -Pruntime_neoecoae=false -Pruntime_ae_provider_addons=false \
  -Pruntime_packaged_addons=false -Pruntime_mekanism_addons=false
```

成功标志：日志包含 `IE_CATALYST_CLIENT_TEST_PASSED`，随后客户端自动退出。
该测试需要图形上下文（Linux 可使用虚拟显示）；专用 nogui 服务端没有 JEI 布局。

## 断言及边界

- 遍历真实金属冲压配方布局，确认板模具的槽角色。
- 扫描真实类别，定位 `immersiveengineering:metalpress/plate_iron`。
- 分别关闭、开启移除催化剂，验证 AE 输入中铁锭保留、铁板输出数量为 1。
- 1.20.1 模具为 INPUT：两种设置下均保留，暴露上游没有声明催化剂的局限。
- 1.21.1 模具为 CATALYST：本来就不进入材料输入，两种设置下均不要求模具；
  不能把这个结果宣称为新增移除逻辑才带来的修复。

本测试不验证机器搭建、投料生产、EMI 原生桥或其他整合包的自定义配方。
不会向玩家样板库上传测试配方，不会自动添加模具白名单。
