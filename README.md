# Never farm（滚牧）

> [English](./README.en.md) | **简体中文**

一个 NeoForge 1.21.1 的挂机畜牧 Mod。放一个「滚木农场」方块，它会在日出日落替你收动物、喂饲料、养牲畜——人该干嘛干嘛去。

版本：`1.0.1` ｜ 协议：MIT ｜ 作者：QiCai

---

## 这是什么

原版的畜牧太累了：要喂、要收、要看孩子别跑丢、还要守着繁殖。这个 Mod 把你的牧场压缩成一格方块，剩下的交给日出日落。

**一句话：收集进方块 → 方块自动干活 → 该放的时候放出来。**

---

## 功能一览

- **收纳与放出**：范围内的成年动物直接右键收进方块，日出按阈值放出，随时也能手动放
- **类型绑定与锁定**：方块自动绑定第一只收到的动物类型；可以用刷怪蛋预绑定，防止混入杂种
- **自动喂食**：把饲料（植物/种子等）存进方块，日出结算是方块来喂，不是你来喂
- **阈值放牧**：每个方块可设定日出放出几只（2 / 4 / 6），保持牧场不爆栏
- **繁殖管控**（默认开启）：任何繁殖行为——不管是你手动喂的还是自动的——亲代和幼崽都会被立刻回收，次日日出再放出。防的就是繁殖失控
- **红石自动繁殖**：给方块一个红石信号，日出时它就用存货饲料批量生幼崽，纯自动化
- **AI 圈养**：放出的动物被拴在方块附近，半径可配置，不会满山乱跑
- **区块弱加载**（默认关闭）：OP 可用 `/neverfarm keepLoaded` 开启 3×3 区块加载，人走了机器照样转
- **Don't Starve 生存 DLC**（默认关闭）：给牧场加一点现实的味道——每天要消耗饲料，供不上就挨饿，存栏动物和区域里没喂过的牲畜都会被套上凋零 debuff
- **Jade 联动**：装了 Jade 直接看方块状态（绑定类型、存栏、阈值、饲料量），不用点开界面
- **掉落保数据**：拆方块掉落的物品自带全部数据（动物、饲料、阈值、绑定），换个位置重新放下，牧场原封不动

---

## 怎么用

### 摆方块

创造模式物品栏「滚牧」标签页里拿 **滚木农场**，或者自己合成（如果有配方，以游戏内为准）。把方块放在牧场中央，然后往它周围赶成年动物。

> 收集范围大约是方块所在的 3×3 区块（半径 48 格）。不知道怎么赶动物？拿着小麦/胡萝卜往方块走，动物自己会跟过来。

### 交互操作

| 操作 | 效果 |
| --- | --- |
| 右键（空手） | 收集范围内符合条件的动物（成年、喂过、没名字、羊没被剪过毛） |
| 右键（拿饲料） | 存入饲料，最多 64 份 |
| 右键（空手，再点一下） | 取出 64 份饲料 |
| **长按右键**（≥250ms） | 打开阈值滑块界面：移动鼠标调到 2 / 4 / 6，**松开右键确认** |
| 潜行 + 右键 | 放出 8 只；40 tick 内连续点 3 次 = 全部放出 |
| 潜行 + 左键 | 切换类型锁定。空手 = 绑定最近的动物；拿刷怪蛋 = 直接预绑定该类型。方块里有存货时无法解绑 |

### 红石自动繁殖

1. 给方块旁边放一个红石信号（按钮、拉杆、红石线都行，带电就行）
2. 方块内存好足够的饲料
3. 日出时它会消耗饲料生出 4~6 只幼崽（场上幼崽超过 12 只时会偷懒）

### Don't Starve DLC

在 `never_farm-common.toml` 里把 `donnotstarve.enable` 改成 `true` 开启：

- 每天结算时按存栏数消耗饲料（`consumeRate` 可调比例）
- 饲料不够 → 存栏动物被标记为挨饿（放出来时带 debuff），同时扫描范围内没被玩家喂过的牲畜也会中招
- 默认 debuff 是凋零 300 秒，影响牛、羊、猪；这些都可在配置里改

