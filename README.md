# Battle Royale - A Minecraft Minigame Plugin (English)

This is a classic Battle Royale ("last man standing") minigame plugin designed for Spigot/PaperMC servers. It provides a complete and automated gameplay loop where players can pay an entry fee to join a deathmatch, competing to win a share of the total prize pool based on their in-game performance (kills and survival time).

## ✨ Core Features

*   **Dynamic Game Creation**: Any player can start a game and set a custom entry fee.
*   **Vault Economy Integration**: Seamlessly hooks into your server's economy via Vault to handle entry fees and prize payouts automatically.
*   **Fully Automated Player Data Management**: Automatically saves a player's inventory, health, experience, potion effects, and more upon joining a game. This data is perfectly restored after the match ends or if they leave, ensuring the safety of their items in the main world.
*   **Robust Data Recovery**: If the server crashes or a player disconnects unexpectedly during a game, the plugin will detect and restore their data upon their next login, preventing data loss.
*   **Isolated Game Worlds**: Each match takes place in a temporary, randomly generated world. This world is automatically unloaded and deleted after the game, keeping your main server worlds clean.
*   **Classic Shrinking Border**: As the game progresses, the world border (the "storm" or "gas") shrinks in phases, forcing players into a smaller area and encouraging combat.
*   **Dynamic Scoring & Prize Distribution**: Players earn points for kills and for their survival time. At the end of the game, the total prize pool is distributed among all participants proportionally to their final score. This rewards skilled play, even for those who don't secure first place.
*   **Engaging Game Phases**: Features a waiting lobby, a preparation phase, a PvP grace period, a BossBar for status updates, and custom sound effects to enhance the player experience.
*   **Anti-Escape Mechanics**: Automatically blocks commands like `/spawn`, `/home`, and `/tpa` during a match to prevent players from unfairly leaving the combat zone.
*   **Admin Tools**: Administrators have permission to force-start a game in the lobby, providing better control for server events.

## 🎮 Gameplay Flow

1.  **Game Creation**: A player initiates a game using `/br create <amount>`. A server-wide broadcast invites others to join.
2.  **Joining the Lobby**: Players can join by clicking the message or using `/br join`. A countdown begins once at least two players have joined.
3.  **Preparation Phase**: When the countdown ends, a new temporary world is created. All participants' data is saved, and they are teleported to random locations within the new world. Players are frozen for a brief period.
4.  **Game Start**: The freeze is lifted, and players can move and gather resources. A 1-minute grace period without PvP begins.
5.  **Combat & Border Shrink**: After the grace period, PvP is enabled. The world border begins to shrink at preset intervals, damaging any players caught outside of it.
6.  **Elimination & Payout**: When a player is killed or disconnects, they are eliminated. Their original data is restored, and they are teleported back to their original location. The killer is awarded points.
7.  **Game End**: The game concludes when only one player remains. A summary is broadcast, showing the final rankings, scores, and the prize money awarded to each participant.
8.  **Cleanup**: The winner's data is restored, the temporary game world is deleted, and the plugin resets to an idle state, ready for the next game.

## ⌨️ Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/br create <amount>` | Creates a new Battle Royale game with a set entry fee. | `none` |
| `/br join` | Joins the current game waiting in the lobby. | `none` |
| `/br leave` | Leaves the game lobby (entry fee is refunded). | `none` |
| `/br forcestart` | Force-starts the game in the lobby (requires min. 2 players). | `br.admin` |

## ⚙️ Installation & Dependencies

