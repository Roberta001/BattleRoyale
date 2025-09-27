# Battle Royale - A Minecraft Minigame Plugin (English)

This is a classic Battle Royale ("last man standing") minigame plugin designed for Spigot/PaperMC servers. It provides a complete and automated gameplay loop where players can pay an entry fee to join a deathmatch, competing to win a share of the total prize pool based on their in-game performance (kills and survival time).

This plugin is built upon the **MiniGameManager** framework, ensuring robust player data handling and compatibility with other minigames that use the same service.

## ✨ Core Features

*   **Intuitive GUI Menu**: Players can interact with the game through a simple and clean GUI menu (`/br menu`), allowing them to create, join, or leave games with a single click.
*   **Built on a Central Framework**: Utilizes the **MiniGameManager** service for all player data handling. This ensures a fully automated and safe process for saving and restoring inventory, health, experience, and more.
*   **Cross-Minigame State Awareness**: Prevents players from joining a Battle Royale match if they are already participating in another minigame that uses the MiniGameManager service.
*   **Dynamic Airdrop System**: Airdrops with progressively better loot spawn at key moments during the match, creating hotspots for conflict and comeback opportunities.
*   **Comprehensive Spectator Mode**: Upon elimination, players automatically become spectators. They can fly around the map to watch the rest of the action. Outsiders can also join mid-game to spectate.
*   **Vault Economy Integration**: Seamlessly hooks into your server's economy via Vault to handle entry fees and prize payouts automatically.
*   **Isolated Game Worlds**: Each match takes place in a temporary, randomly generated world. This world is automatically unloaded and deleted after the game, keeping your main server worlds clean.
*   **Classic Shrinking Border**: The world border shrinks in phases, forcing players into a smaller area and encouraging combat.
*   **Dynamic Scoring & Prize Distribution**: Players earn points for kills and survival time. The total prize pool is distributed among all participants proportionally to their final score, rewarding skilled play.
*   **Engaging Game Phases**: Features a waiting lobby, a preparation phase, a PvP grace period, a BossBar for status updates (visible to spectators too), and custom sound effects to enhance the player experience.
*   **Admin Tools**: Administrators have access to a dedicated admin panel within the GUI to force-start games, providing better control for server events.

## 🎮 Gameplay Flow

1.  **Open the Menu**: A player opens the main menu using `/br menu`.
2.  **Game Creation**: The player clicks the "Create Game" button and is prompted to enter an entry fee via command (`/br create <amount>`). A server-wide broadcast invites others to join.
3.  **Joining the Lobby**: Other players can join by clicking the "Join Game" button in the menu or using `/br join`. A countdown begins once at least two players have joined.
4.  **Preparation Phase**: When the countdown ends, a new temporary world is created. All participants' data is saved by MiniGameManager, and they are teleported to random locations. Players are frozen for a brief period.
5.  **Game Start**: The freeze is lifted. A 1-minute grace period without PvP begins.
6.  **Combat, Airdrops, & Border Shrink**: After the grace period, PvP is enabled. At key intervals, airdrops containing valuable gear will spawn on the map, drawing players together. The world border also begins to shrink at preset intervals.
7.  **Elimination & Spectating**: When a player is eliminated, they enter Spectator Mode. They can freely fly around the game world to watch the remaining players battle it out. The killer is awarded points.
8.  **Game End**: The game concludes when only one player remains. A summary is broadcast, showing the final rankings, scores, and the prize money awarded to each participant.
9.  **Cleanup**: All remaining players (the winner and any spectators) are teleported back to their original locations with their data restored. The temporary game world is then deleted, and the plugin resets to an idle state.

## ⌨️ Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/br menu` or `/br` | **(Recommended)** Opens the main GUI menu for all actions. | `none` |
| `/br create <amount>` | Creates a new Battle Royale game with a set entry fee. | `none` |
| `/br join` | Joins the current game waiting in the lobby. | `none` |
| `/br leave` | Leaves the lobby (refunded), quits the match while alive (counts as elimination), or exits spectator mode. | `none` |
| `/br forcestart` | Force-starts the game from the admin panel or command. | `br.admin` |

## ⚙️ Installation & Dependencies

