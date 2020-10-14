package com.nuparu.nightterror;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.util.math.vector.Vector3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.nuparu.nightterror.config.Config;
import com.nuparu.nightterror.config.ConfigHelper;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerEntity.SleepResult;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.world.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("nightterror")
public class NightTerror {
	private static final Logger LOGGER = LogManager.getLogger();

	public static Method m_isDirectPathBetweenPoints;

	public NightTerror() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigHelper.config);
		ConfigHelper.loadConfig(ConfigHelper.config,
				FMLPaths.CONFIGDIR.get().resolve("nightterror-common.toml").toString());
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void setup(final FMLCommonSetupEvent event) {
		m_isDirectPathBetweenPoints = ObfuscationReflectionHelper.findMethod(PathNavigator.class, "func_75493_a",
				Vector3d.class, Vector3d.class, int.class, int.class, int.class);
	}

	@SubscribeEvent
	public void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
		if (event.getResultStatus() != SleepResult.NOT_SAFE && event.getResultStatus() != null)
			return;
		PlayerEntity player = event.getPlayer();
		if (net.minecraftforge.event.ForgeEventFactory.fireSleepingTimeCheck(player, event.getOptionalPos())) {
			event.setResult((SleepResult) null);
			player.startSleeping(event.getOptionalPos().get());
			ObfuscationReflectionHelper.setPrivateValue(PlayerEntity.class, player, 0, "field_71076_b");
			if (player.world instanceof ServerWorld) {
				((ServerWorld) player.world).updateAllPlayersSleepingFlag();
			}
		}
	}

	@SubscribeEvent
	public void onSleepFinished(SleepFinishedTimeEvent event) {
		IWorld iw = event.getWorld();
		if (iw instanceof IServerWorld) {
			IServerWorld world = (IServerWorld) event.getWorld();
			MinecraftServer server = world.getWorld().getServer();
			if (server == null)
				return;
			List<ServerPlayerEntity> players = server.getPlayerList().getPlayers();
			boolean finished = true;
			for (ServerPlayerEntity player : players) {
				if (player.isCreative() || player.isSpectator())
					continue;
				BlockPos bed = player.getBedPosition().get();
				BlockState state = world.getBlockState(bed);
				boolean woke = false;
				if (world.getRandom().nextDouble() > Config.terrorChance.get() * (getModifier(world))) {
					continue;
				}
				for (int x = -Config.checkRange.get(); x < Config.checkRange.get(); x++) {
					for (int y = -Config.checkRange.get()/2; y < Config.checkRange.get()/2; y++) {
						for (int z = -Config.checkRange.get(); z < Config.checkRange.get(); z++) {
							List<String> list = new ArrayList<>();
							list.addAll(Config.wakingEntites.get());
							if (list.size() < 1)
								return;

							BlockPos pos = bed.add(x, y, z);
							MobEntity mob = null;
							while (mob == null && list.size() > 0) {
								int index = world.getRandom().nextInt(list.size());
								Optional<EntityType<?>> o = EntityType.byKey(list.get(index));
								list.remove(index);
								if (!o.isPresent())
									continue;
								EntityType<?> type = o.get();
								Entity entity = type.create(world.getWorld());
								if (!(entity instanceof MobEntity)) {
									LOGGER.warn(type.getName() + " is not an instance of MobEntity! Skipping.");
									continue;
								}
								mob = (MobEntity) entity;
							}
							if (mob == null)
								return;
							if (!ZombieEntity.isValidLightLevel(world, pos, world.getRandom()))
								continue;
							try {
								boolean path = (boolean) (m_isDirectPathBetweenPoints.invoke(mob.getNavigator(),
										new Vector3d(pos.getX(), pos.getY(), pos.getZ()),
										new Vector3d(bed.getX(), bed.getY(), bed.getZ()), 1 , 1 , 1));
								if (!path)
									continue;
							} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
								e.printStackTrace();
								continue;
							}
							world.addEntity(mob);
							Optional<Vector3d> vec = state.getBedSpawnPosition(EntityType.PLAYER, world.getWorld(), bed, player.func_242109_L(), null);
							mob.setPosition(vec.get().x, vec.get().y, vec.get().z);
							mob.getLookController().setLookPosition(player.getPosX(),
									player.getPosY() + (double) player.getEyeHeight(), player.getPosZ(),
									(float) mob.getHorizontalFaceSpeed(), (float) mob.getVerticalFaceSpeed());
							player.attackEntityAsMob(mob);
							player.attackEntityFrom(DamageSource.causeMobDamage(mob), Config.wakeDamage.get());
							woke = true;
							finished = false;
							break;
						}
						if (woke) {
							break;
						}
					}
					if (woke) {
						break;
					}
				}

			}
			if (!finished && Config.interruptSleep.get()) {
				event.setTimeAddition(world.getWorld().getDayTime());
			}

		}
	}

	public static double getModifier(IServerWorld world) {
		switch (world.getDifficulty()) {
		default:
		case PEACEFUL:
			return 0.5d;
		case EASY:
			return 0.75d;
		case NORMAL:
			return 1d;
		case HARD:
			return 1.5d;
		}
	}

}
