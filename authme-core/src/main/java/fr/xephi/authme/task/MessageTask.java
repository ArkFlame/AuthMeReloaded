package fr.xephi.authme.task;

import org.bukkit.entity.Player;

/**
 * Message shown to a player in a regular interval as long as he is not logged in.
 *
 * <p>Scheduling is owned by {@link fr.xephi.authme.service.BukkitService}, which dispatches
 * through the platform's scheduler provider. This task is a plain {@link Runnable} and does
 * not hold any Bukkit scheduler state itself.
 */
public class MessageTask implements Runnable {

    private final Player player;
    private final String[] message;
    private boolean isMuted;

    /**
     * Constructor.
     *
     * @param player the player to show the message to
     * @param lines the message lines
     */
    public MessageTask(Player player, String[] lines) {
        this.player = player;
        this.message = lines;
    }

    public void setMuted(boolean isMuted) {
        this.isMuted = isMuted;
    }

    @Override
    public void run() {
        if (!isMuted) {
            player.sendMessage(message);
        }
    }

    /**
     * Cancels this message task. Kept for the legacy single-argument
     * {@code LimboPlayer#setMessageTask(MessageTask)} path; tasks scheduled through
     * {@link fr.xephi.authme.service.BukkitService} are cancelled by their
     * {@link fr.xephi.authme.service.CancellableTask} handle instead.
     */
    public void cancel() {
        // No scheduler state is held here; the scheduled task handle owns cancellation.
    }
}
