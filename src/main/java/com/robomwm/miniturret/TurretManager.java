package com.robomwm.miniturret;

import com.robomwm.customitemrecipes.CustomItemRecipes;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Created on 5/11/2018.
 *
 * So I heard using "Manager" is a bad name for stuff
 * but I have no better ideas
 * so yea
 *
 * @author RoboMWM
 */
public class TurretManager implements Listener
{
    CustomItemRecipes customItemRecipes;
    private JavaPlugin plugin;
    private Map<ArmorStand, UUID> idleTurrets = new HashMap<>();

    public TurretManager(JavaPlugin plugin)
    {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        customItemRecipes = (CustomItemRecipes)plugin.getServer().getPluginManager().getPlugin("CustomItemRecipes");

        //wew lad
        for (World world : plugin.getServer().getWorlds())
            for (Chunk chunk : world.getLoadedChunks())
                for (Entity entity : chunk.getEntities())
                    loadTurret(entity);

        //I briefly considered creating a runnable class for this
        //but then I was like, do I really wanna waste time passing stuff around
        //then I realized I had to cuz I'm modifying the very list I'm iterating
        //ugh
        //So I just use metadata and skip over
        //If performance is an issue I'll make it better
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                Iterator<ArmorStand> armorStandIterator = idleTurrets.keySet().iterator();
                while (armorStandIterator.hasNext())
                {
                    ArmorStand turret = armorStandIterator.next();

                    if (turret.isDead() || !turret.isValid())
                    {
                        armorStandIterator.remove();
                        continue;
                    }

                    if (turret.hasMetadata("MT_TARGET"))
                        continue;

                    //TODO: yea I gotta refactor this if I wanna use different types of turrets
                    for (LivingEntity entity : turret.getWorld().getNearbyLivingEntities(turret.getLocation(), 32))
                    {
                        if (entity == turret)
                            continue;
                        if (!turret.hasLineOfSight(entity))
                            continue;
                        if (entity.getUniqueId().equals(idleTurrets.get(turret)))
                            continue;

                        turret.setMetadata("MT_TARGET", new FixedMetadataValue(plugin, entity));

                        new BukkitRunnable()
                        {
                            @Override
                            public void run()
                            {
                                if (turret.isDead() || !turret.isValid())
                                {
                                    turret.removeMetadata("MT_TARGET", plugin);
                                    cancel();
                                    return;
                                }
                                if (entity.isDead() || !entity.isValid() || !turret.hasLineOfSight(entity) || turret.getLocation().distanceSquared(entity.getLocation()) > 32 * 32)
                                {
                                    turret.removeMetadata("MT_TARGET", plugin);
                                    cancel();
                                    return;
                                }
                                Vector vector = entity.getEyeLocation().toVector().subtract(turret.getEyeLocation().add(0, 0.5, 0).toVector());
                                Arrow arrow = turret.getWorld().spawnArrow(turret.getEyeLocation().add(0, 0.5, 0).add(vector.clone().normalize()), vector, 2, 0);
                                arrow.setKnockbackStrength(20);
                                arrow.spigot().setDamage(2D);
                                arrow.setGravity(false);
                                turret.teleport(turret.getLocation().setDirection(vector));
                            }
                        }.runTaskTimer(plugin, 5L, 5L);
                        break;
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event)
    {
        for (Entity entity : event.getChunk().getEntities())
        {
            loadTurret(entity);
        }
    }

    private void loadTurret(Entity entity)
    {
        if (entity.getType() != EntityType.ARMOR_STAND)
            return;
        if (entity.getCustomName() != null)
        {
            plugin.getLogger().info(entity.getCustomName());
            UUID uuid;
            try
            {
                uuid = UUID.fromString(entity.getCustomName());
            }
            catch (IllegalArgumentException e)
            {
                return;
            }
            idleTurrets.put((ArmorStand)entity, uuid);
            plugin.getLogger().info(entity.getCustomName());
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onTurretSpawn(BlockPlaceEvent event)
    {
        if (!event.canBuild())
            return;
        if (!customItemRecipes.isItem("test_turret", event.getItemInHand()))
            return;
        Location location = event.getBlock().getLocation().add(0.5, 0, 0.5);
        ArmorStand turret = event.getBlock().getWorld().spawn(location, ArmorStand.class);
        turret.setSmall(true);
        turret.setCustomName(event.getPlayer().getUniqueId().toString());
        turret.setHelmet(event.getItemInHand());
        event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);
        event.setCancelled(true);
        idleTurrets.put(turret, event.getPlayer().getUniqueId());
    }
}
