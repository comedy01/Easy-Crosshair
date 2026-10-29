package dev.easycrosshair.showcase;

import com.mojang.datafixers.util.Pair;
import dev.easycrosshair.client.EasyCrosshairClient;
import dev.easycrosshair.client.gui.CrosshairSettingsScreen;
import dev.easycrosshair.config.CrosshairConfig;
import dev.easycrosshair.crosshair.CrosshairPolicy;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

// Films Easy Crosshair for its gallery: a scripted visit to a quiet spot in the plains that records each feature as
// a 1080p60 clip and saves stills. Started with -Pec.showcase=<output dir> -Pec.ffmpeg=<ffmpeg path>.
public final class Showcase {
    private static final String WORLD = "easycrosshair-showcase";
    private static final long SETTLE_NANOS = 3_000_000_000L;
    private static final int GLIDE = 24;
    private static final int TEAL = 0xFF33E0D0;
    // Colors for the color scene, each one a setting the red, green, blue and opacity sliders can reach.
    private static final int[] PALETTE = {
            0xFFFF4B4B, 0xFFFFA020, 0xFFFFE030, 0xFF5BE35B, TEAL, 0xFF4F8CFF, 0xFFB45CFF, 0xFFFF5CC8};
    private static final String[] ARROW = {
            "B",
            "BB",
            "BWB",
            "BWWB",
            "BWWWB",
            "BWWWWB",
            "BWWWWWB",
            "BWWWWWWB",
            "BWWWWWWWB",
            "BWWWWWWWWB",
            "BWWWWWWWWWB",
            "BWWWWWWBBBBB",
            "BWWWBWWB",
            "BWWB BWWB",
            "BWB  BWWB",
            "BB    BWWB",
            "B     BWWB",
            "       BWWB",
            "        BB"};
    private static Showcase instance;

    private final Path out;
    private final Recorder recorder;
    private final long seed;
    private final List<Action> actions = new ArrayList<>();
    private final AtomicReference<BlockPos> site = new AtomicReference<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int frame;
    private long actionStart;
    private Path pendingStill;
    private boolean showHand;
    private boolean cursorShown;
    private double cursorX;
    private double cursorY;

    private interface Action {
        // Called once per frame with the frames spent on this action so far; returns true when done.
        boolean run(int frame);
    }

    // A spot for the crosshair to rest on, reached after gliding for move frames and then held.
    private record Key(int move, int hold, Vec3 target) {
    }

    // Where the cursor arrives at a frame. The spot is looked up each frame, since widgets move with the screen.
    private record Stop(int frame, Supplier<double[]> where) {
    }

    private Showcase(Path out, String ffmpeg, long seed) {
        this.out = out;
        this.recorder = new Recorder(ffmpeg);
        this.seed = seed;
    }

    public static void start() {
        String ffmpeg = System.getProperty("easycrosshair.ffmpeg", "ffmpeg");
        long seed = Long.parseLong(System.getProperty("easycrosshair.seed", "12345"));
        instance = new Showcase(Path.of(System.getProperty("easycrosshair.showcase")), ffmpeg, seed);
    }

    public static boolean hideHand() {
        return instance != null && instance.started && !instance.showHand;
    }

    public static long nanos() {
        return Clock.nanos();
    }

    public static void frame() {
        if (instance != null) {
            instance.onFrame();
        }
    }

