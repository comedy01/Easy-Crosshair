package dev.easycrosshair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.easycrosshair.config.ColorSlot;
import dev.easycrosshair.crosshair.CrosshairPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ProjectMetadataTest {
    private static JsonObject resourceJson(String name) throws IOException {
        InputStream in = ProjectMetadataTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(in, name + " must be on the classpath");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static boolean has(String name) {
        return ProjectMetadataTest.class.getClassLoader().getResource(name) != null;
    }

    private static String resourceText(String name) throws IOException {
        try (InputStream in = ProjectMetadataTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void modJson() throws IOException {
        assumeTrue(has("fabric.mod.json"));
        JsonObject mod = resourceJson("fabric.mod.json");
        assertEquals("easycrosshair", mod.get("id").getAsString());
        assertEquals("Easy Crosshair", mod.get("name").getAsString());
        assertEquals("client", mod.get("environment").getAsString());
        assertFalse(mod.get("version").getAsString().contains("$"), "version placeholder must be expanded");

        JsonObject entrypoints = mod.getAsJsonObject("entrypoints");
        assertEquals("dev.easycrosshair.fabric.EasyCrosshairFabric", entrypoints.getAsJsonArray("client").get(0).getAsString());
        for (String key : entrypoints.keySet()) {
            for (JsonElement entry : entrypoints.getAsJsonArray(key)) {
                assertClassExists(entry.getAsString());
            }
        }

        JsonObject depends = mod.getAsJsonObject("depends");
        assertFalse(depends.has("modmenu"), "Mod Menu must stay optional");
        assertTrue(mod.getAsJsonObject("suggests").has("modmenu"));
    }

    @Test
    void neoforgeMetadata() throws IOException {
        assumeTrue(has("META-INF/neoforge.mods.toml"));
        String toml = resourceText("META-INF/neoforge.mods.toml");
        assertTrue(toml.contains("modId=\"easycrosshair\""), toml);
        assertTrue(toml.contains("displayName=\"Easy Crosshair\""), toml);
        assertTrue(toml.contains("config=\"easycrosshair.mixins.json\""), toml);
        assertFalse(toml.contains("${"), "placeholders must be expanded");
        assertClassExists("dev.easycrosshair.neoforge.EasyCrosshairNeoForge");
    }

    @Test
    void forgeMetadata() throws IOException {
        assumeTrue(has("META-INF/mods.toml"));
        String toml = resourceText("META-INF/mods.toml");
        assertTrue(toml.contains("modId=\"easycrosshair\""), toml);
        assertTrue(toml.contains("displayName=\"Easy Crosshair\""), toml);
        assertTrue(toml.contains("modId=\"forge\""), toml);
        assertFalse(toml.contains("${"), "placeholders must be expanded");
        assertEquals("easycrosshair.refmap.json", resourceJson("easycrosshair.mixins.json").get("refmap").getAsString());
        assertEquals(15, resourceJson("pack.mcmeta").getAsJsonObject("pack").get("pack_format").getAsInt());
        assertClassExists("dev.easycrosshair.forge.EasyCrosshairForge");
    }

    @Test
    void mixinsExist() throws IOException {
        JsonObject mixins = resourceJson("easycrosshair.mixins.json");
        assertEquals("dev.easycrosshair.mixin", mixins.get("package").getAsString());
        JsonArray client = mixins.getAsJsonArray("client");
        assertEquals(1, client.size());
        for (JsonElement entry : client) {
            assertClassExists("dev.easycrosshair.mixin." + entry.getAsString());
        }
        assertFalse(mixins.has("mixins"), "all mixins are client-only");
        assertFalse(mixins.has("server"));
    }

    @Test
    void everyChoiceIsTranslated() throws IOException {
        JsonObject lang = resourceJson("assets/easycrosshair/lang/en_us.json");
        for (String shape : CrosshairPolicy.SHAPES) {
            assertTrue(lang.has("easycrosshair.shape." + key(shape)), shape);
        }
        for (String indicator : CrosshairPolicy.INDICATORS) {
            assertTrue(lang.has("easycrosshair.indicator." + key(indicator)), indicator);
        }
        for (ColorSlot slot : ColorSlot.values()) {
            assertTrue(lang.has("easycrosshair.options.slot." + slot.key()), slot.name());
        }
        assertTrue(lang.has("easycrosshair.color_mode." + key(CrosshairPolicy.COLOR_SOLID)));
        assertTrue(lang.has("easycrosshair.color_mode." + key(CrosshairPolicy.COLOR_RAINBOW)));
    }

    @Test
    void iconsExist() {
        for (String size : new String[] {"16", "32", "128"}) {
            assertTrue(has("assets/easycrosshair/icon" + size + ".png"), "icon" + size);
        }
    }

    @Test
    void noOtherProjectReferences() throws IOException {
        String[] forbidden = {"limn", "lidless", "reticle", "loupe"};
        try (Stream<Path> files = Stream.of(Path.of("src/main"), Path.of("src/loader"), Path.of("src/compat"),
                        Path.of("build.gradle"), Path.of("gradle.properties"), Path.of("settings.gradle"), Path.of("README.md"))
                .filter(Files::exists)
                .flatMap(ProjectMetadataTest::walk)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> !file.toString().endsWith(".png"))
                    .forEach(file -> {
                        String text;
                        try {
                            text = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                        } catch (IOException e) {
                            throw new IllegalStateException(file.toString(), e);
                        }
                        for (String token : forbidden) {
                            assertFalse(text.contains(token), file + " mentions \"" + token + "\"");
                        }
                    });
        }
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT).replace(" + ", "_").replace('-', '_');
    }

    private static void assertClassExists(String className) {
        String path = className.replace('.', '/') + ".class";
        assertNotNull(ProjectMetadataTest.class.getClassLoader().getResource(path), path + " must be compiled");
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root);
        } catch (IOException e) {
            throw new IllegalStateException(root.toString(), e);
        }
    }
}
