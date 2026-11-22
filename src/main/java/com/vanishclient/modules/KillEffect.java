package com.vanishclient.modules;

import com.vanishclient.VanishClientAddon;
import com.vanishclient.util.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class KillEffect extends Module {
    public enum Type { Particles, Thunder, Blood }
    public enum TargetType { Players, Mobs, Both }
    public enum Sound { None, Thunder, Orb, LevelUp }
    public enum ColorMode { Static, Rainbow }

    private final SettingGroup sgEffect = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgSound = settings.createGroup("Sound");

    // ---- Effect type ----
    private final Setting<Type> effectType = sgEffect.add(new EnumSetting.Builder<Type>()
        .name("effect-type").description("Effect for your own kills.")
        .defaultValue(Type.Particles).build());

    private final Setting<Type> friendEffectType = sgEffect.add(new EnumSetting.Builder<Type>()
        .name("friend-effect-type").description("Effect when a friend gets a kill near you.")
        .defaultValue(Type.Thunder).build());

    private final Setting<Boolean> showForFriends = sgEffect.add(new BoolSetting.Builder()
        .name("show-for-friends").description("Also show effects when friends kill something.")
        .defaultValue(false).build());

    private final Setting<TargetType> targetType = sgEffect.add(new EnumSetting.Builder<TargetType>()
        .name("target-type").description("Which kinds of victims show an effect.")
        .defaultValue(TargetType.Both).build());

    private final Setting<Set<EntityType<?>>> entities = sgEffect.add(new EntityTypeListSetting.Builder()
        .name("entities").description("Select which entities to show effects for.")
        .defaultValue(EntityType.PLAYER, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER)
        .onlyAttackable().build());

    // ---- Appearance ----
    private final Setting<Integer> count = sgAppearance.add(new IntSetting.Builder()
        .name("particle-count").description("Amount of particles per kill.")
        .defaultValue(40).min(5).sliderMax(150).build());

    private final Setting<Double> size = sgAppearance.add(new DoubleSetting.Builder()
        .name("particle-size").description("Particle size in blocks.")
        .defaultValue(0.06).min(0.01).sliderMax(0.25).build());

    private final Setting<Double> boltHeight = sgAppearance.add(new DoubleSetting.Builder()
        .name("thunder-height").description("How high the lightning bolt starts.")
        .defaultValue(24).min(5).sliderMax(60).build());

    // ---- Behavior ----
    private final Setting<Double> duration = sgBehavior.add(new DoubleSetting.Builder()
        .name("duration").description("How long an effect lasts, in seconds.")
        .defaultValue(1.2).min(0.2).sliderMax(4).build());

    private final Setting<Double> spread = sgBehavior.add(new DoubleSetting.Builder()
        .name("spread").description("How fast particles fly outward.")
        .defaultValue(2.5).min(0.2).sliderMax(8).build());

    // ---- Colors ----
    private final Setting<ColorMode> colorMode = sgColors.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode").description("Colour for Particles and Thunder.")
        .defaultValue(ColorMode.Static).build());

    private final Setting<SettingColor> color = sgColors.add(new ColorSetting.Builder()
        .name("color").description("Particle and thunder colour.")
        .defaultValue(new SettingColor(255, 140, 40, 255))
        .visible(() -> colorMode.get() == ColorMode.Static).build());

    private final Setting<SettingColor> bloodColor = sgColors.add(new ColorSetting.Builder()
        .name("blood-color").description("Colour of the Blood effect.")
        .defaultValue(new SettingColor(200, 10, 10, 255)).build());

    // ---- Sound ----
    private final Setting<Sound> sound = sgSound.add(new EnumSetting.Builder<Sound>()
        .name("sound").description("Sound played on a kill.")
        .defaultValue(Sound.None).build());

    private final Setting<Double> volume = sgSound.add(new DoubleSetting.Builder()
        .name("volume").description("Sound volume.")
        .defaultValue(0.8).min(0).max(2).sliderMax(2)
        .visible(() -> sound.get() != Sound.None).build());

    private static final class Burst {
        Type type;
        Vec3d pos;
        long start;
        long seed;
        double[][] vel;   // per particle: vx, vy, vz
        double[] hue;     // per particle colour offset
    }

    private final List<Burst> bursts = new ArrayList<>();
    private final Map<Integer, Long> hits = new HashMap<>();   // entity id -> last time you hit it
    private final Set<Integer> handled = new HashSet<>();
    private final Random rng = new Random();

    public KillEffect() {
        super(VanishClientAddon.CATEGORY, "kill-effect", "Shows an effect when you (or friends) kill something.");
    }

    @Override
    public void onDeactivate() {
        bursts.clear();
        hits.clear();
        handled.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) { bursts.clear(); hits.clear(); handled.clear(); return; }

        long now = System.currentTimeMillis();
        if (mc.options.attackKey.isPressed() && mc.targetedEntity instanceof LivingEntity hit) {
            hits.put(hit.getId(), now);
        }
        hits.values().removeIf(t -> now - t > 4000);

        for (Entity ent : mc.world.getEntities()) {
            if (!(ent instanceof LivingEntity le) || le == mc.player) continue;
            if (le.getHealth() > 0f) continue;
            if (!handled.add(le.getId())) continue;
            if (!isAllowed(le)) continue;

            Type type;
            if (hits.containsKey(le.getId())) {
                type = effectType.get();
            } else if (showForFriends.get() && friendNear(le)) {
                type = friendEffectType.get();
            } else {
                continue;
            }
            spawn(type, Fx.pos(le).add(0, le.getHeight() * 0.5, 0));
        }
        handled.removeIf(id -> mc.world.getEntityById(id) == null);

        bursts.removeIf(b -> now - b.start > duration.get() * 1000);
    }

    private boolean isAllowed(LivingEntity le) {
        boolean player = le instanceof PlayerEntity;
        if (targetType.get() == TargetType.Players && !player) return false;
        if (targetType.get() == TargetType.Mobs && player) return false;
        return entities.get().contains(le.getType());
    }

    /** The client cannot see who dealt the damage, so a friend within 6 blocks counts as the killer. */
    private boolean friendNear(LivingEntity victim) {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p == victim) continue;
            if (Friends.get().isFriend(p) && p.distanceTo(victim) < 6) return true;
        }
        return false;
    }

    private void spawn(Type type, Vec3d pos) {
        Burst b = new Burst();
        b.type = type;
        b.pos = pos;
        b.start = System.currentTimeMillis();
        b.seed = rng.nextLong();

        int n = type == Type.Thunder ? 0 : (type == Type.Blood ? Math.max(8, count.get() / 2) : count.get());
        b.vel = new double[n][3];
        b.hue = new double[n];
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double up = type == Type.Blood ? 0.8 + rng.nextDouble() * 1.4 : (rng.nextDouble() - 0.3) * 1.6;
            double s = spread.get() * (0.3 + rng.nextDouble() * 0.7) * (type == Type.Blood ? 0.5 : 0.6);
            b.vel[i][0] = Math.cos(a) * s;
            b.vel[i][1] = up * (type == Type.Blood ? 2.2 : spread.get() * 0.4);
            b.vel[i][2] = Math.sin(a) * s;
            b.hue[i] = rng.nextDouble();
        }
        bursts.add(b);
        playSound();
    }

    private void playSound() {
        SoundEvent ev = switch (sound.get()) {
            case None -> null;
            case Thunder -> SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;
            case Orb -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case LevelUp -> SoundEvents.ENTITY_PLAYER_LEVELUP;
        };
        if (ev != null) mc.getSoundManager().play(PositionedSoundInstance.master(ev, 1f, volume.get().floatValue()));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (bursts.isEmpty()) return;
        long now = System.currentTimeMillis();
        double dur = duration.get() * 1000;

        for (Burst b : bursts) {
            double age = now - b.start;
            double life = Math.min(1, age / dur);
            double t = age / 1000.0;
            int alpha = Fx.clampAlpha(255 * (1 - life));

            switch (b.type) {
                case Particles -> {
                    for (int i = 0; i < b.vel.length; i++) {
                        double x = b.pos.x + b.vel[i][0] * t;
                        double y = b.pos.y + b.vel[i][1] * t - 2.0 * t * t;
                        double z = b.pos.z + b.vel[i][2] * t;
                        Fx.cube(event.renderer, x, y, z, size.get() * (1 - life * 0.6), particleColor(b.hue[i], alpha));
                    }
                }
                case Blood -> {
                    Color c = bloodColor.get();
                    for (int i = 0; i < b.vel.length; i++) {
                        double x = b.pos.x + b.vel[i][0] * t;
                        double y = Math.max(b.pos.y - 1.0, b.pos.y + b.vel[i][1] * t - 9.0 * t * t);
                        double z = b.pos.z + b.vel[i][2] * t;
                        double shade = 0.6 + 0.4 * b.hue[i];
                        Fx.cube(event.renderer, x, y, z, size.get() * 1.4,
                            new Color((int) (c.r * shade), (int) (c.g * shade), (int) (c.b * shade), Fx.clampAlpha(255 * (1 - life * life))));
                    }
                }
                case Thunder -> drawBolt(event, b, alpha);
            }
        }
    }

    private void drawBolt(Render3DEvent event, Burst b, int alpha) {
        Random r = new Random(b.seed);
        Vec3d top = b.pos.add(0, boltHeight.get(), 0);
        int segs = 14;
        Vec3d prev = top;
        for (int i = 1; i <= segs; i++) {
            double f = i / (double) segs;
            Vec3d next = i == segs ? b.pos
                : new Vec3d(
                    top.x + (b.pos.x - top.x) * f + (r.nextDouble() - 0.5) * 1.6,
                    top.y + (b.pos.y - top.y) * f,
                    top.z + (b.pos.z - top.z) * f + (r.nextDouble() - 0.5) * 1.6);
            Color core = new Color(255, 255, 255, alpha);
            Color glow = particleColor(f, Fx.clampAlpha(alpha * 0.6));
            Fx.line(event.renderer, prev, next, core);
            for (double o : new double[]{0.04, -0.04}) {
                Fx.line(event.renderer, prev.add(o, 0, 0), next.add(o, 0, 0), glow);
                Fx.line(event.renderer, prev.add(0, 0, o), next.add(0, 0, o), glow);
            }
            prev = next;
        }
    }

    private Color particleColor(double hue, int alpha) {
        if (colorMode.get() == ColorMode.Rainbow) return Fx.rainbow(0.5, hue, alpha);
        SettingColor c = color.get();
        return new Color(c.r, c.g, c.b, alpha);
    }
}
