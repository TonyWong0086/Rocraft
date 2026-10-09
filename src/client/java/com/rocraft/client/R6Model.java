package com.rocraft.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * Roblox R6 rig in Minecraft model units (6 units = 1 stud, so legs stay 12 tall and the player stays 1.8 blocks):
 * torso 2x2x1 studs, arms/legs 1x2x1, head ~1.2 cube. Same part names as PlayerModel so vanilla animation drives it.
 */
public final class R6Model {
	/** {u, v, w, h, d} per box on the 64x64 unit texture: head, torso, rightArm, leftArm, rightLeg, leftLeg. */
	static final int[][] BOXES = {{24, 36, 7, 7, 7}, {0, 0, 12, 12, 6}, {36, 0, 6, 12, 6}, {0, 18, 6, 12, 6}, {24, 18, 6, 12, 6}, {0, 36, 6, 12, 6}};

	public static MeshDefinition mesh() {
		var mesh = new MeshDefinition();
		var root = mesh.getRoot();
		root.addOrReplaceChild("head", box(0, -3.5f, -7, -3.5f), PartPose.ZERO)
			.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", box(1, -6, 0, -3), PartPose.ZERO)
			.addOrReplaceChild("jacket", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", box(2, -3, -2, -3), PartPose.offset(-9, 2, 0))
			.addOrReplaceChild("right_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_arm", box(3, -3, -2, -3), PartPose.offset(9, 2, 0))
			.addOrReplaceChild("left_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_leg", box(4, -3, 0, -3), PartPose.offset(-3, 12, 0))
			.addOrReplaceChild("right_pants", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_leg", box(5, -3, 0, -3), PartPose.offset(3, 12, 0))
			.addOrReplaceChild("left_pants", CubeListBuilder.create(), PartPose.ZERO);
		return mesh;
	}

	private static CubeListBuilder box(int i, float x, float y, float z) {
		int[] b = BOXES[i];
		// boxes only without a Roblox install; otherwise AvatarLayer draws Roblox's meshes (and, like Roblox, no body in first person)
		return RobloxAssets.CONTENT != null ? CubeListBuilder.create() : CubeListBuilder.create().texOffs(b[0], b[1]).addBox(x, y, z, b[2], b[3], b[4]);
	}
}
