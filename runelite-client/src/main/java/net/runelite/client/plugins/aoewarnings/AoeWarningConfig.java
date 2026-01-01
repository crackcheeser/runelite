/*
 * Copyright (c) 2024, OpenAI
 * All rights reserved.
 */
package net.runelite.client.plugins.aoewarnings;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(AoeWarningConfig.GROUP)
public interface AoeWarningConfig extends Config
{
    String GROUP = "aoewarnings";

    @ConfigItem(
        keyName = "enabled",
        name = "Enable plugin",
        description = "Toggle AoE projectile warnings"
    )
    default boolean enabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showOutline",
        name = "Draw outline",
        description = "Draw an outline around AoE tiles"
    )
    default boolean showOutline()
    {
        return true;
    }

    @Range(max = 255)
    @ConfigItem(
        keyName = "fillOpacity",
        name = "Fill opacity",
        description = "Opacity of the AoE tile fill"
    )
    default int fillOpacity()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "borderWidth",
        name = "Border width",
        description = "Width of the AoE tile border"
    )
    default double borderWidth()
    {
        return 2.0d;
    }

    @Alpha
    @ConfigItem(
        keyName = "tileColor",
        name = "Tile color",
        description = "Color used for AoE tiles"
    )
    default Color tileColor()
    {
        return new Color(255, 0, 0, 180);
    }
}
