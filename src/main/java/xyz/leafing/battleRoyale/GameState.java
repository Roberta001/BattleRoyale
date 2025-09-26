package xyz.leafing.battleRoyale;

public enum GameState {
    IDLE,       // 空闲，无游戏
    LOBBY,      // 大厅等待玩家
    PREPARING,  // 准备中（创建世界、传送玩家）
    INGAME,     // 游戏中
    CLEANUP     // 游戏结束后的清理阶段
}