/*
 * Copyright (c) 2024, OpenAI
 * All rights reserved.
 */
package net.runelite.client.plugins.aoewarnings;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Projectile;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "AoE Projectile Warnings",
    description = "Shows ground markers where AoE projectiles will land",
    tags = {"aoe", "projectile", "warning", "vorkath", "galvek"}
)
public class AoeWarningPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AoeWarningOverlay overlay;

    @Inject
    private AoeWarningConfig config;

    private final List<AoeProjectile> trackedProjectiles = new ArrayList<>();

    @Provides
    AoeWarningConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AoeWarningConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        trackedProjectiles.clear();
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        trackedProjectiles.clear();
    }

    List<AoeProjectile> getProjectiles()
    {
        return trackedProjectiles;
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event)
    {
        if (!config.enabled())
        {
            return;
        }

        Projectile projectile = event.getProjectile();

        // Ignore targeted projectiles (non-AoE)
        if (projectile.getInteracting() != null)
        {
            return;
        }

        AoeProjectileInfo info = AoeProjectileInfo.getById(projectile.getId());
        if (info == null)
        {
            return;
        }

        var localPoint = event.getPosition();
        if (localPoint == null)
        {
            return;
        }

        var wv = client.getWorldView(localPoint.getWorldView());
        if (wv == null)
        {
            return;
        }

        var worldPoint = net.runelite.api.coords.WorldPoint.fromLocal(wv, localPoint.getX(), localPoint.getY(), wv.getPlane());
        trackedProjectiles.add(new AoeProjectile(Instant.now(), worldPoint, info));
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (trackedProjectiles.isEmpty())
        {
            return;
        }

        Instant now = Instant.now();
        Iterator<AoeProjectile> it = trackedProjectiles.iterator();
        while (it.hasNext())
        {
            if (now.isAfter(it.next().getExpiry()))
            {
                it.remove();
            }
        }
    }
}