### 弱加载

- 配置文件 `general.keepLoaded`（默认 `false`）
- 或游戏内 OP（权限等级 2）执行：

```
/neverfarm keepLoaded        # 切换开关
/neverfarm keepLoaded true   # 或显式指定
```

开启后每个方块会给周围 3×3 区块加弱加载 ticket，人下线 / 走远也不影响日出日落结算。

---

## 命令

| 命令 | 权限 | 效果 |
| --- | --- | --- |
| `/neverfarm keepLoaded [true\|false]` | OP 2 | 全局开关区块弱加载 |
| `/neverfarm autobreed [true\|false]` | OP 2 | 全局开关红石自动繁殖 |

---

## 配置

配置文件位于 `config/never_farm-common.toml`（单机在 `.minecraft/config/`，服务端在服务器根目录 `config/`）。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `general.maxSlots` | `32` | 每方块存栏上限（16~64） |
| `general.checkInterval` | `Sunrise_Sunset` | 自动作业时间点（目前仅支持日出日落各一次） |
| `general.enableAIRestrict` | `true` | 放出动物的 AI 圈养开关 |
| `general.aiRestrictRadius` | `8` | AI 圈养半径（格），跑远会被拉回 |
| `general.keepLoaded` | `false` | 是否默认开启区块弱加载 |
| `general.enforceBreedingControl` | `true` | 繁殖管控（亲代+幼崽回收） |
| `donnotstarve.enable` | `false` | 生存 DLC 开关 |
| `donnotstarve.starveDeathsPerCheck` | `2` | 每次结算饿死的动物数 |
| `donnotstarve.consumeRate` | `1.0` | 每日饲料消耗比例（按存栏数） |
| `donnotstarve.affectedMobs` | 牛/羊/猪 | 受饲料规则影响的实体 ID 列表 |
| `donnotstarve.debuffEffect` | `WITHER` | 挨饿 debuff（也支持注册名如 `minecraft:wither`） |
| `donnotstarve.debuffDuration` | `300` | debuff 时长（秒） |

---

## 装到自己服务器

1. 服务端装 NeoForge 21.1.248
2. 把 mod jar 丢进 `mods/`
3. （可选）装 Jade 看方块 HUD，装不装都不影响功能
4. 重启，OP 记得执行 `/neverfarm keepLoaded true` 才能让方块在玩家离线时继续干活

---

## 开发

环境：Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21 / Gradle（wrapper 自带）。

```bash
./gradlew runClient        # 跑客户端
./gradlew runServer        # 跑服务端（工作目录 run/）
./gradlew runGameTestServer # 跑游戏测试
```

结构速览：

```
src/main/java/dev/never_farm/
├── Never_farm.java         # 主类：注册方块/物品/方块实体/创造标签
├── Config.java             # 配置
├── block/WorkBlock.java    # 方块本体（含掉落数据保留）
├── blockentity/WorkBlockEntity.java  # 核心逻辑：收集/放出/繁殖/结算
├── handler/                # 事件处理器（调度/繁殖管控/交互/命令/AI）
├── network/                # C2S/S2C 数据包（阈值、方块操作、数据请求）
├── client/                 # 阈值滑块界面、Jade 插件
└── gametest/               # 自动化测试
```

---

## 开发日志

- `1.0.1`：当前版本。弱加载命令、红石繁殖开关、掉落数据保留、Jade 联动
- 测试：`WorkBlockGameTests` 随 `runGameTestServer` 运行，验证收集/放出/阈值等核心路径

---

## 待办 / 想法

- 更多作业时间点（按需，不只是日出日落）
- 多维度/多世界测试
- 合成配方
- 如果服务器方块多了，考虑错峰结算（现在的结算是全球同一 tick 触发）

有问题欢迎直接开 issue，或者到游戏里试了再回来吐槽。
