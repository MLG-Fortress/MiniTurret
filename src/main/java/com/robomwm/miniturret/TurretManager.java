package com.robomwm.miniturret;

import com.robomwm.customitemrecipes.CustomItemRecipes;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
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
                                if (!canTarget(turret, entity, false))
                                {
                                    turret.removeMetadata("MT_TARGET", plugin);
                                    cancel();
                                    return;
                                }

                                fire(turret, entity);
                            }
                        }.runTaskTimer(plugin, 0, 7L);
                        break;
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    private void fire(ArmorStand turret, LivingEntity target)
    {
        Vector vector = target.getEyeLocation().toVector().subtract(turret.getEyeLocation().add(0, 0.5, 0).toVector());
        turret.teleport(turret.getLocation().setDirection(vector));
        switch (((SkullMeta)turret.getHelmet().getItemMeta()).getOwningPlayer().getName())
        {
            case "carrqt":
                Arrow arrow = turret.getWorld().spawnArrow(turret.getEyeLocation().add(0, 0.5, 0).add(vector.clone().normalize()), vector, 2, 0);
                arrow.spigot().setDamage(0.5D);
                arrow.setGravity(false);
                break;
            case "Wabash_Warrior":
                Fireball fireball = turret.getWorld().spawn(turret.getLocation(), Fireball.class);
                fireball.setDirection(vector);
                break;
        }
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
            String uuidString = entity.getCustomName().split(":")[0];
            UUID uuid;
            try
            {
                uuid = UUID.fromString(uuidString);
            }
            catch (IllegalArgumentException e)
            {
                return;
            }
            idleTurrets.put((ArmorStand)entity, uuid);
            plugin.getLogger().info(entity.getCustomName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    private void onTurretSpawn(BlockPlaceEvent event)
    {
        if (!event.canBuild())
            return;

        switch (customItemRecipes.extractCustomID(event.getItemInHand().getItemMeta()))
        {
            case "test_turret":
                break;
            case "wabash_turret":
                break;
            default:
                return;
        }

        Location location = event.getBlock().getLocation().add(0.5, 0, 0.5);
        ArmorStand turret = event.getBlock().getWorld().spawn(location, ArmorStand.class);
        turret.setSmall(true);
        turret.setCustomName(event.getPlayer().getUniqueId().toString());
        turret.setHelmet(event.getItemInHand());
        event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);
        event.setCancelled(true);
        idleTurrets.put(turret, event.getPlayer().getUniqueId());
    }

    @EventHandler
    private void onTurretDestroyed(EntityDeathEvent event)
    {
        if (event.getEntityType() != EntityType.ARMOR_STAND)
            return;
        if (idleTurrets.containsKey(event.getEntity()))
            event.getDrops().clear();
    }

    @EventHandler(ignoreCancelled = true)
    private void onTurretInteract(PlayerInteractAtEntityEvent event)
    {
        if (event.getRightClicked().getType() != EntityType.ARMOR_STAND)
            return;
        event.setCancelled(idleTurrets.containsKey(event.getRightClicked()));
    }

    private boolean canTarget(LivingEntity turret, LivingEntity target, boolean includeInvisible)
    {
        if (target.getType() == EntityType.PLAYER && ((Player)target).getGameMode() != GameMode.SURVIVAL)
            return false;
        return (includeInvisible && target.hasPotionEffect(PotionEffectType.INVISIBILITY)) || !target.isDead() || target.isValid() || !turret.hasLineOfSight(target) || turret.getLocation().distanceSquared(target.getLocation()) > 36 * 36;
    }
}
