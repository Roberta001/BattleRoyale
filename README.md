# Battle Royale - A Minecraft Minigame Plugin (English)

This is a classic Battle Royale ("last man standing") minigame plugin designed for Spigot/PaperMC servers. It provides a complete and automated gameplay loop where players can pay an entry fee to join a deathmatch, competing to win a share of the total prize pool based on their in-game performance (kills and survival time).

This plugin is built upon the **MiniGameManager** framework, ensuring robust player data handling and compatibility with other minigames that use the same service.

## ✨ Core Features

*   **Intuitive GUI Menu**: Players can interact with the game through a simple and clean GUI menu (`/br menu`), allowing them to create, join, or leave games with a single click.
*   **Advanced Map System**: Each match runs in an isolated, temporary world. Admins can configure maps to be:
    *   **Procedurally generated** with a random seed.
    *   A **complete copy** of a pre-built template world, ideal for static arenas.
    *   **Generated from a template's `level.dat`** (for custom world generators) while also copying specific folders like `datapacks`, enabling map-specific data packs and structures!
*   **Built on a Central Framework**: Utilizes the **MiniGameManager** service for all player data handling. This ensures a fully automated and safe process for saving and restoring inventory, health, experience, and more.
*   **Cross-Minigame State Awareness**: Prevents players from joining a Battle Royale match if they are already participating in another minigame that uses the MiniGameManager service.
*   **Dynamic Airdrop System**: Airdrops with progressively better loot spawn at key moments during the match, creating hotspots for conflict and comeback opportunities.
*   **Comprehensive Spectator Mode**: Upon elimination, players automatically become spectators. They can fly around the map to watch the rest of the action. Outsiders can also join mid-game to spectate.
*   **Vault Economy Integration**: Seamlessly hooks into your server's economy via Vault to handle entry fees and prize payouts automatically.
*   **Classic Shrinking Border**: The world border shrinks in phases, forcing players into a smaller area and encouraging combat.
*   **Dynamic Scoring & Prize Distribution**: Players earn points for kills and survival time. The total prize pool is distributed among all participants proportionally to their final score, rewarding skilled play.
*   **Engaging Game Phases**: Features a waiting lobby, a preparation phase, a PvP grace period, a BossBar for status updates (visible to spectators too), and custom sound effects to enhance the player experience.
*   **Admin Tools**: Administrators have access to a dedicated admin panel within the GUI to force-start games, providing better control for server events.

## 🎮 Gameplay Flow

1.  **Open the Menu**: A player opens the main menu using `/br menu`.
2.  **Game Creation**: The player clicks the "Create Game" button and is prompted to enter an entry fee via command (`/br create <amount>`). A server-wide broadcast invites others to join.
3.  **Joining the Lobby**: Other players can join by clicking the "Join Game" button in the menu or using `/br join`. A countdown begins once the minimum number of players has joined.
4.  **World Preparation**: When the countdown ends, a new temporary world is created based on the selected map's configuration. All participants' data is saved by MiniGameManager, and they are teleported to random locations. Players are frozen for a brief period.
5.  **Game Start**: The freeze is lifted. A grace period without PvP begins.
6.  **Combat, Airdrops, & Border Shrink**: After the grace period, PvP is enabled. At key intervals, airdrops will spawn, and the world border will begin to shrink.
7.  **Elimination & Spectating**: When a player is eliminated, they enter Spectator Mode. The killer is awarded points.
8.  **Game End**: The game concludes when only one player remains. A summary is broadcast, showing the final rankings, scores, and prize money.
9.  **Cleanup**: All players are teleported back to their original locations with their data restored. The temporary game world is deleted, and the plugin resets.

## 🗺️ Advanced Map Configuration

The plugin's map system is highly flexible. All map configurations are `.yml` files located in the `plugins/BattleRoyale/maps/` directory. World templates are stored in `plugins/BattleRoyale/maps/worlds/`.

Here are the three ways you can set up a map:

### Mode 1: Procedural Generation
This mode generates a new, random vanilla world for each match. It's the simplest setup.

**`maps/procedural_map.yml`:**
```yaml
display-name: "&aProcedural Plains"
world-source:
  type: GENERATE
```

### Mode 2: Full World Copy
This mode creates an exact copy of a pre-built world template. Ideal for custom-built arenas or adventure maps where the terrain should never change.

1.  Place your world folder (e.g., `my_arena`) inside `maps/worlds/`.
2.  Create the config file.

**`maps/arena_map.yml`:**
```yaml
display-name: "&eSky Arena"
world-source:
  type: COPY
  source-folder-name: "my_arena"
```

### Mode 3: Template-Based Generation (with Data Packs)
This is the most powerful mode. It uses a template's `level.dat` to define the world generation rules (e.g., custom biomes, structures from a data pack) but generates a new world with a random seed every time. It also allows you to copy specific folders, like `datapacks`, into the new world.

