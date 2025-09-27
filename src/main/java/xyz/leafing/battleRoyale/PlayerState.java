package xyz.leafing.battleRoyale;

/**
 * 代表一个玩家在游戏中所处的状态。
 */
public enum PlayerState {
    /**
     * 在大厅中，等待游戏开始。
     */
    PARTICIPANT,
    /**
     * 在游戏中，并且存活。
     */
    ALIVE,
    /**
     * 在游戏中，但已被淘汰，作为旁观者。
     */
    SPECTATOR
}