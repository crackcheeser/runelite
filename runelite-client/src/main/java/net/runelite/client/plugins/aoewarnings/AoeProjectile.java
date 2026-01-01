/*
 * Copyright (c) 2024, OpenAI
 * All rights reserved.
 */
package net.runelite.client.plugins.aoewarnings;

import java.time.Instant;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
class AoeProjectile
{
    Instant createdAt;
    WorldPoint target;
    AoeProjectileInfo info;

    Instant getExpiry()
    {
        return createdAt.plus(info.getLifeTime());
    }
}
