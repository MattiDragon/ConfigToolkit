package io.github.mattidragon.configloader.impl;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.mattidragon.configloader.api.ConfigManager;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public class ConfigManagerImpl<D> implements ConfigManager<D> {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConfigLoader/ConfigManagerImpl");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Event<OnLoadCallback<D>> onChange = EventFactory.createArrayBacked(OnLoadCallback.class,
            listeners -> config -> {
                for (var listener : listeners) {
                    listener.onChange(config);
                }
            });
    private final Codec<D> codec;
    private final D defaultValue;
    private final Path path;
    private final String id;

    private boolean overridden = false;
    private boolean prepared = false;
    private D value;

    public ConfigManagerImpl(Codec<D> codec, D defaultValue, String id) {
        this.codec = codec;
        this.defaultValue = defaultValue;
        this.id = id;
        this.path = FabricLoader.getInstance().getConfigDir().resolve(id + ".json");

        this.value = defaultValue;
    }

    @Override
    public @NotNull D get() {
        // Use default config for datagen
        if (System.getProperty("fabric-api.datagen") != null || System.getProperty("fabric-api.gametest") != null) {
            return defaultValue;
        }

        prepare();
        return value;
    }

    @Override
    public void set(@NotNull D config) {
        if (overridden) {
            LOGGER.error("Tried to set config {} while overridden", id);
            throw new IllegalStateException("Tried to set config %s while overridden".formatted(id));
        }
        value = config;
        onChange.invoker().onChange(config);
        save();
    }

    @Override
    public Optional<RuntimeException> reload() {
        try {
            load();
            return Optional.empty();
        } catch (RuntimeException e) {
            LOGGER.error("Failed to reload config {}", id, e);
            return Optional.of(e);
        }
    }

    @Override
    public @NotNull Event<OnLoadCallback<D>> getReloadEvent() {
        return onChange;
    }

    @Override
    public ConfigManager.@NotNull OverrideCloser override(@NotNull D config) {
        if (overridden) throw new IllegalStateException("Only singe config override allowed");
        overridden = true;
        var previous = value;
        value = config;
        return () -> {
            value = previous;
            overridden = false;
        };
    }

    private void prepare() {
        if (prepared) return;
        prepared = true;

        if (Files.exists(path)) {
            load();
        } else {
            save();
        }
    }

    private void save() {
        codec.encodeStart(JsonOps.INSTANCE, value)
                .resultOrPartial(LOGGER::error)
                .ifPresent(this::write);
    }

    private void write(JsonElement data) {
        try (var out = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(data, out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save power networks config", e);
        }
    }

    private void load() {
        try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            var json = GSON.fromJson(in, JsonObject.class);
            var result = codec.parse(JsonOps.INSTANCE, json);

            value = result.mapError(error -> "Failed to load config %s: %s. Delete the file or invalid values to regenerate defaults.".formatted(id, error))
                    .getOrThrow(false, LOGGER::error);

            onChange.invoker().onChange(value);

            codec.encodeStart(JsonOps.INSTANCE, value)
                    .resultOrPartial(LOGGER::error)
                    .filter(o -> !json.equals(o)) // Don't save if no changes in json
                    .ifPresent(this::write);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load config %s due to io error".formatted(id), e);
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("Config %s has a syntax errors".formatted(id), e);
        }
    }
}
