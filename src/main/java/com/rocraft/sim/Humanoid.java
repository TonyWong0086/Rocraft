package com.rocraft.sim;

/** Roblox Humanoid in Roblox units (studs, seconds). Imports nothing from Minecraft. */
public final class Humanoid {
    public static final double STEP = 1.0 / 60;      // Roblox physics rate
    public static final double GRAVITY = 196.2;      // studs/s^2
    public static final double STUD_M = 0.28;        // 1 stud = 0.28 m (frame mapping lives in one place)

    public double walkSpeed = 16, jumpPower = 50, maxHealth = 100;
    public double health = 100;
    public double x, y, z, vy;                       // y is feet height; floorY is the surface below
    public double floorY = 0;
    public boolean grounded = true;

    /** moveX/moveZ: unit-ish intent vector in world studs space. */
    public void step(double moveX, double moveZ, boolean jump) {
        double len = Math.hypot(moveX, moveZ);
        if (len > 1) { moveX /= len; moveZ /= len; }
        x += moveX * walkSpeed * STEP;
        z += moveZ * walkSpeed * STEP;
        if (grounded && jump) { vy = jumpPower; grounded = false; }
        if (!grounded) {
            y += vy * STEP - 0.5 * GRAVITY * STEP * STEP; // exact parabola per step
            vy -= GRAVITY * STEP;
            if (y <= floorY) { y = floorY; vy = 0; grounded = true; }
        }
    }

    public void damage(double d) { health = Math.max(0, health - d); }
    public boolean dead() { return health <= 0; }
}