1.  Create a template folder (e.g., `datapack_template`) inside `maps/worlds/`.
2.  Inside `datapack_template`, place your `level.dat` file and your `datapacks` folder.
3.  Create the config file.

**`maps/datapack_map.yml`:**
```yaml
display-name: "&cDatapack Wasteland"
world-source:
  type: COPY
  source-folder-name: "datapack_template"
  
  # This tells the plugin to ONLY use the level.dat for generation rules
  # and not copy the region files.
  import-level-dat-settings: true
  
  # This list specifies which additional folders to copy from the source.
  # This is how you include data packs in the generated world.
  folders-to-copy-on-import:
    - "datapacks"
```

## ⌨️ Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/br menu` or `/br` | **(Recommended)** Opens the main GUI menu for all actions. | `none` |
| `/br create <amount>` | Creates a new Battle Royale game with a set entry fee. | `none` |
| `/br join` | Joins the current game waiting in the lobby. | `none` |
| `/br leave` | Leaves the lobby, quits the match, or exits spectator mode. | `none` |
| `/br forcestart` | Force-starts the game from the admin panel or command. | `br.admin` |

## ⚙️ Installation & Dependencies

1.  **Dependencies**:
    *   [**MiniGameManager**](https://github.com/Roberta001/MiniGameManager) (Required) - The core service for player data management.
    *   [**Vault**](https://www.spigotmc.org/resources/vault.34315/) (Required)
    *   A Vault-compatible **economy plugin** (e.g., EssentialsX, CMI) (Required)
    *   **NBT API** (Required) - Needed for advanced world manipulation.
2.  **Installation Steps**:
    *   Ensure your server is running Spigot or a fork (e.g., Paper, Purpur).
    *   Place `MiniGameManager.jar`, `Vault.jar`, `NBTAPI.jar`, and `BattleRoyale.jar` into your server's `plugins` directory.
    *   Install an economy plugin if you don't have one.
    *   Restart your server.

---
# Battle Royale - Minecraft 大逃杀插件 (中文)

这是一个为 Spigot/PaperMC 服务器设计的经典大逃杀（吃鸡）小游戏插件。它提供了一个完整、自动化的游戏流程，玩家可以通过支付报名费加入一场“最后生还者”模式的战斗，根据游戏表现（击杀与存活）瓜分总奖池。

本插件基于 **MiniGameManager** 框架构建，以确保强大的玩家数据处理能力以及与其它使用该服务的小游戏的兼容性。

## ✨ 核心功能

*   **直观的图形菜单 (GUI)**: 玩家可通过 `/br menu` 打开简洁明了的图形界面，一键完成创建、加入或离开游戏等所有操作。
*   **高级地图系统**: 每场比赛都在一个独立的临时世界中进行。管理员可以配置地图实现：
    *   **程序化生成**：每次都生成一个随机的全新世界。
    *   **完整复制**：完全复制一个预先构建好的世界模板，适用于固定的竞技场地图。
    *   **模板化生成 (含数据包)**：使用模板的 `level.dat` 文件作为世界生成规则（例如自定义生物群系），但每次都使用随机种子生成地形。此模式还支持复制 `datapacks` 等特定文件夹，从而实现地图专属的数据包！
*   **基于核心框架构建**: 依赖 **MiniGameManager** 服务进行玩家数据管理，全自动且安全地保存和恢复玩家的背包、血量、经验等状态。
*   **跨游戏状态感知**: 如果玩家正在参与另一个使用 MiniGameManager 服务的小游戏，将无法加入大逃杀，有效防止冲突。
*   **动态空投系统**: 在比赛的关键时刻，会刷新装有渐进式高级战利品的空投，制造冲突热点，并为玩家提供翻盘机会。
*   **完整的旁观者模式**: 玩家被淘汰后会自动进入旁观者模式，可以在地图中自由飞行，观赏剩余的战斗。外部玩家也可以在游戏中途加入观战。
*   **经济系统集成 (Vault)**: 通过 Vault 插件无缝对接服务器经济系统，自动处理报名费和奖金发放。
*   **经典的缩圈机制**: 世界边界（毒圈）会分阶段缩小，迫使玩家向中心区域移动，增加战斗的激烈程度。
*   **动态积分与奖金分配**: 玩家通过击杀敌人和存活时间来获得积分。游戏结束后，总奖池会根据所有玩家的积分比例进行分配，奖励表现出色的玩家。
*   **丰富的游戏阶段与提示**: 包含等待大厅、准备阶段、PVP保护期、BossBar 状态提示（旁观者可见）、音效反馈等，提升了整体游戏体验。
*   **管理员工具**: 管理员在 GUI 中拥有专属的管理面板，可以强制开始游戏，方便在活动等场景下控制游戏进程。

## 🎮 玩法流程

1.  **打开菜单**: 玩家使用 `/br menu` 指令打开主菜单。
2.  **创建游戏**: 玩家点击“创建游戏”按钮，并根据提示使用指令 (`/br create <金额>`) 输入报名费。服务器会向所有玩家广播游戏邀请。
3.  **加入大厅**: 其他玩家在菜单中点击“加入游戏”或使用 `/br join` 指令加入游戏大厅。当人数达到最低要求时，游戏开始倒计时。
4.  **世界准备**: 倒计时结束，插件根据地图配置创建新世界。MiniGameManager 保存所有参与者的数据，然后将他们传送到随机位置。玩家在短暂的准备时间内会被冻结。
5.  **游戏开始**: 准备时间结束后，玩家可以自由行动。游戏开始后有PVP保护期。
6.  **战斗、空投与缩圈**: PVP开启后，玩家可以互相攻击。在关键时间点，空投会降落，世界边界也会开始缩小。
7.  **淘汰与观战**: 玩家被淘汰后，将进入旁观者模式。击杀者会获得积分。
8.  **游戏结束**: 当场上仅剩最后一名玩家时，游戏结束。系统会公布所有玩家的积分排名和他们获得的奖金。
9.  **清理现场**: 所有玩家的数据都将被恢复，并传送回原始位置。随后，临时游戏世界将被自动删除，插件重置为空闲状态。

## 🗺️ 高级地图配置

插件的地图系统非常灵活。所有地图配置文件（`.yml`）都存放在 `plugins/BattleRoyale/maps/` 目录中，而世界模板则存放在 `plugins/BattleRoyale/maps/worlds/` 目录中。

以下是配置地图的三种模式：

### 模式一：程序化生成
此模式为每场比赛生成一个全新的、随机的原版世界。这是最简单的设置。

**`maps/procedural_map.yml`:**
```yaml
display-name: "&a随机平原"
world-source:
  type: GENERATE
```

### 模式二：完整世界复制
此模式会创建一个预设世界模板的精确副本。适用于那些地形固定不变的自定义竞技场。

1.  将你的世界文件夹（例如 `my_arena`）放入 `maps/worlds/` 目录。
2.  创建对应的配置文件。

**`maps/arena_map.yml`:**
```yaml
display-name: "&e天空竞技场"
world-source:
  type: COPY
  source-folder-name: "my_arena"
```

### 模式三：模板化生成 (含数据包)
这是最强大的模式。它利用模板中的 `level.dat` 文件来定义世界生成规则（例如，由数据包添加的自定义生物群系或结构），但每次游戏都会使用随机种子生成全新的地形。同时，它允许你将 `datapacks` 这样的特定文件夹也复制到新世界中。

1.  在 `maps/worlds/` 目录下创建一个模板文件夹（例如 `datapack_template`）。
2.  将你的 `level.dat` 文件和 `datapacks` 文件夹放入 `datapack_template` 中。
3.  创建对应的配置文件。

**`maps/datapack_map.yml`:**
```yaml
display-name: "&c数据包废土"
world-source:
  type: COPY
  source-folder-name: "datapack_template"
  
  # 告诉插件仅使用 level.dat 的生成规则，而不要复制旧的地形文件。
  import-level-dat-settings: true
  
  # 这个列表指定了需要从模板中额外复制哪些文件夹。
  # 这就是将数据包加载到新生成世界的方法。
  folders-to-copy-on-import:
    - "datapacks"
```

## ⌨️ 指令与权限

| 指令 | 描述 | 权限 |
| :--- | :--- | :--- |
| `/br menu` 或 `/br` | **(推荐)** 打开图形主菜单，进行所有操作。 | `无` |
| `/br create <金额>` | 创建一场新的大逃杀游戏，并设置报名费。 | `无` |
| `/br join` | 加入当前正在等待的游戏。 | `无` |
| `/br leave` | 离开大厅、退出比赛或退出旁观者模式。 | `无` |
| `/br forcestart` | 通过管理面板或指令强制开始游戏。 | `br.admin` |

## ⚙️ 安装与依赖

1.  **前置插件**:
    *   [**MiniGameManager**](https://github.com/Roberta001/MiniGameManager) (必需) - 负责玩家数据管理的核心服务。
    *   [**Vault**](https://www.spigotmc.org/resources/vault.34315/) (必需)
    *   一个 Vault 支持的**经济插件** (例如: EssentialsX, CMI) (必需)
    *   **NBT API** (必需) - 用于高级世界文件操作。
2.  **安装步骤**:
    *   确保你的服务器是 Spigot 或其衍生版 (如 Paper, Purpur)。
    *   将 `MiniGameManager.jar`, `Vault.jar`, `NBTAPI.jar` 和 `BattleRoyale.jar` 文件放入服务器的 `plugins` 文件夹。
    *   如果你没有经济插件，请安装一个。
    *   重启服务器。