    // Draws an arrow cursor at the scripted mouse position, two window pixels to each cursor pixel.
    public static void drawCursor(GuiGraphicsExtractor graphics) {
        if (instance == null || !instance.started || !instance.cursorShown) {
            return;
        }
        float cell = 2.0F / (float) Minecraft.getInstance().getWindow().getGuiScale();
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) instance.cursorX, (float) instance.cursorY);
        graphics.pose().scale(cell, cell);
        for (int row = 0; row < ARROW.length; row++) {
            String line = ARROW[row];
            int x = 0;
            while (x < line.length()) {
                char c = line.charAt(x);
                int end = x;
                while (end < line.length() && line.charAt(end) == c) {
                    end++;
                }
                if (c != ' ') {
                    graphics.fill(x, row, end, row + 1, c == 'B' ? 0xFF000000 : 0xFFFFFFFF);
                }
                x = end;
            }
        }
        graphics.pose().popMatrix();
    }

    private void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 120) {
                started = true;
                setUpOptions(mc);
                plan(mc);
                log("creating world with seed " + seed);
                deleteWorld(mc);
                Worlds.create(mc, WORLD, false, seed);
            }
            return;
        }
        Clock.step();
        mc.gui.toastManager().clear();
        try {
            while (index < actions.size()) {
                if (frame == 0) {
                    actionStart = System.nanoTime();
                }
                if (!actions.get(index).run(frame++)) {
                    if (cursorShown && Screens.current(mc) != null) {
                        double scale = mc.getWindow().getGuiScale();
                        Ui.moveMouse(mc, cursorX * scale, cursorY * scale);
                    }
                    return;
                }
                index++;
                frame = 0;
            }
        } catch (Throwable e) {
            log("FAILED: " + e);
            e.printStackTrace();
        }
        finish(mc);
    }

    private void finish(Minecraft mc) {
        finished = true;
        Clock.release();
        release(mc);
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(90_000L);
            } catch (InterruptedException e) {
                return;
            }
            log("the game did not close, forcing it");
            Runtime.getRuntime().halt(1);
        }, "easycrosshair-showcase-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        EasyCrosshairClient.config().resetToDefaults();
        EasyCrosshairClient.saveConfig();
        log("showcase done");
        mc.stop();
    }

    private static void setUpOptions(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.renderDistance().set(12);
        mc.options.bobView().set(false);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    // ---- building blocks ----------------------------------------------------------------------------------------

    private void once(Runnable action) {
        actions.add(f -> {
            action.run();
            return true;
        });
    }

    private void until(BooleanSupplier ready) {
        actions.add(f -> ready.getAsBoolean());
    }

    // Runs hold every frame for this many frames.
    private void holdFor(int frames, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= frames;
        });
    }

    // Waits until the chunks around the camera are loaded and drawn, running hold every frame meanwhile.
    private void settle(Minecraft mc, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= 90 && System.nanoTime() - actionStart > SETTLE_NANOS
                    && (mc.levelRenderer.hasRenderedAllSections() || System.nanoTime() - actionStart > 20 * SETTLE_NANOS);
        });
    }

    // Records a clip. The script runs before each frame is drawn; call shot() inside it to keep that frame as a still.
    private void record(String name, int frames, IntConsumer script) {
        actions.add(f -> {
            if (f == 0) {
                log("recording " + name);
                recorder.start(out.resolve("clips").resolve(name + ".mp4"));
            } else {
                Path still = pendingStill;
                pendingStill = null;
                recorder.capture(still);
            }
            if (f < frames) {
                script.accept(f);
                return false;
            }
            return true;
        });
        until(recorder::drained);
        once(recorder::stop);
    }

    private void shot(String name) {
        pendingStill = out.resolve("stills").resolve(name + ".png");
    }

    static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    private static void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void cmd(Minecraft mc, String format, Object... args) {
        run(mc, String.format(Locale.ROOT, format, args));
    }

    // Turns the player to this yaw and pitch at once, with no smoothing between frames.
    private static void face(Minecraft mc, double yaw, double pitch) {
        LocalPlayer player = mc.player;
        player.setYRot((float) yaw);
        player.setXRot((float) pitch);
        player.yRotO = (float) yaw;
        player.xRotO = (float) pitch;
        player.setYHeadRot((float) yaw);
        player.yHeadRotO = (float) yaw;
    }

    // Points the crosshair at a spot in the world.
    private static void lookAt(Minecraft mc, Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        face(mc, Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    // Glides the crosshair through the keys: the first is held, each later one is reached over its move frames.
    private static void follow(Minecraft mc, int f, Key... keys) {
        Vec3 target = keys[0].target();
        int t = f - keys[0].hold();
        for (int i = 1; i < keys.length && t >= 0; i++) {
            Key key = keys[i];
            if (t < key.move()) {
                target = lerp(keys[i - 1].target(), key.target(), smooth(t / (double) key.move()));
                break;
            }
            t -= key.move();
            target = key.target();
            t -= key.hold();
        }
        lookAt(mc, target);
    }

    // Moves the cursor through the stops, gliding into each one in the frames before it arrives.
    private void cursor(int f, Stop... stops) {
        double[] at = stops[0].where().get();
        for (int i = 1; i < stops.length; i++) {
            Stop stop = stops[i];
            if (f >= stop.frame()) {
                at = stop.where().get();
            } else if (f >= stop.frame() - GLIDE) {
                double[] to = stop.where().get();
                double s = smooth((f - (stop.frame() - GLIDE)) / (double) GLIDE);
                at = new double[] {lerp(at[0], to[0], s), lerp(at[1], to[1], s)};
                break;
            } else {
                break;
            }
        }
        cursorShown = true;
        cursorX = at[0];
        cursorY = at[1];
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private static boolean wanted(String scene) {
        String only = System.getProperty("easycrosshair.scenes");
        return only == null || List.of(only.split(",")).contains(scene);
    }

    // ---- the set: a flat stretch of plains with three mobs and a big oak straight ahead -----------------------

    // The centre of block (dx, dy, dz) on the set, where dy 0 is the layer the player stands in.
    private Vec3 at(double dx, double dy, double dz) {
        BlockPos c = site.get();
        return new Vec3(c.getX() + dx + 0.5, c.getY() + dy, c.getZ() + dz + 0.5);
    }

    private static int height(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    // Finds plains, then of the spots around it picks the flattest dry one, so levelling the set leaves no cliffs.
    // Heights come straight from the terrain noise, which is quick and generates no chunks.
    private void locate(Minecraft mc) {
        once(() -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                ChunkGenerator generator = level.getChunkSource().getGenerator();
                RandomState noise = level.getChunkSource().randomState();
                Pair<BlockPos, Holder<Biome>> pair = level.findClosestBiome3d(h -> h.is(Biomes.PLAINS),
                        BlockPos.ZERO, 6400, 32, 64);
                BlockPos found = pair == null ? BlockPos.ZERO : pair.getFirst();
                BlockPos best = null;
                int bestScore = Integer.MAX_VALUE;
                for (int ox = -768; ox <= 768; ox += 48) {
                    for (int oz = -768; oz <= 768; oz += 48) {
                        int cx = found.getX() + ox;
                        int cz = found.getZ() + oz;
                        int centre = generator.getBaseHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG, level, noise);
                        if (!level.getBiome(new BlockPos(cx, centre, cz)).is(Biomes.PLAINS)) {
                            continue;
                        }
                        // How many samples stand more than a block off the usual ground height, with water worse.
                        List<Integer> heights = new ArrayList<>();
                        int wet = 0;
                        for (int dx = -40; dx <= 40; dx += 8) {
                            for (int dz = -40; dz <= 40; dz += 8) {
                                int top = generator.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.WORLD_SURFACE_WG,
                                        level, noise);
                                int floor = generator.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.OCEAN_FLOOR_WG,
                                        level, noise);
                                heights.add(top);
                                if (top != floor) {
                                    wet++;
                                }
                            }
                        }
                        int usual = mode(heights);
                        int score = wet * 3;
                        for (int h : heights) {
                            if (Math.abs(h - usual) > 1) {
                                score++;
                            }
                        }
                        if (score < bestScore) {
                            bestScore = score;
                            best = new BlockPos(cx, centre, cz);
                        }
                    }
                }
                site.set(best == null ? found : best);
                log("plains at " + site.get().toShortString() + ", " + bestScore + " uneven samples");
            });
        });
        until(() -> site.get() != null);
    }

    private static int mode(List<Integer> values) {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = values.get(0);
        for (int value : values) {
            int count = counts.merge(value, 1, Integer::sum);
            if (count > counts.get(best)) {
                best = value;
            }
        }
        return best;
    }

    // Once the chunks exist, stands the set on the most common ground height around it, so its edges meet the land.
    private void level(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        int ground = server.submit(() -> {
            ServerLevel level = server.overworld();
            BlockPos c = site.get();
            List<Integer> heights = new ArrayList<>();
            for (int dx = -24; dx <= 24; dx += 2) {
                for (int dz = -24; dz <= 24; dz += 2) {
                    heights.add(height(level, c.getX() + dx, c.getZ() + dz));
                }
            }
            return mode(heights);
        }).join();
        BlockPos c = site.get();
        site.set(new BlockPos(c.getX(), ground + 1, c.getZ()));
        log("set stands at y " + (ground + 1));
    }

    // Fills a box in slices small enough for the fill command.
    private static void fill(Minecraft mc, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        int layers = Math.max(1, 32768 / ((Math.abs(x2 - x1) + 1) * (Math.abs(z2 - z1) + 1)));
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y += layers) {
            cmd(mc, "fill %d %d %d %d %d %d %s", x1, y, z1, x2, Math.min(y + layers - 1, Math.max(y1, y2)), z2, block);
        }
    }

    private void summon(Minecraft mc, String type, Vec3 at, float facing) {
        cmd(mc, "summon minecraft:%s %.2f %.2f %.2f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"set\"],"
                + "Rotation:[%.1ff,0f]}", type, at.x, at.y, at.z, facing);
    }

    private void buildSet(Minecraft mc) {
        BlockPos c = site.get();
        int x = c.getX();
        int z = c.getZ();
        int ground = c.getY() - 1;
        fill(mc, x - 24, ground + 1, z - 24, x + 24, ground + 40, z + 24, "minecraft:air");
        fill(mc, x - 24, ground - 6, z - 24, x + 24, ground - 1, z + 24, "minecraft:dirt");
        fill(mc, x - 24, ground, z - 24, x + 24, ground, z + 24, "minecraft:grass_block");
        // A big oak straight ahead gives the crosshair something to sit on in the close-ups.
        cmd(mc, "place feature minecraft:fancy_oak %d %d %d", x, ground + 1, z + 14);
        String[] kinds = {"oak", "birch", "fancy_oak"};
        int[][] trees = {{-9, 12}, {8, 17}, {-16, 19}, {15, 10}, {-21, 5}, {21, -6}, {-15, -15}, {12, -19}, {3, -22},
                {-5, 22}, {19, 21}, {-22, -3}};
        for (int i = 0; i < trees.length; i++) {
            cmd(mc, "place feature minecraft:%s %d %d %d", kinds[i % kinds.length], x + trees[i][0], ground + 1,
                    z + trees[i][1]);
        }
        Random random = new Random(seed);
        String[] flowers = {"dandelion", "poppy", "oxeye_daisy", "cornflower", "azure_bluet"};
        for (int dx = -23; dx <= 23; dx++) {
            for (int dz = -23; dz <= 23; dz++) {
                double roll = random.nextDouble();
                String block;
                if (roll < 0.12) {
                    block = "short_grass";
                } else if (roll < 0.145) {
                    block = flowers[random.nextInt(flowers.length)];
                } else {
                    continue;
                }
                cmd(mc, "setblock %d %d %d minecraft:%s keep", x + dx, ground + 1, z + dz, block);
            }
        }
        run(mc, "kill @e[type=!minecraft:player]");
        // A hostile and two passive mobs in a row, facing the camera.
        summon(mc, "creeper", at(2, 0, 3), 180.0F);
        summon(mc, "sheep", at(0, 0, 3), 180.0F);
        summon(mc, "cow", at(-2, 0, 3), 180.0F);
        run(mc, "clear @p");
        run(mc, "item replace entity @p hotbar.0 with minecraft:diamond_sword");
        run(mc, "item replace entity @p hotbar.1 with minecraft:bow");
        run(mc, "item replace entity @p hotbar.2 with minecraft:arrow 64");
        run(mc, "item replace entity @p hotbar.3 with minecraft:oak_log 64");
        run(mc, "item replace entity @p hotbar.4 with minecraft:bread 16");
    }

    // Moves to the start of a scene with defaults, then waits for the world to draw, holding where the clip starts.
    private void scene(Minecraft mc, CrosshairConfig config, Vec3 feet, Runnable setup, Runnable hold) {
        once(() -> {
            release(mc);
            Screens.open(mc, null);
            config.resetToDefaults();
            cursorShown = false;
            showHand = false;
            cmd(mc, "tp @p %.3f %.3f %.3f", feet.x, feet.y, feet.z);
            run(mc, "kill @e[type=!minecraft:player,tag=!set]");
            select(mc, 8);
        });
        holdFor(20, hold);
        once(setup);
        settle(mc, hold);
    }

    private void plan(Minecraft mc) {
        CrosshairConfig config = EasyCrosshairClient.config();

        until(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null);
        once(() -> {
            log("world loaded");
            Clock.fix();
            config.resetToDefaults();
            run(mc, "time set 2500");
            run(mc, "weather clear 1000000");
            run(mc, "gamerule advance_time false");
            run(mc, "gamerule spawn_mobs false");
        });
        locate(mc);
        // Commands only change loaded chunks, so go there and keep the whole set loaded before building it.
        once(() -> {
            BlockPos c = site.get();
            cmd(mc, "forceload add %d %d %d %d", c.getX() - 30, c.getZ() - 30, c.getX() + 30, c.getZ() + 30);
            cmd(mc, "tp @p %d %d %d", c.getX(), c.getY() + 45, c.getZ());
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        });
        settle(mc, () -> {
        });
        once(() -> level(mc));
        once(() -> buildSet(mc));
        holdFor(60, () -> {
        });
        once(() -> {
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
        });
        // The scenes are placed relative to the site, so they can only be planned once it has been found.
        once(() -> film(mc, config));
    }

    private void film(Minecraft mc, CrosshairConfig config) {
        Vec3 spot = at(0, 0, 0);
        // Straight ahead at eye height, over the sheep's back and onto the oak's trunk, out of reach.
        Vec3 trunk = at(0, 1.62, 14);

        if (wanted("intro")) {
            Key from = new Key(0, 0, at(9, 3.0, 14));
            Key to = new Key(330, 0, at(-9, 3.0, 14));
            scene(mc, config, spot, () -> {
                config.setShape(CrosshairPolicy.SHAPE_CROSS_DOT);
                config.setColor(TEAL);
            }, () -> follow(mc, 0, from, to));
            record("01-intro", 330, f -> {
                follow(mc, f, from, to);
                if (f == 250) {
                    shot("intro");
                }
            });
        }

        if (wanted("shapes")) {
            // Each of the eight shapes in turn, as the Shape button steps through them.
            scene(mc, config, spot, () -> {
            }, () -> lookAt(mc, trunk));
            record("02-shapes", 300, f -> {
                int step = Math.min(CrosshairPolicy.SHAPES.size() - 1, Math.max(0, f - 20) / 34);
                String shape = CrosshairPolicy.SHAPES.get(step);
                config.setShape(shape);
                lookAt(mc, trunk);
                if (f >= 20 && (f - 20) % 34 == 17) {
                    shot("shape-" + shape.toLowerCase(Locale.ROOT).replace(" + ", "-").replace(' ', '-'));
                }
            });
        }

        if (wanted("colors")) {
            scene(mc, config, spot, () -> config.setShape(CrosshairPolicy.SHAPE_CROSS_DOT), () -> lookAt(mc, trunk));
            record("03-colors", 300, f -> {
                if (f < 200) {
                    config.setColor(PALETTE[Math.min(PALETTE.length - 1, f / 25)]);
                } else if (f == 200) {
                    config.setShape(CrosshairPolicy.SHAPE_CIRCLE_DOT);
                    config.setColorMode(CrosshairPolicy.COLOR_RAINBOW);
                }
                lookAt(mc, trunk);
                if (f == 12) {
                    shot("color-red");
                } else if (f == 112) {
                    shot("color-teal");
                } else if (f == 240) {
                    shot("rainbow");
                }
            });
        }

        if (wanted("size")) {
            // Whole slider steps, each held a moment: longer, wider, thicker, then a heavier outline.
            scene(mc, config, spot, () -> {
                config.setShape(CrosshairPolicy.SHAPE_CROSS);
                config.setColor(TEAL);
            }, () -> lookAt(mc, trunk));
            record("04-size", 300, f -> {
                if (f == 40) {
                    config.setLength(10);
                } else if (f == 80) {
                    config.setLength(14);
                } else if (f == 120) {
                    config.setGap(7);
                } else if (f == 160) {
                    config.setThickness(4);
                } else if (f == 200) {
                    config.setOutline(3);
                } else if (f == 240) {
                    config.setLength(8);
                    config.setGap(4);
                    config.setThickness(3);
                }
                lookAt(mc, trunk);
                if (f == 20) {
                    shot("size-default");
                } else if (f == 230) {
                    shot("size-large");
                }
            });
        }

        if (wanted("targets")) {
            Key creeper = new Key(0, 60, at(2, 1.05, 3));
            Key sheep = new Key(40, 60, at(0, 0.85, 3));
            Key cow = new Key(40, 90, at(-2, 0.95, 3));
            scene(mc, config, spot, () -> config.setShape(CrosshairPolicy.SHAPE_CROSS_DOT),
                    () -> follow(mc, 0, creeper));
            record("05-targets", 300, f -> {
                follow(mc, f, creeper, sheep, cow);
                if (f == 40) {
                    shot("target-hostile");
                } else if (f == 140) {
                    shot("target-passive");
                }
            });
        }

        if (wanted("attack")) {
            // Swing at the air: the bar charges back up, then the same with Fade.
            scene(mc, config, spot, () -> {
                config.setShape(CrosshairPolicy.SHAPE_CROSS_DOT);
                select(mc, 0);
                showHand = true;
            }, () -> lookAt(mc, trunk));
            record("06-attack", 300, f -> {
                if (f == 25 || f == 85 || f == 200 || f == 260) {
                    KeyMapping.click(mc.options.keyAttack.getDefaultKey());
                } else if (f == 150) {
                    config.setAttackIndicator(CrosshairPolicy.INDICATOR_FADE);
                }
                lookAt(mc, trunk);
                if (f == 31) {
                    shot("attack-bar");
                } else if (f == 206) {
                    shot("attack-fade");
                }
            });
        }

        if (wanted("spread")) {
            // Walk, sprint and jump towards the oak, then stop and draw the bow.
            Vec3 start = at(1, 0, -14);
            Vec3 ahead = at(1, 1.62, 14);
            scene(mc, config, start, () -> {
                config.setShape(CrosshairPolicy.SHAPE_CROSS);
                config.setDynamicSpread(true);
                select(mc, 1);
                showHand = true;
            }, () -> lookAt(mc, ahead));
            record("07-spread", 330, f -> {
                if (f == 30) {
                    mc.options.keyUp.setDown(true);
                } else if (f == 80) {
                    mc.options.keySprint.setDown(true);
                } else if (f == 105) {
                    mc.options.keyJump.setDown(true);
                } else if (f == 108) {
                    mc.options.keyJump.setDown(false);
                } else if (f == 130) {
                    release(mc);
                } else if (f == 195) {
                    mc.options.keyUse.setDown(true);
                }
                face(mc, 0, 0);
                if (f == 100) {
                    shot("spread-sprint");
                } else if (f == 300) {
                    shot("spread-bow");
                }
            });
            once(() -> {
                // Put the bow away before letting go, so no arrow is fired.
                select(mc, 8);
                release(mc);
            });
        }

        if (wanted("settings")) {
            scene(mc, config, spot, () -> {
            }, () -> lookAt(mc, trunk));
            once(() -> Screens.open(mc, new CrosshairSettingsScreen(null, mc.options)));
            holdFor(20, () -> cursor(0, new Stop(0, () -> corner(mc))));
            record("08-settings", 300, f -> {
                cursor(f, new Stop(0, () -> corner(mc)),
                        new Stop(35, () -> labelAt(mc, "Shape", 0.5)),
                        new Stop(80, () -> sliderAt(mc, "Red", 51 / 255.0)),
                        new Stop(120, () -> sliderAt(mc, "Green", 224 / 255.0)),
                        new Stop(160, () -> sliderAt(mc, "Blue", 208 / 255.0)),
                        new Stop(205, () -> sliderAt(mc, "Length", (10 - 1) / 19.0)),
                        new Stop(245, () -> sliderAt(mc, "Thickness", (3 - 1) / 9.0)));
                if (f == 45) {
                    clickLabel(mc, "Shape", 0.5);
                } else if (f == 90) {
                    clickSlider(mc, "Red", 51 / 255.0);
                } else if (f == 130) {
                    clickSlider(mc, "Green", 224 / 255.0);
                } else if (f == 170) {
                    clickSlider(mc, "Blue", 208 / 255.0);
                } else if (f == 215) {
                    clickSlider(mc, "Length", (10 - 1) / 19.0);
                } else if (f == 255) {
                    clickSlider(mc, "Thickness", (3 - 1) / 9.0);
                } else if (f == 285) {
                    shot("settings");
                }
            });
            once(() -> Screens.open(mc, null));
        }

        if (wanted("outro")) {
            once(() -> run(mc, "time set 12300"));
            Key from = new Key(0, 0, at(-8, 3.5, 14));
            Key to = new Key(330, 0, at(6, 3.5, 14));
            scene(mc, config, spot, () -> {
                config.setShape(CrosshairPolicy.SHAPE_CIRCLE_DOT);
                config.setColorMode(CrosshairPolicy.COLOR_RAINBOW);
            }, () -> follow(mc, 0, from, to));
            record("09-outro", 330, f -> {
                follow(mc, f, from, to);
                if (f == 150) {
                    shot("sunset");
                }
            });
            once(() -> run(mc, "time set 2500"));
        }
    }

    // ---- finding things on screen, in GUI coordinates ------------------------------------------------------------

    // The lower right corner, where the settings screen has no buttons.
    private static double[] corner(Minecraft mc) {
        Screen screen = Screens.current(mc);
        return new double[] {screen.width - 36, screen.height - 30};
    }

    private static double[] labelAt(Minecraft mc, String label, double along) {
        AbstractWidget widget = find(Screens.current(mc), label);
        if (widget == null) {
            throw new IllegalStateException("no widget " + label);
        }
        return new double[] {widget.getX() + widget.getWidth() * along, widget.getY() + widget.getHeight() / 2.0};
    }

    // Clicks the widget whose label starts with this text, at this fraction of its width.
    private static void clickLabel(Minecraft mc, String label, double along) {
        double[] at = labelAt(mc, label, along);
        Ui.click(Screens.current(mc), at[0], at[1]);
    }

    // The spot on a slider that sets it to this fraction of its range; the handle is 8 wide, so the ends are inset.
    private static double[] sliderAt(Minecraft mc, String label, double value) {
        AbstractWidget widget = find(Screens.current(mc), label);
        if (widget == null) {
            throw new IllegalStateException("no slider " + label);
        }
        return new double[] {widget.getX() + 4 + value * (widget.getWidth() - 8), widget.getY() + widget.getHeight() / 2.0};
    }

    private static void clickSlider(Minecraft mc, String label, double value) {
        double[] at = sliderAt(mc, label, value);
        Ui.click(Screens.current(mc), at[0], at[1]);
    }

    private static AbstractWidget find(GuiEventListener node, String label) {
        if (node instanceof AbstractWidget widget && widget.getMessage().getString().startsWith(label)) {
            return widget;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractWidget found = find(child, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void deleteWorld(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static void log(String message) {
        System.out.println("[easycrosshair-showcase] " + message);
    }
}
