package xyz.leafing.battleRoyale;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * A utility class to handle playing sounds for specific game events.
 */
public class SoundManager {

    public enum GameSound {
        JOIN_LOBBY,
        COUNTDOWN_TICK,
        GAME_START,
        PVP_ENABLE,
        BORDER_SHRINK_WARN,
        KILL_PLAYER,
        PLAYER_DEATH,
        GAME_WIN, // For the top player on the leaderboard
        PREPARATION_PHASE_CHANGE
    }

    public static void playSound(Player player, GameSound gameSound) {
        if (player == null || !player.isOnline()) {
            return;
        }

        switch (gameSound) {
            case JOIN_LOBBY:
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                break;
            case COUNTDOWN_TICK:
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                break;
            case GAME_START:
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                break;
            case PVP_ENABLE:
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.0f);
                break;
            case BORDER_SHRINK_WARN:
                player.playSound(player.getLocation(), Sound.ENTITY_GHAST_WARN, 0.8f, 0.8f);
                break;
            case KILL_PLAYER:
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                break;
            case PLAYER_DEATH:
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1.0f, 1.0f);
                break;
            case GAME_WIN:
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                break;
            case PREPARATION_PHASE_CHANGE:
                // 选择一个低沉的音效，例如大鼓声或者末影箱打开的声音
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.7f);
                break;
        }
    }

    /**
     * Plays a sound for a collection of players.
     * @param players The players to play the sound for.
     * @param gameSound The sound to play.
     */
    public static void broadcastSound(Iterable<Player> players, GameSound gameSound) {
        for (Player player : players) {
            playSound(player, gameSound);
        }
    }
}