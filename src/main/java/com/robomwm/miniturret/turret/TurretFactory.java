package com.robomwm.miniturret.turret;

import org.bukkit.Color;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Created on 9/2/2019.
 *
 * @author RoboMWM
 */
public class TurretFactory
{
    public static Turret createTurret(String name, Plugin plugin, LivingEntity entity, OfflinePlayer owner)
    {
        switch (name)
        {
            case "test_turret":
                return new Turret(plugin, entity, null, owner, null, 32, 40, 10, TargetSystem.FIRST)
                {
                    @Override
                    public Projectile spawnProjectile(Vector vector)
                    {
                        Arrow arrow = turret.launchProjectile(Arrow.class, vector.normalize());
                        arrow.setGravity(false);
                        arrow.setCritical(true);
                        arrow.setColor(Color.ORANGE);
                        arrow.addCustomEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 1), false);
                        setProjectileProperty(arrow, "DAMAGE", 0.5D);
                        return arrow;
                    }
                };
            case "Wabash_Turret":
                return new Turret(plugin, entity, null, owner, null, 32, 100, 30, TargetSystem.FIRST)
                {
                    @Override
                    public Projectile spawnProjectile(Vector vector)
                    {
                        Fireball fireball = turret.launchProjectile(Fireball.class, vector);
                        fireball.setDirection(vector);
                        return fireball;
                    }
                };
            case "TURRET_MMM10":
                return new Turret(plugin, entity, null, owner, null, 32, 100, 5, TargetSystem.NEAREST)
                {
                    @Override
                    public Projectile spawnProjectile(Vector vector)
                    {
                        Arrow arrow = turret.launchProjectile(Arrow.class, vector.normalize().multiply(3));
                        arrow.setGravity(false);
                        arrow.setCritical(true);
                        arrow.setColor(Color.YELLOW);
                        return arrow;
                    }
                };
            default:
                return null;
        }
    }
}
