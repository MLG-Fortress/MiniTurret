package com.robomwm.miniturret;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Created on 5/11/2018.
 *
 * @author RoboMWM
 */
public class MiniTurret extends JavaPlugin
{
    public static final String TURRET_KEY = "MT_TURRET";

    public void onEnable()
    {
        new TurretManager(this);
    }
}
