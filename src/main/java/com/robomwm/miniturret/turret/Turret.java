package com.robomwm.miniturret.turret;

import com.robomwm.miniturret.MiniTurret;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

public abstract class Turret
{
    private final Plugin plugin;
    protected final LivingEntity turret;
    private final Clan clan;
    private final ClanManager clanManager;
    private final OfflinePlayer owner;
    private final int range;
    private final int delay;
    private final int rate;
    protected LivingEntity target;
    private TargetSystem system;
    private final BukkitTask checkTask;
    private final BukkitTask fireTask;

    public Turret(Plugin plugin, LivingEntity turret, ClanManager clanManager, OfflinePlayer owner, Clan clan, int range, int delay, int rate, TargetSystem system)
    {
        this.plugin = plugin;
        this.turret = turret;
        this.clanManager = clanManager;
        this.owner = owner;
        this.clan = clan;
        this.range = range;
        this.delay = delay;
        this.rate = rate;
        this.system = system;

        checkTask = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (!turret.isValid())
                {
                    cancel();
                    return;
                }
                target = pickTarget();
            }
        }.runTaskTimer(plugin, delay, delay);

        fireTask = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (!turret.isValid())
                {
                    cancel();
                    return;
                }
                fire();
            }
        }.runTaskTimer(plugin, delay, rate);

        turret.setMetadata(MiniTurret.TURRET_KEY, new FixedMetadataValue(plugin, this));
    }

    public OfflinePlayer getOwner()
    {
        return owner;
    }

    public void close()
    {
        checkTask.cancel();
        fireTask.cancel();
    }

    public boolean isFriendly(UUID uuid)
    {
        if (uuid.equals(owner.getUniqueId()))
            return true;
        if (clan == null)
            return false;
        return clan.getAllAllyMembers().contains(clanManager.getClanPlayer(uuid));
    }

    private Collection<LivingEntity> getTargetsInSight(int range)
    {
        Collection<LivingEntity> targets = turret.getLocation().getNearbyLivingEntities(range);
        targets.remove(turret);
        Iterator<LivingEntity> targetsIterator = targets.iterator();

        while (targetsIterator.hasNext())
        {
            LivingEntity entity = targetsIterator.next();
            //Ignore owner and friendly players
            if (isFriendly(entity.getUniqueId()))
            {
                targetsIterator.remove();
                continue;
            }

            //Ignore invulnerable gamemodes
            else if (entity.getType() == EntityType.PLAYER)
            {
                Player player = (Player)entity;
                if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.isInvulnerable())
                {
                    targetsIterator.remove();
                    continue;
                }
            }

            //Ignore friendly turrets
            else if (entity.getType() == EntityType.ARMOR_STAND)
            {
                if (!entity.hasMetadata(MiniTurret.TURRET_KEY))
                {
                    targetsIterator.remove();
                    continue;
                }

                Turret turret = (Turret)entity.getMetadata("MT_TURRET").get(0).value();
                if (isFriendly(turret.getOwner().getUniqueId()))
                {
                    targetsIterator.remove();
                    continue;
                }
            }

            //Ignore entities not in line of sight
            if (!entity.isValid() || !turret.hasLineOfSight(entity))
                targetsIterator.remove();
        }

        return targets;
    }

    //choose a target based on the assigned Target system
    public LivingEntity pickTarget()
    {
        LivingEntity target = null;
        switch (system)
        {
            case NEAREST:
                int distance = range + 1;
                //TODO: check if getEntitiesInRange returns in sorted order (I'd presume it does?)
                for (LivingEntity entity : getTargetsInSight(range))
                    return entity;
                return target;

            case LOWEST_HEALTH:
                double health = Double.MAX_VALUE;
                for (LivingEntity entity : getTargetsInSight(range))
                {
                    if (entity.getHealth() < health)
                    {
                        health = entity.getHealth();
                        target = entity;
                    }
                }
                return target;

            case HIGHEST_HEALTH:
                health = 0;
                for (LivingEntity entity : getTargetsInSight(range))
                {
                    if (entity.getHealth() < health)
                    {
                        health = entity.getHealth();
                        target = entity;
                    }
                }
                return target;

            case FIRST:
            default:
                if (this.target != null)
                    return this.target;
                for (LivingEntity entity : getTargetsInSight(range))
                    return entity;
                return target;
        }
    }

    public Vector aim()
    {
        if (target == null || !target.isValid() || target.hasMetadata("DEAD")
                || target.getWorld() != turret.getWorld()
                || !turret.hasLineOfSight(target)
                || target.getLocation().distanceSquared(turret.getLocation()) > range * range)
        {
            target = null;
            return null;
        }
        Vector vector = target.getEyeLocation().toVector().subtract(turret.getEyeLocation().toVector());
        turret.teleport(turret.getLocation().setDirection(vector));
        return vector;
    }

    public boolean fire()
    {
        Vector vector = aim();
        if (vector == null)
            return false;

        spawnProjectile(vector).setShooter(turret);
        return true;
    }

    public Projectile spawnProjectile(Vector vector)
    {
        Arrow arrow = turret.launchProjectile(Arrow.class, vector.normalize());
        arrow.setGravity(false);
        arrow.setCritical(true);
        setProjectileProperty(arrow, "DAMAGE", 0.5D);
        return arrow;
    }

    public void setProjectileProperty(Projectile projectile, String property, Object value)
    {
        projectile.setMetadata("PROJECTILE_" + property, new FixedMetadataValue(plugin, value));
    }
}
