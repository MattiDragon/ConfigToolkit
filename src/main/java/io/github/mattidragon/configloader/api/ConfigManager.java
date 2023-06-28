package io.github.mattidragon.configloader.api;

import com.mojang.serialization.Codec;
import io.github.mattidragon.configloader.impl.ConfigManagerImpl;
import net.fabricmc.fabric.api.event.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * This class handles loading and saving of config files as well as storing the current value.
 * It also provides other utilities, like a reload event and option to temporarily override the config,
 * intended for use in previews in config screens.
 * @param <D> The type of data the config holds. Usually a record named {@code ConfigData}.
 */
public interface ConfigManager<D> {
    /**
     * Creates a new config manager.
     * @param codec The codec used for serializing the config. Partial results are not accepted.
     * @param defaultValue A default value for the config.
     * @param id An id for the config. Currently only used for file path and in error messages.
     * @return The created config manager.
     * @param <D> The config type of the manager
     */
    @Contract("_, _, _ -> new")
    @NotNull
    static <D> ConfigManager<D> create(@NotNull Codec<D> codec, @NotNull D defaultValue, @NotNull String id) {
        return new ConfigManagerImpl<>(codec, defaultValue, id);
    }

    /**
     * Gets the current value of the config, loading it if it hasn't already been done.
     * @return The current value.
     */
    @NotNull
    D get();

    /**
     * Sets the value of the config. This triggers the config to save to file.
     * @param value The value to set the config to.
     */
    void set(@NotNull D value);

    /**
     * Forces the config to reload from file.
     * @return A runtime exceptions that might have occurred during loading. It's recommended to handle these gracefully and report errors back to the user.
     */
    @SuppressWarnings("UnstableApiUsage")
    @CheckReturnValue
    Optional<RuntimeException> reload();

    /**
     * The method provides an event that you can use to listen for changes to the config. Useful if you need to refresh caches that are based on the config value.
     */
    @NotNull
    Event<OnLoadCallback<D>> getReloadEvent();

    /**
     * Temporarily overrides the value of the config.
     * While overridden the config cannot be set.
     * Does not trigger the reload event.
     * @param config The value to temporarily override the config with.
     * @return An {@link AutoCloseable} that when closed returns the config to the original value.
     * Use with try-with-resources highly advised.
     */
    @NotNull
    OverrideCloser override(@NotNull D config);

    @ApiStatus.NonExtendable
    interface OverrideCloser extends AutoCloseable {
        void close();
    }

    @FunctionalInterface
    interface OnLoadCallback<D> {
        void onChange(D config);
    }
}
