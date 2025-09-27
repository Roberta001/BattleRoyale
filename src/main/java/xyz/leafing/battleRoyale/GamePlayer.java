package xyz.leafing.battleRoyale;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 封装了参与游戏玩家的所有数据。
 */
public class GamePlayer {
    private final UUID uuid;
    private PlayerState state;
    private final Location originalLocation;
    private int kills = 0;
    private int score = 0;

    public GamePlayer(UUID uuid, Location originalLocation) {
        this.uuid = uuid;
        this.originalLocation = originalLocation;
        this.state = PlayerState.PARTICIPANT; // 初始状态为参与者
    }

    // --- Getters ---
    public UUID getUuid() { return uuid; }
    public PlayerState getState() { return state; }
    public Location getOriginalLocation() { return originalLocation; }
    public int getKills() { return kills; }
    public int getScore() { return score; }

    // --- Setters ---
    public void setState(PlayerState state) { this.state = state; }
    public void setScore(int score) { this.score = score; }

    // --- Modifiers ---
    public void incrementKills() { this.kills++; }
    public void addScore(int amount) { this.score += amount; }
}