1.  **Dependencies**:
    *   [**Vault**](https://www.spigotmc.org/resources/vault.34315/) (Required)
    *   A Vault-compatible **economy plugin** (e.g., EssentialsX, CMI, etc.) (Required)
2.  **Installation Steps**:
    *   Ensure your server is running Spigot or a fork (e.g., Paper, Purpur).
    *   Place the downloaded `BattleRoyale.jar` file into your server's `plugins` directory.
    *   Restart your server. The plugin will load and generate its necessary files.
---
# Battle Royale - Minecraft 大逃杀插件 (中文)

这是一个为 Spigot/PaperMC 服务器设计的经典大逃杀（吃鸡）小游戏插件。它提供了一个完整、自动化的游戏流程，玩家可以通过支付报名费加入一场“最后生还者”模式的战斗，根据游戏表现（击杀与存活）瓜分总奖池。

## ✨ 核心功能

*   **动态游戏创建**: 任何玩家都可以发起一场游戏，并自定义报名费用。
*   **经济系统集成 (Vault)**: 通过 Vault 插件无缝对接服务器经济系统，自动处理报名费和奖金发放。
*   **全自动玩家数据管理**: 在玩家加入游戏时自动保存其背包、血量、经验、药水效果等数据，并在游戏结束后或中途退出时完美恢复，确保玩家在主世界的物品安全。
*   **强大的数据恢复机制**: 即使服务器在游戏中断电或崩溃，玩家重新登录时插件也会检测到未恢复的数据并自动为其恢复，防止数据丢失。
*   **独立游戏世界**: 每场游戏都会在一个临时的、随机生成的世界中进行。游戏结束后，该世界会自动卸载并删除，保持服务器主世界的整洁。
*   **经典的缩圈机制**: 随着游戏进行，世界边界（毒圈）会分阶段缩小，迫使玩家向中心区域移动，增加战斗的激烈程度。
*   **动态积分与奖金分配**: 玩家通过击杀敌人和存活时间来获得积分。游戏结束后，总奖池会根据所有玩家的积分比例进行分配，即使没有获得第一名也能根据表现获得奖励。
*   **丰富的游戏阶段与提示**: 包含等待大厅、准备阶段、PVP保护期、BossBar 状态提示、音效反馈等，提升了整体游戏体验。
*   **反作弊/逃跑机制**: 游戏期间会自动禁用 `/spawn`, `/home`, `/tpa` 等传送指令，防止玩家利用指令逃离战场。
*   **管理员工具**: 管理员拥有强制开始游戏的权限，方便在活动等场景下控制游戏进程。

## 🎮 玩法流程

1.  **创建游戏**: 一名玩家使用 `/br create <金额>` 指令发起一场游戏。服务器会向所有玩家广播游戏邀请。
2.  **加入大厅**: 其他玩家点击聊天框中的提示或使用 `/br join` 指令加入游戏大厅。当人数达到2人时，游戏开始倒计时。
3.  **准备阶段**: 倒计时结束，插件会创建一个新的临时世界。所有参与者的数据被保存，然后被传送到新世界的随机位置。玩家在短暂的准备时间内会被冻结，无法移动。
4.  **游戏开始**: 准备时间结束后，玩家可以自由移动和收集物资。游戏开始后有1分钟的PVP保护期。
5.  **战斗与缩圈**: PVP开启后，玩家可以互相攻击。世界边界会根据预设的时间点开始缩小，停留在边界外的玩家会持续受到伤害。
6.  **淘汰与结算**: 玩家被击杀或中途掉线后即被淘汰，其游戏前的数据会被恢复，并传送回原始位置。击杀者会获得积分。
7.  **游戏结束**: 当场上仅剩最后一名玩家时，游戏结束。系统会公布所有玩家的积分排名和他们根据积分比例获得的奖金。
8.  **清理现场**: 获胜者的数据被恢复，临时游戏世界被自动删除，插件重置为空闲状态，等待下一场游戏的开始。

## ⌨️ 指令与权限

| 指令 | 描述 | 权限 |
| :--- | :--- | :--- |
| `/br create <金额>` | 创建一场新的大逃杀游戏，并设置报名费。 | `无` |
| `/br join` | 加入当前正在等待的游戏。 | `无` |
| `/br leave` | 离开游戏大厅（会退还报名费）。 | `无` |
| `/br forcestart` | 强制开始正在等待的游戏（需要至少2名玩家）。 | `br.admin` |

## ⚙️ 安装与依赖

1.  **前置插件**:
    *   [**Vault**](https://www.spigotmc.org/resources/vault.34315/) (必需)
    *   一个 Vault 支持的**经济插件** (例如: EssentialsX, CMI, etc.) (必需)
2.  **安装步骤**:
    *   确保你的服务器是 Spigot 或其衍生版 (如 Paper, Purpur)。
    *   将下载的 `BattleRoyale.jar` 文件放入服务器的 `plugins` 文件夹。
    *   重启服务器。插件会自动加载并生成所需文件。
