package io.github.mattidragon.configloader.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * @deprecated The DFU {@link Codec#optionalFieldOf(String, Object)} method no longer fails silently as of minecraft 1.20.5. 
 * Please use those. The current factory methods are retained for backwards compatibility.
 */
@Deprecated
public class DefaultedFieldCodec {
    private DefaultedFieldCodec() {
    }

    /**
     * @deprecated Use the DFU equivalent
     */
    @Deprecated
    public static <A> MapCodec<A> of(Codec<A> codec, String name, Supplier<A> defaultSupplier) {
        return codec.optionalFieldOf(name).xmap(optional -> optional.orElseGet(defaultSupplier), Optional::of);
    }

    /**
     * @deprecated Use the DFU equivalent
     */
    @Deprecated
    public static <A> MapCodec<A> of(Codec<A> codec, String name, A defaultValue) {
        return codec.optionalFieldOf(name, defaultValue);
    }
}
