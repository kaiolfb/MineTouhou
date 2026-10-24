package com.minetouhou.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** O PC MineTouhou: um bloco com entidade de bloco que guarda o CD e o estado da sessão. */
public class PcBlock extends BlockWithEntity {
	public static final MapCodec<PcBlock> CODEC = createCodec(PcBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<PcState> STATE = EnumProperty.of("state", PcState.class);

	/** Preenchido pelo cliente para abrir a tela do PC (evita carregar classes de cliente no servidor). */
	public static volatile ScreenOpener clientScreenOpener = (world, pos) -> { };

	@FunctionalInterface
	public interface ScreenOpener {
		void open(World world, BlockPos pos);
	}

	private static final VoxelShape SHAPE = VoxelShapes.cuboid(0.0, 0.0, 0.1875, 1.0, 0.9375, 1.0);

	public PcBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(STATE, PcState.OFF));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, STATE);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// a frente do PC olha para o jogador
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()).with(STATE, PcState.OFF);
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return rotateShape(state.get(FACING));
	}

	/** O modelo "norte" ocupa z em [0.1875, 1]; gira o cubo para as outras direções. */
	private static VoxelShape rotateShape(Direction facing) {
		return switch (facing) {
			case SOUTH -> VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.9375, 0.8125);
			case EAST -> VoxelShapes.cuboid(0.0, 0.0, 0.0, 0.8125, 0.9375, 1.0);
			case WEST -> VoxelShapes.cuboid(0.1875, 0.0, 0.0, 1.0, 0.9375, 1.0);
			default -> SHAPE;
		};
	}

	/** Rotação Y (graus, sentido horário visto de cima) que o blockstate aplica ao modelo "norte". */
	public static int blockstateYRotation(Direction facing) {
		return switch (facing) {
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> 0;
		};
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected net.minecraft.util.ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
		BlockHitResult hit) {
		if (world.isClient) {
			clientScreenOpener.open(world, pos);
		} else if (world.getBlockEntity(pos) instanceof PcBlockEntity pc) {
			pc.powerOnIfOff();
		}
		return net.minecraft.util.ActionResult.success(world.isClient);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PcBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient) return null;
		return validateTicker(type, com.minetouhou.MthRegistry.PC_BLOCK_ENTITY, PcBlockEntity::serverTick);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof PcBlockEntity pc) {
				// O CD nunca se perde: cai no mundo quando o PC é quebrado.
				var cd = pc.takeCd();
				if (!cd.isEmpty() && !world.isClient) {
					ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cd);
				}
				pc.endSessionSilently();
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
