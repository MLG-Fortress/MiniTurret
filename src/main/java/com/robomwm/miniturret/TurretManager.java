package com.robomwm.miniturret;

import com.robomwm.customitemrecipes.CustomItemRecipes;
import com.robomwm.miniturret.turret.TargetSystem;
import com.robomwm.miniturret.turret.Turret;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

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
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    private void onTurretSpawn(BlockPlaceEvent event)
    {
        if (!event.canBuild())
            return;

        if (event.getItemInHand().getType() != Material.PLAYER_HEAD)
            return;

        if (!customItemRecipes.isCustomItem(event.getItemInHand().getItemMeta()))
            return;

        String name = customItemRecipes.extractCustomID(event.getItemInHand().getItemMeta());

        switch (name)
        {
            case "test_turret":
                break;
            case "wabash_turret":
                break;
            default:
                if (!name.startsWith("TURRET_"))
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

        if (turrets.containsKey(entity))
            return false;

        //TODO: use ItemMeta instead of SkullMeta
        plugin.getLogger().info(entity.getHelmet().getItemMeta().getDisplayName());
        String name = ((SkullMeta)entity.getHelmet().getItemMeta()).getOwningPlayer().getName();
        Turret turret = null;
        switch (name)
        {
            case "carrqt":
                turret = new Turret(plugin, entity, null, owner, null, 32, 40, 10, TargetSystem.FIRST)
                {
                    @Override
                    public Projectile spawnProjectile(Vector vector)
                    {
                        Arrow arrow = turret.launchProjectile(Arrow.class, vector.normalize());
                        arrow.setGravity(false);
                        arrow.addCustomEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 1), false);
                        setProjectileProperty(arrow, "DAMAGE", 0.5D);
                        return arrow;
                    }
                };
                break;

            case "Wabash_Rouge":
                turret = new Turret(plugin, entity, null, owner, null, 32, 100, 30, TargetSystem.FIRST)
                {
                    @Override
                    public Projectile spawnProjectile(Vector vector)
                    {
                        Fireball fireball = turret.launchProjectile(Fireball.class, vector);
                        fireball.setDirection(vector);
                        return fireball;
                    }
                };
                break;
            case "MMM10":
                turret = new Turret(plugin, entity, null, owner, null, 32, 100, 5, TargetSystem.NEAREST)
                {
                    @Override
                    public Projectile spawnProjectile(Vector vector)
                    {
                        Arrow arrow = turret.launchProjectile(Arrow.class, vector.normalize().multiply(3));
                        arrow.setGravity(false);
                        return arrow;
                    }
                };
                break;

            default:
                return false;
        }
        turrets.put(entity, turret);
        plugin.getLogger().info(entity.getCustomName());
        return true;
    }

    @EventHandler
    private void onTurretDestroyed(EntityDeathEvent event)
    {
        if (event.getEntityType() != EntityType.ARMOR_STAND)
            return;
        if (turrets.containsKey(event.getEntity()))
            event.getDrops().clear();
    }

    @EventHandler(ignoreCancelled = true)
    private void onTurretInteract(PlayerInteractAtEntityEvent event)
    {
        if (event.getRightClicked().getType() != EntityType.ARMOR_STAND)
            return;
        event.setCancelled(turrets.containsKey(event.getRightClicked()));
    }

    private boolean canTarget(LivingEntity turret, LivingEntity target, boolean includeInvisible)
    {
        if (target.getType() == EntityType.PLAYER && ((Player)target).getGameMode() != GameMode.SURVIVAL)
            return false;
        return (includeInvisible || !target.hasPotionEffect(PotionEffectType.INVISIBILITY))
                && !target.isDead()
                && target.isValid()
                && turret.hasLineOfSight(target)
                && turret.getLocation().distanceSquared(target.getLocation()) < 36 * 36;
    }
}
