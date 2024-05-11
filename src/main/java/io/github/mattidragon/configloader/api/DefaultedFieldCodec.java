package io.github.mattidragon.configloader.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * @deprecated The DFU {@link Codec#optionalFieldOf(String, Object)} method no longer fails silently as of minecraft 1.20.5,
 * and can thus be used safely in a config context. 
 * It can be used when it's desired for default values not to be serialized. If serialization of default values is desired, 
 * {@link AlwaysSerializedOptionalFieldCodec} can be used instead.
 */
@Deprecated
public class DefaultedFieldCodec {
    private DefaultedFieldCodec() {
    }
    
    @Deprecated
    public static <A> MapCodec<A> of(Codec<A> codec, String name, Supplier<A> defaultSupplier) {
        return codec.optionalFieldOf(name).xmap(optional -> optional.orElseGet(defaultSupplier), Optional::of);
    }

    @Deprecated
    public static <A> MapCodec<A> of(Codec<A> codec, String name, A defaultValue) {
        return AlwaysSerializedOptionalFieldCodec.create(codec, name, defaultValue);
    }
}
