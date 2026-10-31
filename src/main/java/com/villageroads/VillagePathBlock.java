package com.villageroads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A path block (like the vanilla dirt path) that makes everything walk a little faster on it. The speed bonus is the
 * block's own speed factor, so it needs no code running per tick. 1.075 on the block gives about +10% top speed on
 * ground (the factor is applied every tick on top of the normal ground friction).
 */
public class VillagePathBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

    public VillagePathBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.DIRT_PATH).speedFactor(1.075F));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
