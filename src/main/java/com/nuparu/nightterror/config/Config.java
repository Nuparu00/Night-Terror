package com.nuparu.nightterror.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public class Config {

	public static IntValue checkRange;
	public static DoubleValue terrorChance;
	public static ConfigValue<List<? extends String>> wakingEntites;
	public static IntValue wakeDamage;
	public static BooleanValue interruptSleep;
	
	public static void init(ForgeConfigSpec.Builder server) {

		ArrayList<String> list = new ArrayList<String>();
		list.add("minecraft:zombie");
		list.add("minecraft:skeleton");
		checkRange = server.comment("Range of check for dark spots.").defineInRange("general.check_range", 8, 1, 32);
		terrorChance = server.comment("Base chance of being woken up.").defineInRange("general.terror_chance", 0.5, 0, 1);
		wakingEntites = server.comment("List of entities that can wake the player up.").defineList("general.waking_entities",
				list, s -> s instanceof String);
		wakeDamage = server.comment("Damage dealt to play on waking up by a monster.").defineInRange("general.wake_damage", 1, 0, 2147483647);
		interruptSleep = server.comment("Should interrupt sleep at night or wait until sunrise?").define("general.interrupt_sleep", true);
	}
}
