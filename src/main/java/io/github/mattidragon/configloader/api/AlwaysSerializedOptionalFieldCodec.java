package io.github.mattidragon.configloader.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

public class AlwaysSerializedOptionalFieldCodec {
    private AlwaysSerializedOptionalFieldCodec() {}

    /**
     * Creates a {@link MapCodec} that acts like {@link Codec#optionalFieldOf(String, Object)} except that it is always serialized.
     * This is useful when you want to expose default values to config users.
     * @param codec The codec to serialize present values with
     * @param name The name of the field
     * @param defaultValue The default value
     * @return The created map codec
     * @param <A> The type of codec being dealt with
     */
    public static <A> MapCodec<A> create(Codec<A> codec, String name, A defaultValue) {
        return codec.optionalFieldOf(name).xmap(optional -> optional.orElse(defaultValue), Optional::of);
    }
}
