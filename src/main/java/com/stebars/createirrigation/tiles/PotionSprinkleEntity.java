package com.stebars.createirrigation.tiles;

import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.particles.IParticleData;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class PotionSprinkleEntity extends AreaEffectCloudEntity {
	
	// TODO this doesn't really look like it's supposed to
	// particles should fly out faster, disappear faster, not waft upward
	// shouldn't be inheriting from AreaEffectCloudEntity

	private final int DURATION = 8;
	private final double SPEED = 15;
	private final double NUM_PARTICLES = 40;
	private final double SPEED_VAR = 2;
	private final double DOWN_BIAS = 10;

	public PotionSprinkleEntity(EntityType<? extends AreaEffectCloudEntity> p_i50389_1_, World p_i50389_2_) {
		super(p_i50389_1_, p_i50389_2_);
	}

	public PotionSprinkleEntity(World p_i46810_1_, double p_i46810_2_, double p_i46810_4_, double p_i46810_6_) {
		this(EntityType.AREA_EFFECT_CLOUD, p_i46810_1_);
		this.setPos(p_i46810_2_, p_i46810_4_, p_i46810_6_);
	}

	@Override
	public void tick() {
		if (this.level.isClientSide) {
			IParticleData iparticledata = this.getParticle();

			for(int k1 = 0; (float)k1 < NUM_PARTICLES; ++k1) {				
				double dx = this.random.nextGaussian();
				double dy = this.random.nextGaussian();
				double dz = this.random.nextGaussian();
				double mul = MathHelper.fastInvSqrt(dx * dx + dy * dy + dz * dz) * (SPEED + SPEED_VAR * (random.nextFloat() - 0.5));
				
				this.level.addAlwaysVisibleParticle(iparticledata, this.getX(), this.getY(), this.getZ(),
						dx * mul, dy * mul - DOWN_BIAS, dz * mul);
			}
		} else {
			if (this.tickCount >= DURATION) {
				this.remove();
				return;
			}
		}
	}
}
