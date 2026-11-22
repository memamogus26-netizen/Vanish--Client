package com.vanishclient.modules;

import com.vanishclient.VanishClientAddon;
import com.vanishclient.util.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TargetEsp extends Module {
    public enum RenderType { Circle, Ghosts, Crystals, Lightning }
    public enum ColorMode { Static, Rainbow }
    public enum GhostTexture { Soft, Bloom, Smoke }
    public enum GhostPath { Standard, Spiral, Wave }

    private final SettingGroup sgRender = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");
    private final SettingGroup sgGhosts = settings.createGroup("Ghosts");
    private final SettingGroup sgFriends = settings.createGroup("Friends Target Render");

    // ---- Render ----
    private final Setting<RenderType> renderType = sgRender.add(new EnumSetting.Builder<RenderType>()
        .name("render-type").description("Selects the type of target ESP.")
        .defaultValue(RenderType.Circle).build());

    // ---- Colors ----
    private final Setting<ColorMode> colorMode = sgColors.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode").description("How the effect is coloured.")
        .defaultValue(ColorMode.Static).build());

    private final Setting<SettingColor> color = sgColors.add(new ColorSetting.Builder()
        .name("color").description("Effect colour.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> colorMode.get() == ColorMode.Static).build());

    // ---- Behavior ----
    private final Setting<Double> followTime = sgBehavior.add(new DoubleSetting.Builder()
        .name("follow-time").description("Seconds the ESP stays on a target after you stop hitting it.")
        .defaultValue(10).min(0.5).sliderMax(30).build());

    private final Setting<Double> animSpeed = sgBehavior.add(new DoubleSetting.Builder()
        .name("anim-speed").description("Animation speed multiplier.")
        .defaultValue(1).min(0.1).sliderMax(4).build());

    private final Setting<Double> maxDistance = sgBehavior.add(new DoubleSetting.Builder()
        .name("max-distance").description("Maximum distance to render the target.")
        .defaultValue(128).min(8).sliderMax(256).build());

    // ---- Ghosts ----
    private final Setting<GhostTexture> texture = sgGhosts.add(new EnumSetting.Builder<GhostTexture>()
        .name("texture").description("Soft = clean, Bloom = strong glow, Smoke = wide and faint.")
        .defaultValue(GhostTexture.Soft).build());

    private final Setting<GhostPath> path = sgGhosts.add(new EnumSetting.Builder<GhostPath>()
        .name("path").description("Ghost movement trajectory.")
        .defaultValue(GhostPath.Standard).build());

    private final Setting<Double> ghostLength = sgGhosts.add(new DoubleSetting.Builder()
        .name("length").description("Length of each ghost's tail.")
        .defaultValue(1.1).min(0.3).sliderMax(3).build());

    private final Setting<Integer> ghostCount = sgGhosts.add(new IntSetting.Builder()
        .name("count").description("Number of ghosts.")
        .defaultValue(3).min(1).sliderMax(8).build());

    private final Setting<Double> ghostSize = sgGhosts.add(new DoubleSetting.Builder()
        .name("size").description("Thickness of the ghosts.")
        .defaultValue(1).min(0.3).sliderMax(3).build());

    // ---- Friends ----
    private final Setting<Boolean> friendsEnabled = sgFriends.add(new BoolSetting.Builder()
        .name("enabled").description("Show the ESP on Meteor friends too.")
        .defaultValue(false).build());

    private final Setting<RenderType> friendType = sgFriends.add(new EnumSetting.Builder<RenderType>()
        .name("render-type").description("Effect used on friends.")
        .defaultValue(RenderType.Circle).build());

    private final Setting<SettingColor> friendColor = sgFriends.add(new ColorSetting.Builder()
        .name("color").description("Colour used on friends.")
        .defaultValue(new SettingColor(80, 255, 120, 255)).build());

    private LivingEntity target;
    private long lastSeen;
    private Vec3d prev, cur;

    public TargetEsp() {
        super(VanishClientAddon.CATEGORY, "target-esp", "Draws an effect on the entity you are fighting.");
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) { target = null; return; }

        long now = System.currentTimeMillis();
        if (mc.options.attackKey.isPressed() && mc.targetedEntity instanceof LivingEntity le && le != mc.player) {
            if (target != le) { target = le; prev = cur = Fx.pos(le); }
            lastSeen = now;
        }

        if (target != null) {
            if (target.isRemoved() || target.getHealth() <= 0f || now - lastSeen > followTime.get() * 1000) {
                target = null;
            } else {
                prev = cur;
                cur = Fx.pos(target);
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target == null || mc.player == null || prev == null || cur == null) return;
        if (mc.player.distanceTo(target) > maxDistance.get()) return;

        boolean friend = target instanceof PlayerEntity p && Friends.get().isFriend(p);
        if (friend && !friendsEnabled.get()) return;

        RenderType type = friend ? friendType.get() : renderType.get();
        Vec3d pos = Fx.lerp(prev, cur, event.tickDelta);
        double w = target.getWidth();
        double h = target.getHeight();
        double anim = System.currentTimeMillis() / 1000.0 * animSpeed.get();
        Renderer3D r = event.renderer;

        switch (type) {
            case Circle -> circle(r, pos, w, h, anim, friend);
            case Ghosts -> ghosts(r, pos, w, h, anim, friend);
            case Crystals -> crystals(r, pos, w, h, anim, friend);
            case Lightning -> lightning(r, pos, w, h, friend);
        }
    }

    private Color col(boolean friend, double offset, int alpha) {
        if (friend) {
            SettingColor c = friendColor.get();
            return new Color(c.r, c.g, c.b, alpha);
        }
        if (colorMode.get() == ColorMode.Rainbow) return Fx.rainbow(0.3, offset, alpha);
        SettingColor c = color.get();
        return new Color(c.r, c.g, c.b, alpha);
    }

    // ---------------------------------------------------------------- Circle

    private void circle(Renderer3D r, Vec3d pos, double w, double h, double anim, boolean friend) {
        double radius = w * 0.9 + 0.15;
        double bob = (Math.sin(anim * 1.6) * 0.5 + 0.5) * h;

        ring(r, pos, radius, 0.02, col(friend, 0, 90));
        for (int i = 0; i < 4; i++) {
            ring(r, pos, radius, bob - i * 0.04, col(friend, i * 0.03, 255 - i * 60));
        }
    }

    private void ring(Renderer3D r, Vec3d pos, double radius, double y, Color c) {
        int seg = 40;
        Vec3d last = null;
        for (int i = 0; i <= seg; i++) {
            double a = i / (double) seg * Math.PI * 2;
            Vec3d p = new Vec3d(pos.x + Math.cos(a) * radius, pos.y + y, pos.z + Math.sin(a) * radius);
            if (last != null) Fx.line(r, last, p, c);
            last = p;
        }
    }

    // ---------------------------------------------------------------- Ghosts

    private void ghosts(Renderer3D r, Vec3d pos, double w, double h, double anim, boolean friend) {
        int count = ghostCount.get();
        int trail = Math.max(4, (int) (26 * ghostLength.get()));

        // {thickness multiplier, alpha multiplier} per glow layer
        double[][] layers = switch (texture.get()) {
            case Soft -> new double[][]{{1, 1}};
            case Bloom -> new double[][]{{1, 1}, {2, 0.35}, {3.2, 0.15}};
            case Smoke -> new double[][]{{2, 0.4}, {3.5, 0.2}};
        };

        for (int g = 0; g < count; g++) {
            double base = anim * 2.2 + g * Math.PI * 2 / count;
            List<Vec3d> pts = new ArrayList<>();
            for (int k = 0; k <= trail; k++) {
                double a = base - k * 0.09;
                double rad = w * 0.9 + 0.25;
                double y;
                switch (path.get()) {
                    case Spiral -> {
                        double s = Math.sin(a * 0.5);
                        rad = w * 0.6 + 0.2 + (s * 0.5 + 0.5) * 0.4;
                        y = h * 0.5 + s * h * 0.45;
                    }
                    case Wave -> y = h * 0.5 + Math.sin(a * 3 + g) * h * 0.3;
                    default -> y = h * 0.5 + Math.sin(a + g) * h * 0.35;
                }
                pts.add(new Vec3d(pos.x + Math.cos(a) * rad, pos.y + y, pos.z + Math.sin(a) * rad));
            }

            final int n = trail;
            for (double[] layer : layers) {
                double half = 0.035 * ghostSize.get() * layer[0];
                double alphaMul = layer[1];
                final int gi = g;
                Fx.ribbon(r, pts, -half, half, i -> col(friend, gi * 0.2 + i / (double) n * 0.2,
                    Fx.clampAlpha(255 * alphaMul * (1 - i / (double) n))));
            }
        }
    }

    // -------------------------------------------------------------- Crystals

    private void crystals(Renderer3D r, Vec3d pos, double w, double h, double anim, boolean friend) {
        int n = 28;
        for (int i = 0; i < n; i++) {
            double seed = i / (double) n;
            double t = (anim * 0.5 + seed * 7.3) % 1.0;
            double a = seed * Math.PI * 2 * 3 + t * 4;
            double rad = w * (0.9 + 0.5 * Math.sin(seed * 20 + anim));
            double x = pos.x + Math.cos(a) * rad;
            double y = pos.y + t * h * 1.1;
            double z = pos.z + Math.sin(a) * rad;
            double s = 0.045 * Math.sin(t * Math.PI);
            if (s <= 0.002) continue;

            Color c = col(friend, seed, Fx.clampAlpha(255 * Math.sin(t * Math.PI)));
            Fx.cube(r, x, y, z, s, c);
            Color arm = new Color(c.r, c.g, c.b, c.a / 2);
            r.line(x - s * 3, y, z, x + s * 3, y, z, arm);
            r.line(x, y - s * 3, z, x, y + s * 3, z, arm);
            r.line(x, y, z - s * 3, x, y, z + s * 3, arm);
        }
    }

    // ------------------------------------------------------------- Lightning

    private void lightning(Renderer3D r, Vec3d pos, double w, double h, boolean friend) {
        long bucket = System.currentTimeMillis() / 90;   // new bolts roughly every 90 ms
        int bolts = 5;
        for (int b = 0; b < bolts; b++) {
            Random rnd = new Random(bucket * 31L + b);
            Vec3d from = pos.add((rnd.nextDouble() - 0.5) * w * 1.6, rnd.nextDouble() * h, (rnd.nextDouble() - 0.5) * w * 1.6);
            Vec3d prev = from;
            int segs = 5;
            for (int i = 0; i < segs; i++) {
                Vec3d next = prev.add((rnd.nextDouble() - 0.5) * 0.7, (rnd.nextDouble() - 0.5) * 0.7, (rnd.nextDouble() - 0.5) * 0.7);
                Fx.line(r, prev, next, new Color(255, 255, 255, 255));
                Fx.line(r, prev.add(0.02, 0, 0), next.add(0.02, 0, 0), col(friend, b * 0.15, 150));
                prev = next;
            }
        }
    }
}
