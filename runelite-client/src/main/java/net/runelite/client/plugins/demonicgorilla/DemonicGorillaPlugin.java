/*
 * Copyright (c) 2024, RuneLite
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
package net.runelite.client.plugins.demonicgorilla;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Demonic Gorillas",
    description = "Track demonic gorilla attack styles and upcoming switches"
)
public class DemonicGorillaPlugin extends Plugin
{
    /*
     * Manual test checklist:
     * - Gorillas spawn/detect correctly when entering the area.
     * - Prayer switch animation handling delays next attack appropriately.
     * - Projectile sources register for magic/ranged and tie back to the originating gorilla.
     * - Boulder AoE positions log in recentBoulders for inference.
     * - Overlay draws above gorillas with possible styles and arc countdown.
     * - Attack-style narrowing follows prayers/tick progression.
     * - Flinch/damage scenarios delay attacks and update counters as expected.
     */
    private static final Set<Integer> GORILLA_IDS = ImmutableSet.of(
        // Stated compatibility set 7144-7149 plus 7152; current API names use MM2 prefix.
        NpcID.MM2_DEMON_GORILLA_1_MELEE,  // 7144
        NpcID.MM2_DEMON_GORILLA_1_RANGED, // 7145
        NpcID.MM2_DEMON_GORILLA_1_MAGIC,  // 7146
        NpcID.MM2_DEMON_GORILLA_2_MELEE,  // 7147
        NpcID.MM2_DEMON_GORILLA_2_RANGED, // 7148
        NpcID.MM2_DEMON_GORILLA_2_MAGIC,  // 7149
        NpcID.MM2_DEMON_GORILLA_NONCOMBAT // 7152
    );

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DemonicGorillaOverlay overlay;

    @Inject
    private ClientThread clientThread;

    @Inject
    private DemonicGorillaConfig config;

    @Getter
    private final Map<NPC, DemonicGorilla> gorillas = new HashMap<>();

    private final List<WorldPoint> recentBoulders = new ArrayList<>();

    private final List<PendingGorillaAttack> pendingAttacks = new ArrayList<>();

    private final Map<Player, MemorizedPlayer> memorizedPlayers = new HashMap<>();

    @Provides
    DemonicGorillaConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DemonicGorillaConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        clientThread.invoke(this::resetAll);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        clearAll();
    }

    private void clearAll()
    {
        gorillas.clear();
        recentBoulders.clear();
        pendingAttacks.clear();
        memorizedPlayers.clear();
    }

    private void resetAll()
    {
        clearAll();
        resetPlayers();
        resetGorillas();
    }

    private void resetPlayers()
    {
        for (Player player : client.getPlayers())
        {
            memorizedPlayers.put(player, new MemorizedPlayer(player));
        }
    }

    private void resetGorillas()
    {
        for (NPC npc : client.getNpcs())
        {
            if (isGorilla(npc.getId()))
            {
                gorillas.put(npc, new DemonicGorilla(npc));
            }
        }
    }

    private boolean isGorilla(int npcId)
    {
        return GORILLA_IDS.contains(npcId);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.LOGGING_IN || state == GameState.CONNECTION_LOST)
        {
            resetAll();
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        NPC npc = event.getNpc();
        if (isGorilla(npc.getId()))
        {
            if (gorillas.isEmpty())
            {
                resetPlayers();
            }
            gorillas.put(npc, new DemonicGorilla(npc));
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        if (gorillas.remove(event.getNpc()) != null && gorillas.isEmpty())
        {
            clearAll();
        }
    }

    @Subscribe
    public void onPlayerSpawned(PlayerSpawned event)
    {
        if (gorillas.isEmpty())
        {
            return;
        }
        Player player = event.getPlayer();
        memorizedPlayers.put(player, new MemorizedPlayer(player));
    }

    @Subscribe
    public void onPlayerDespawned(PlayerDespawned event)
    {
        if (gorillas.isEmpty())
        {
            return;
        }
        memorizedPlayers.remove(event.getPlayer());
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (gorillas.isEmpty())
        {
            return;
        }

        if (event.getActor() instanceof Player)
        {
            Player player = (Player) event.getActor();
            MemorizedPlayer mp = memorizedPlayers.get(player);
            if (mp != null)
            {
                mp.getRecentHitsplats().add(event.getHitsplat());
            }
        }
        else if (event.getActor() instanceof NPC)
        {
            DemonicGorilla gorilla = gorillas.get(event.getActor());
            if (gorilla != null)
            {
                int hitsplatType = event.getHitsplat().getHitsplatType();
                if (isDamageOrBlockHitsplat(hitsplatType))
                {
                    gorilla.setTakenDamageRecently(true);
                }
            }
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event)
    {
        if (gorillas.isEmpty())
        {
            return;
        }

        Projectile projectile = event.getProjectile();
        if (projectile == null || event.getPosition() == null)
        {
            return;
        }

        // TODO: Replace placeholder projectile ID checks with real IDs when available.
        int projectileId = projectile.getId();
        if (projectileId == DemonicGorillaProjectileIds.BOULDER)
        {
            recentBoulders.add(WorldPoint.fromLocal(client, event.getPosition()));
            return;
        }

        if (projectileId != DemonicGorillaProjectileIds.MAGIC && projectileId != DemonicGorillaProjectileIds.RANGED)
        {
            return;
        }

        WorldPoint source = WorldPoint.fromLocal(client, projectile.getX1(), projectile.getY1(), client.getPlane());
        if (source == null)
        {
            return;
        }

        for (DemonicGorilla gorilla : gorillas.values())
        {
            if (gorilla.getNpc().getWorldLocation().distanceTo(source) == 0)
            {
                gorilla.setRecentProjectileId(projectileId);
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (gorillas.isEmpty())
        {
            return;
        }

        checkGorillaAttacks();
        checkPendingAttacks();
        updatePlayers();
        recentBoulders.clear();
    }

    private void checkGorillaAttacks()
    {
        // TODO: Implement full attack inference (animations, projectiles, melee pathing) using legacy logic.
        // Stub structure keeps per-tick bookkeeping so legacy logic can be dropped in unchanged.
        for (DemonicGorilla gorilla : gorillas.values())
        {
            int animationId = gorilla.getNpc().getAnimation();

            if (animationId == AnimationID.DEMONIC_GORILLA_PUNCH)
            {
                onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.MELEE);
            }
            else if (animationId == AnimationID.DEMONIC_GORILLA_MAGIC)
            {
                onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.MAGIC);
            }
            else if (animationId == AnimationID.DEMONIC_GORILLA_RANGE)
            {
                onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.RANGED);
            }

            // TODO: Add prayer switch / AoE animation handling and flinch delays.

            gorilla.setLastTickAnimation(animationId);
            gorilla.setLastWorldArea(gorilla.getNpc().getWorldArea());
            gorilla.setLastTickInteracting(gorilla.getNpc().getInteracting());
            gorilla.setTakenDamageRecently(false);
            gorilla.setRecentProjectileId(-1);
            gorilla.setChangedAttackStyleLastTick(gorilla.isChangedAttackStyleThisTick());
            gorilla.setChangedAttackStyleThisTick(false);
            gorilla.setLastTickOverheadIcon(gorilla.getOverheadIcon());
        }
    }

    private void onGorillaAttack(DemonicGorilla gorilla, DemonicGorilla.AttackStyle style)
    {
        gorilla.setInitiatedCombat(true);

        Player target = (Player) gorilla.getNpc().getInteracting();
        if (target != null)
        {
            gorilla.setNextAttackTick(client.getTickCount() + DemonicGorilla.ATTACK_RATE);
            gorilla.setAttacksUntilSwitch(Math.max(0, gorilla.getAttacksUntilSwitch() - 1));
            gorilla.setNextPosibleAttackStyles(List.of(style));

            // If prayer was incorrect, defer until damage lands; placeholder pending queue mirrors legacy flow.
            HeadIcon protectedStyle = getProtectedStyle(target);
            if (protectedStyle != null && !style.matchesHeadIcon(protectedStyle))
            {
                pendingAttacks.add(new PendingGorillaAttack(gorilla, style, target, client.getTickCount() + DemonicGorilla.ATTACK_RATE));
            }
        }
    }

    private void checkPendingAttacks()
    {
        Iterator<PendingGorillaAttack> it = pendingAttacks.iterator();
        int tick = client.getTickCount();
        while (it.hasNext())
        {
            PendingGorillaAttack attack = it.next();
            if (tick < attack.getFinishesOnTick())
            {
                continue;
            }

            DemonicGorilla gorilla = attack.getAttacker();
            MemorizedPlayer target = memorizedPlayers.get(attack.getTarget());
            if (target == null || target.getRecentHitsplats().stream().anyMatch(h -> h.getHitsplatType() == HitsplatID.BLOCK_ME || h.getHitsplatType() == HitsplatID.BLOCK_OTHER))
            {
                gorilla.setAttacksUntilSwitch(Math.max(0, gorilla.getAttacksUntilSwitch() - 1));
            }
            it.remove();
        }
    }

    private boolean isDamageOrBlockHitsplat(int hitsplatType)
    {
        return hitsplatType == HitsplatID.BLOCK_ME
            || hitsplatType == HitsplatID.BLOCK_OTHER
            || hitsplatType == HitsplatID.DAMAGE_ME
            || hitsplatType == HitsplatID.DAMAGE_OTHER
            || hitsplatType == HitsplatID.DAMAGE_ME_CYAN
            || hitsplatType == HitsplatID.DAMAGE_OTHER_CYAN
            || hitsplatType == HitsplatID.DAMAGE_ME_ORANGE
            || hitsplatType == HitsplatID.DAMAGE_OTHER_ORANGE
            || hitsplatType == HitsplatID.DAMAGE_ME_YELLOW
            || hitsplatType == HitsplatID.DAMAGE_OTHER_YELLOW
            || hitsplatType == HitsplatID.DAMAGE_ME_WHITE
            || hitsplatType == HitsplatID.DAMAGE_OTHER_WHITE
            || hitsplatType == HitsplatID.DAMAGE_MAX_ME
            || hitsplatType == HitsplatID.DAMAGE_MAX_ME_CYAN
            || hitsplatType == HitsplatID.DAMAGE_MAX_ME_ORANGE
            || hitsplatType == HitsplatID.DAMAGE_MAX_ME_YELLOW
            || hitsplatType == HitsplatID.DAMAGE_MAX_ME_WHITE
            || hitsplatType == HitsplatID.DAMAGE_ME_POISE
            || hitsplatType == HitsplatID.DAMAGE_OTHER_POISE
            || hitsplatType == HitsplatID.DAMAGE_MAX_ME_POISE;
    }

    private void updatePlayers()
    {
        for (MemorizedPlayer mp : memorizedPlayers.values())
        {
            mp.setLastWorldArea(mp.getPlayer().getWorldArea());
            mp.getRecentHitsplats().clear();
        }
    }

    private HeadIcon getProtectedStyle(Player player)
    {
        return player.getOverheadIcon();
    }
}
