package fr.xephi.authme.platform;

import fr.xephi.authme.listener.PaperChatListener;
import fr.xephi.authme.listener.PaperPlayerSpawnLocationListener;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Platform adapter implementation for PaperMC 1.21.11+.
 * Uses Paper's async teleport API for non-blocking player teleportation,
 * the Adventure API for text components, and the Paper dialog API for
 * graphical login/register dialogs (available since 1.21.11).
 * On older Paper versions the dialog feature is gracefully disabled.
 */
public class PaperPlatformAdapter extends AbstractPaperPlatformAdapter {

    @Override
    public String getPlatformName() {
        return "paper-1.21";
    }

    @Override
    public String getCompatibilityError() {
        return getPaperCompatibilityError("This AuthMe Paper build requires the Paper 1.21.11+ API.");
    }

    @Override
    protected List<Class<? extends Listener>> getPlatformListeners() {
        return getPaperListeners(PaperChatListener.class, PaperPlayerSpawnLocationListener.class);
    }
}