1.  **Dependencies**:
    *   [**MiniGameManager**](https://github.com/Roberta001/MiniGameManager) (Required) - The core service for player data management.
    *   [**Vault**](https://www.spigotmc.org/resources/vault.34315/) (Required)
    *   A Vault-compatible **economy plugin** (e.g., EssentialsX, CMI) (Required)
2.  **Installation Steps**:
    *   Ensure your server is running Spigot or a fork (e.g., Paper, Purpur).
    *   Place `MiniGameManager.jar`, `Vault.jar`, and `BattleRoyale.jar` into your server's `plugins` directory.
    *   Install an economy plugin if you don't have one.
    *   Restart your server.

---
# Battle Royale - Minecraft 大逃杀插件 (中文)

这是一个为 Spigot/PaperMC 服务器设计的经典大逃杀（吃鸡）小游戏插件。它提供了一个完整、自动化的游戏流程，玩家可以通过支付报名费加入一场“最后生还者”模式的战斗，根据游戏表现（击杀与存活）瓜分总奖池。

本插件基于 **MiniGameManager** 框架构建，以确保强大的玩家数据处理能力以及与其它使用该服务的小游戏的兼容性。

## ✨ 核心功能

*   **直观的图形菜单 (GUI)**: 玩家可通过 `/br menu` 打开简洁明了的图形界面，一键完成创建、加入或离开游戏等所有操作。
*   **基于核心框架构建**: 依赖 **MiniGameManager** 服务进行玩家数据管理，全自动且安全地保存和恢复玩家的背包、血量、经验等状态。
*   **跨游戏状态感知**: 如果玩家正在参与另一个使用 MiniGameManager 服务的小游戏，将无法加入大逃杀，有效防止冲突。
*   **动态空投系统**: 在比赛的关键时刻，会刷新装有渐进式高级战利品的空投，制造冲突热点，并为玩家提供翻盘机会。
*   **完整的旁观者模式**: 玩家被淘汰后会自动进入旁观者模式，可以在地图中自由飞行，观赏剩余的战斗。外部玩家也可以在游戏中途加入观战。
*   **经济系统集成 (Vault)**: 通过 Vault 插件无缝对接服务器经济系统，自动处理报名费和奖金发放。
*   **独立游戏世界**: 每场游戏都会在一个临时的、随机生成的世界中进行。游戏结束后，该世界会自动卸载并删除，保持服务器主世界的整洁。
*   **经典的缩圈机制**: 世界边界（毒圈）会分阶段缩小，迫使玩家向中心区域移动，增加战斗的激烈程度。
*   **动态积分与奖金分配**: 玩家通过击杀敌人和存活时间来获得积分。游戏结束后，总奖池会根据所有玩家的积分比例进行分配，奖励表现出色的玩家。
*   **丰富的游戏阶段与提示**: 包含等待大厅、准备阶段、PVP保护期、BossBar 状态提示（旁观者可见）、音效反馈等，提升了整体游戏体验。
*   **管理员工具**: 管理员在 GUI 中拥有专属的管理面板，可以强制开始游戏，方便在活动等场景下控制游戏进程。

## 🎮 玩法流程

1.  **打开菜单**: 玩家使用 `/br menu` 指令打开主菜单。
2.  **创建游戏**: 玩家点击“创建游戏”按钮，并根据提示使用指令 (`/br create <金额>`) 输入报名费。服务器会向所有玩家广播游戏邀请。
3.  **加入大厅**: 其他玩家在菜单中点击“加入游戏”或使用 `/br join` 指令加入游戏大厅。当人数达到2人时，游戏开始倒计时。
4.  **准备阶段**: 倒计时结束，插件创建新世界。MiniGameManager 保存所有参与者的数据，然后将他们传送到随机位置。玩家在短暂的准备时间内会被冻结。
5.  **游戏开始**: 准备时间结束后，玩家可以自由行动。游戏开始后有1分钟的PVP保护期。
6.  **战斗、空投与缩圈**: PVP开启后，玩家可以互相攻击。在关键时间点，装有珍贵物资的空投会降落在地图上，吸引玩家前往争夺。世界边界也会根据预设的时间点开始缩小。
7.  **淘汰与观战**: 玩家被淘汰后，将进入旁观者模式，可以自由地在游戏世界中飞行，观看其他玩家的战斗。击杀者会获得积分。
8.  **游戏结束**: 当场上仅剩最后一名玩家时，游戏结束。系统会公布所有玩家的积分排名和他们根据积分比例获得的奖金。
9.  **清理现场**: 所有仍在场内的玩家（包括获胜者和所有旁观者）的数据都将被恢复，并传送回原始位置。随后，临时游戏世界将被自动删除，插件重置为空闲状态。

## ⌨️ 指令与权限

| 指令 | 描述 | 权限 |
| :--- | :--- | :--- |
| `/br menu` 或 `/br` | **(推荐)** 打开图形主菜单，进行所有操作。 | `无` |
| `/br create <金额>` | 创建一场新的大逃杀游戏，并设置报名费。 | `无` |
| `/br join` | 加入当前正在等待的游戏。 | `无` |
| `/br leave` | 离开大厅（退款）、在存活时退出比赛（视为淘汰）、或退出旁观者模式。 | `无` |
| `/br forcestart` | 通过管理面板或指令强制开始游戏。 | `br.admin` |

## ⚙️ 安装与依赖

1.  **前置插件**:
    *   [**MiniGameManager**](https://github.com/Roberta001/MiniGameManager) (必需) - 负责玩家数据管理的核心服务。
    *   [**Vault**](https://www.spigotmc.org/resources/vault.34315/) (必需)
    *   一个 Vault 支持的**经济插件** (例如: EssentialsX, CMI) (必需)
2.  **安装步骤**:
    *   确保你的服务器是 Spigot 或其衍生版 (如 Paper, Purpur)。
    *   将 `MiniGameManager.jar`, `Vault.jar` 和 `BattleRoyale.jar` 文件放入服务器的 `plugins` 文件夹。
    *   如果你没有经济插件，请安装一个。
    *   重启服务器。