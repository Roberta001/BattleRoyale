package xyz.leafing.battleRoyale;

import org.bukkit.Location;
import java.util.UUID;

public class GamePlayer {
    private final UUID uuid;
    private final Location originalLocation;
    private boolean isAlive = true; // 状态简化为布尔值
    private int kills = 0;
    private int score = 0;

    public GamePlayer(UUID uuid, Location originalLocation) {
        this.uuid = uuid;
        this.originalLocation = originalLocation;
    }

    // --- Getters ---
    public UUID getUuid() { return uuid; }
    public Location getOriginalLocation() { return originalLocation; }
    public boolean isAlive() { return isAlive; }
    public int getKills() { return kills; }
    public int getScore() { return score; }

    // --- Setters ---
    public void setAlive(boolean alive) { this.isAlive = alive; }

    // --- Modifiers ---
    public void incrementKills() { this.kills++; }
    public void addScore(int amount) { this.score += amount; }
}