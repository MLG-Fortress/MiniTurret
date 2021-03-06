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
import org.bukkit.potion.PotionEffectType;
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

    public Turret(Plugin plugin, LivingEntity turretEntity, ClanManager clanManager, OfflinePlayer owner, Clan clan, int range, int delay, int rate, TargetSystem system)
    {
        this.plugin = plugin;
        this.turret = turretEntity;
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
                if (!turretEntity.isValid())
                {
                    close();
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
                if (!turretEntity.isValid())
                {
                    close();
                    return;
                }
                fire();
            }
        }.runTaskTimer(plugin, delay, rate);

        turretEntity.setMetadata(MiniTurret.TURRET_KEY, new FixedMetadataValue(plugin, this));
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

            //Ignore anything that can't be targeted
            if (!canTarget(entity))
            {
                targetsIterator.remove();
                continue;
            }

            //Ignore owner and friendly players
            else if (isFriendly(entity.getUniqueId()))
            {
                targetsIterator.remove();
                continue;
            }

            //Ignore armor stands
            else if (entity.getType() == EntityType.ARMOR_STAND)
            {
                //except for other turrets
                if (!entity.hasMetadata(MiniTurret.TURRET_KEY))
                {
                    targetsIterator.remove();
                    continue;
                }

                //but if it's a friendly turret, ignore it
                Turret turret = (Turret)entity.getMetadata(MiniTurret.TURRET_KEY).get(0).value();
                if (isFriendly(turret.getOwner().getUniqueId()))
                {
                    targetsIterator.remove();
                    continue;
                }
            }
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
        if (!canTarget(target))
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

    public boolean canTarget(LivingEntity target)
    {
        if (target == null)
            return false;
        if (target.getType() == EntityType.PLAYER
                && (((Player)target).getGameMode() == GameMode.CREATIVE
                || ((Player)target).getGameMode() == GameMode.SPECTATOR))
            return false;
        return !target.hasPotionEffect(PotionEffectType.INVISIBILITY)
                && !target.isInvulnerable()
                && !target.hasMetadata("DEAD")
                && target.isValid()
                && turret.hasLineOfSight(target)
                && turret.getLocation().distanceSquared(target.getLocation()) < range * range;
    }
}
