package com.robomwm.miniturret;

import com.robomwm.customitemrecipes.CustomItemRecipes;
import com.robomwm.miniturret.turret.Turret;
import com.robomwm.miniturret.turret.TurretFactory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
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
    private Map<LivingEntity, Turret> turrets = new HashMap<>();

    public TurretManager(JavaPlugin plugin)
    {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        customItemRecipes = (CustomItemRecipes)plugin.getServer().getPluginManager().getPlugin("CustomItemRecipes");

        for (World world : plugin.getServer().getWorlds())
            for (Entity entity : world.getEntities())
                if (entity instanceof LivingEntity)
                    loadTurret((LivingEntity)entity);
    }


    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event)
    {
        for (Entity entity : event.getChunk().getEntities())
        {
            if (entity instanceof LivingEntity)
                loadTurret((LivingEntity)entity);
        }
    }

    private void loadTurret(LivingEntity entity)
    {
        if (turrets.containsKey(entity))
            return;

        if (entity.getType() != EntityType.ARMOR_STAND)
            return;

        ArmorStand armorStand = (ArmorStand)entity;
        UUID uuid = null;

        if (entity.getCustomName() != null)
        {
            String uuidString = entity.getCustomName().split(":")[0];
            try
            {
                uuid = UUID.fromString(uuidString);
            }
            catch (IllegalArgumentException e)
            {
                return;
            }
        }

        if (uuid == null)
            return;

        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(uuid);

        activateTurret(armorStand, owner);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    private void onTurretSpawn(BlockPlaceEvent event)
    {
        if (!event.canBuild())
            return;

        switch (event.getItemInHand().getType())
        {
            case PLAYER_HEAD:
            case CREEPER_HEAD:
            case ZOMBIE_HEAD:
            case SKELETON_SKULL:
            case WITHER_SKELETON_SKULL:
            case DRAGON_HEAD:
                break;
            default:
                return;
        }

        if (!customItemRecipes.isCustomItem(event.getItemInHand().getItemMeta()))
            return;

        String name = customItemRecipes.extractCustomID(event.getItemInHand().getItemMeta());

        switch (name)
        {
            case "test_turret":
                break;
            case "wabash_turret":
                break;
            case "TURRET_MMM10":
                break;
            default:
                if (!name.endsWith("_TURRET"))
                    return;
        }

        Location location = event.getBlock().getLocation().add(0.5, 0, 0.5);
        ArmorStand turret = event.getBlock().getWorld().spawn(location, ArmorStand.class);
        turret.setSmall(true);
        turret.setCustomName(event.getPlayer().getUniqueId().toString());
        turret.setHelmet(event.getItemInHand());
        event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);
        event.setCancelled(true);
        activateTurret(turret, event.getPlayer());
    }

    private boolean activateTurret(ArmorStand entity, OfflinePlayer owner)
    {
        //ignore if already activated
        if (turrets.containsKey(entity))
            return false;

        plugin.getLogger().info(entity.getHelmet().getItemMeta().getDisplayName());
        if (entity.getHelmet().getType() == Material.AIR)
            return false;
        String name = customItemRecipes.extractCustomID(entity.getHelmet().getItemMeta());
        if (name == null)
            return false;
        Turret turret = TurretFactory.createTurret(name, plugin, entity, owner);
        turrets.put(entity, turret);
        plugin.getLogger().info(entity.getCustomName());
        return true;
    }

    @EventHandler
    private void onTurretDestroyed(EntityDeathEvent event)
    {
        if (turrets.containsKey(event.getEntity()))
            event.getDrops().clear();
    }

    @EventHandler(ignoreCancelled = true)
    private void onTurretInteract(PlayerInteractAtEntityEvent event)
    {
        event.setCancelled(turrets.containsKey(event.getRightClicked()));
    }
}
