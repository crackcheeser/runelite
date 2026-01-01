/*
 * Copyright (c) 2024, OpenAI
 * All rights reserved.
 */
package net.runelite.client.plugins.aoewarnings;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.time.Instant;
import java.util.Collection;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class AoeWarningOverlay extends Overlay
{
    private static final int MAX_DRAW_DISTANCE = 32;

    private final Client client;
    private final AoeWarningConfig config;
    private final AoeWarningPlugin plugin;

    @Inject
    private AoeWarningOverlay(Client client, AoeWarningConfig config, AoeWarningPlugin plugin)
    {
        this.client = client;
        this.config = config;
        this.plugin = plugin;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.enabled())
        {
            return null;
        }

        Collection<AoeProjectile> active = plugin.getProjectiles();
        if (active.isEmpty())
        {
            return null;
        }

        Stroke stroke = new BasicStroke((float) config.borderWidth());
        Color fill = new Color(config.tileColor().getRed(), config.tileColor().getGreen(), config.tileColor().getBlue(), config.fillOpacity());

        WorldView playerWv = client.getLocalPlayer().getWorldView();
        WorldPoint playerLoc = client.getLocalPlayer().getWorldLocation();

        for (AoeProjectile aoe : active)
        {
            if (Instant.now().isAfter(aoe.getExpiry()))
            {
                continue;
            }

            WorldPoint target = aoe.getTarget();
            WorldView wv = client.findWorldViewFromWorldPoint(target);
            if (wv == null)
            {
                continue;
            }

            if (playerWv.isTopLevel())
            {
                if (target.distanceTo(playerLoc) >= MAX_DRAW_DISTANCE)
                {
                    continue;
                }
            }

            LocalPoint lp = LocalPoint.fromWorld(wv, target);
            if (lp == null)
            {
                continue;
            }

            Polygon poly = Perspective.getCanvasTileAreaPoly(client, lp, aoe.getInfo().getAoeSize());
            if (poly != null)
            {
                Color outline = config.showOutline() ? config.tileColor() : fill;
                OverlayUtil.renderPolygon(graphics, poly, outline, fill, stroke);
            }
        }

        return null;
    }
}
