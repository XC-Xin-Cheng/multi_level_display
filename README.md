# Multi-Level Display — Meteor Client 模块显示层扩展

一个基于 [meteor-addon-template](https://github.com/MeteorDevelopment/meteor-addon-template) 的 Meteor Client 扩展（addon），
给模块列表界面（Modules 界面）加上 **"层（Layers）"** 功能：

- 每个层可以自定义**显示哪些模块**，切换层后分类窗口里只显示该层勾选的模块；
- 界面右上角（搜索窗口旁边）有一个"层"窗口，提供 **上一层 `<` / 下一层 `>` / 添加层 `+`** 按钮；
- 窗口下方是**按分类折叠的模块勾选列表**，勾选 = 当前层显示该模块，勾选后分类窗口立即刷新；
- 层配置保存在 `.minecraft/meteor-client/layers.json`，每次改动即保存。

> 目标版本：**Minecraft 26.1（26.1.2）Fabric + Meteor Client 26.1.2-SNAPSHOT**，
> 与 2026-10 的 [meteor-addon-template](https://github.com/MeteorDevelopment/meteor-addon-template) master 结构一致（Kotlin DSL + version catalog）。

## 功能说明

> **字体提示**：Meteor 的 GUI 使用自带字体（JetBrains Mono 等，不含 CJK 字形），
> 所以 GUI 里层名/标题请用英文（默认层叫 `Default`，新建层自动命名 `Layer 2`、`Layer 3`…）。
> 中文层名在聊天与命令（`.layer`）里显示完全正常，只是 GUI 标签里会是空白；
> 可以随时用 `.layer rename <新名字>` 改成英文名。

### 层窗口（Modules 界面右上角）

| 元素 | 说明 |
| --- | --- |
| `<` / `>` | 切换上一层 / 下一层（循环切换），中间的标签显示 `序号/总数 层名` |
| `+` | 新建一层并切换过去（层名自动去重） |
| `−` | 删除当前层（两段式确认；最后一层不可删除） |
| 分类折叠列表 | 按分类列出全部模块，行首勾选框 = 该模块在本层显示/隐藏；**分类标题左侧的开关 = 整个分类一键显示/隐藏** |

### 保存模块状态（层 = 配置方案）

每个层除了决定**显示哪些模块**，还会记住**模块的启停状态**（类似 Profiles）：

- 切换层的瞬间，离开的层自动记录当时各模块的开关状态，进入的层自动恢复自己保存的状态；
- 从未保存过状态的层（新建的层）在进入时**不会改动**任何模块；
- "Default" 层显示全部模块；在空层里勾选想要的模块即可控制显示。

也就是说：切到 "PvP" 层 → 自动开启你上次在 PvP 层用的模块、关掉其他层的，同时界面里只显示 PvP 层勾选的模块。

- 新建的层默认**什么都不显示**，在下方列表里勾选想要的模块即可；
- 初始有一个"默认"层，`showAll = true`，显示全部模块；
  在"默认"层取消勾选某个模块时，会自动转换成白名单模式（保留其它全部模块）；
- 层窗口和普通 Meteor 窗口一样可以拖动、折叠，位置会被记住。

### 命令

`.layer`（别名 `.layers`）：

| 命令 | 说明 |
| --- | --- |
| `.layer` | 查看当前层状态 |
| `.layer next` / `.layer prev` | 切换下一层 / 上一层 |
| `.layer set <名字>` | 按名字切换（带补全） |
| `.layer add <名字>` | 新建层并切换 |
| `.layer remove <名字>` | 删除层（至少保留一层） |
| `.layer rename <新名字>` | 重命名当前层 |
| `.layer showall` | 切换当前层"显示全部 / 白名单"模式 |
| `.layer list` | 列出所有层（当前层绿色高亮） |

## 构建与环境

- 需要 **JDK 25**：Loom 1.18 要求 Gradle 主进程也运行在 Java 25 上（工具链自动下载只覆盖编译用的 JDK，不够）。
  - 机器上默认 java 就是 25 时，直接执行下面的命令即可；
  - 否则先设置 `JAVA_HOME` 指向 JDK 25 再执行（Windows 可用 `gradlew.bat`）。
- 需要联网（会从 Fabric / Mojang / Meteor 的 Maven 拉取依赖）：

```bash
./gradlew build
```

产物在 `build/libs/multi_level_display-<版本>.jar`（用不含 `-sources` 的那个）。

## 自动化测试

项目带一个游戏内自动测试钩子（`LayersAutoTest`，仅当 JVM 参数 `-Dlayers.autotest=true` 时生效）：

```bash
./gradlew runTestClient   # 启动游戏 → 自动打开模块界面 → 验证层切换/过滤/勾选/持久化 → 截图到 run/ → 自动退出
```

覆盖场景：层窗口挂载、`<`/`>`/`+` 对应的切换与环绕、空层过滤（模块列隐藏/恢复）、
勾选列表真实点击路径（模块出现/消失）、Default 层 showAll→白名单自动转换、
模块状态保存/恢复（切层自动开关模块）、
层名去重与删除、`layers.json` 持久化与跨启动恢复。
普通 `./gradlew runClient` 不受影响。

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/installer/)（0.19.5+）与对应版本的 Fabric API 环境；
2. 把 meteor-client（26.1.2 对应版本）放进 `.minecraft/mods`；
3. 把本扩展的 jar 放进 `.minecraft/mods`；
4. 游戏内按 Right Shift 打开模块界面即可看到右上角的"层"窗口。

## 代码结构

```
src/main/java/com/example/layers/
├── LayersAddon.java                  # addon 入口（meteor entrypoint）
├── layers/Layer.java                 # 层数据模型（showAll 或模块名白名单）
├── layers/Layers.java                # 层管理器：增删切换 + JSON 持久化（meteor-client/layers.json）
├── gui/LayerGui.java                 # 层窗口 UI、模块勾选列表、分类窗口重建/过滤
├── commands/LayerCommand.java        # .layer 命令
└── mixin/MixinWCategoryController.java
                                      # 往 ModulesScreen$WCategoryController.init() 尾部
                                      # 注入层窗口 + 按当前层重建分类窗口
```

实现要点：

- Meteor 26.x 的模块界面中，每个分类是一个独立窗口，由 `ModulesScreen` 的内部类
  `WCategoryController` 创建；本扩展通过 Mixin 在其 `init()` 末尾插入一个"层"窗口，
  并在切换层 / 勾选模块时**用公开 GUI API 重建这些分类窗口**（只显示当前层的模块）；
- 全部只用了 Meteor 的公开 API + 一个 Mixin，未修改、未复制 Meteor 或其它插件的内部实现。

## 真实环境测试

项目带 `launch-test.py`（工作区根目录）：直接以离线模式启动 PCL 真实环境（`D:PCL.minecraft`）跑自动化测试。

- `-Dlayers.autotest=safe` → 只验证挂载 + 截图，不碰层数据和模块状态（用于真实环境）；
- `-Dlayers.autotest=true` → 完整测试，结束后自动恢复现场（用于 dev 环境）；
- 测试截图写到游戏目录 `layers-test-*.png`。

## 已知与类似功能的区别

- Meteor 自带的 `Config -> Hidden Modules`：全局隐藏模块，与层无关；
- Meteor 自带的 `Profiles`：保存/恢复模块开关和配置状态，不改变界面显示；
- [meteor-rejects](https://github.com/AntiCope/meteor-rejects) 的 Hidden Modules：同样是全局隐藏。
  本扩展的"层"是**可切换的多套显示方案**，切换即时生效。
