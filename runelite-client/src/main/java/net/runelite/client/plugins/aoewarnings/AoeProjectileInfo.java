/*
 * Copyright (c) 2024, OpenAI
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.aoewarnings;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.SpotanimID;

/**
 * Minimal mapping of AoE-style projectile travel spotanims to splash size and lifetime.
 * Add more entries here as you discover new projectile ids; the overlay and plugin
 * consume this enum dynamically via {@link #getById(int)}.
 */
public enum AoeProjectileInfo
{
    VORKATH_BOMB(SpotanimID.VORKATH_AREA_TRAVEL, Duration.ofMillis(2400), 3),
    VORKATH_ACID(SpotanimID.VORKATH_ACID_TRAVEL, Duration.ofMillis(1800), 1),
    VORKATH_SPAWN(SpotanimID.VORKATH_SPAWN_TRAVEL, Duration.ofMillis(3000), 1),
    GALVEK_BOMB(SpotanimID.GALVEK_AREA_TRAVEL, Duration.ofMillis(2400), 3),
    GALVEK_EARTH(SpotanimID.GALVEK_EARTH_PROJ, Duration.ofMillis(2400), 3);

    private static final Map<Integer, AoeProjectileInfo> MAP = new HashMap<>();

    static
    {
        for (AoeProjectileInfo info : values())
        {
            MAP.put(info.id, info);
        }
    }

    private final int id;
    private final Duration lifeTime;
    private final int aoeSize;

    AoeProjectileInfo(int id, Duration lifeTime, int aoeSize)
    {
        this.id = id;
        this.lifeTime = lifeTime;
        this.aoeSize = aoeSize;
    }

    public int getId()
    {
        return id;
    }

    public Duration getLifeTime()
    {
        return lifeTime;
    }

    public int getAoeSize()
    {
        return aoeSize;
    }

    public static AoeProjectileInfo getById(int id)
    {
        return MAP.get(id);
    }
}
