package com.rocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rocraft.Rocraft;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * First-person gear: the tool's Roblox mesh (with its Grip) instead of the flat icon. Item JSON:
 * {"type": "minecraft:special", "base": "rocraft:item/<gear>", "model": {"type": "rocraft:gear", "gear": "<gear>"}}.
 */
record GearSpecial(String gear) implements NoDataSpecialModelRenderer {
	record Unbaked(String gear) implements NoDataSpecialModelRenderer.Unbaked {
		static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.fieldOf("gear").forGetter(Unbaked::gear)).apply(i, Unbaked::new));
		@Override public MapCodec<Unbaked> type() { return CODEC; }
		@Override public NoDataSpecialModelRenderer bake(SpecialModelRenderer.BakingContext ctx) { return new GearSpecial(gear); }
	}

	static void register() {
		com.rocraft.client.mixin.SpecialRenderersAccessor.rocraft$ids().put(Rocraft.id("gear"), Unbaked.CODEC);
	}

	@Override
	public void submit(PoseStack ps, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
		var piece = Rig.GEAR.get(BuiltInRegistries.ITEM.getValue(Rocraft.id(gear)));
		if (piece == null) return;
		ps.pushPose();
		// grip point at the lower-left of the item square, tool pointing up-right like a held item sprite:
		// item = T(0.25, 0.25, 0.5) * S(stud) * Rz(-45) * Rx(90) * T(0, 1, 0) * armSpace  (RightGripAttachment is arm (0,-1,0))
		ps.translate(0.25f, 0.25f, 0.5f);
		ps.scale(0.28f, 0.28f, 0.28f);
		ps.mulPose(Axis.ZP.rotationDegrees(-45));
		ps.mulPose(Axis.XP.rotationDegrees(90));
		ps.translate(0, 1, 0);
		MeshDraw.submit(ps, out, light, piece.draw(), piece.draw().texture());
		ps.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> out) {
		for (float x : new float[]{0, 1}) for (float y : new float[]{0, 1}) for (float z : new float[]{0, 1}) out.accept(new Vector3f(x, y, z));
	}
}
