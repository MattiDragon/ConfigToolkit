package io.github.mattidragon.configloader.api;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.FieldEncoder;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A codec for creating fields that have default values. Unlike other methods of using default values,
 * this one only uses the default for missing fields, and not when deserialization fails.
 * Besides the default value this class works exactly like {@link Codec#fieldOf(String)}.
 * @param <A> The type that this codec serializes.
 */
public class DefaultedFieldCodec<A> extends MapCodec<A> {
    private final Codec<A> codec;
    private final String name;
    private final Supplier<A> defaultSupplier;
    private final MapEncoder<A> encoder;

    private DefaultedFieldCodec(Codec<A> codec, String name, Supplier<A> defaultSupplier) {
        this.codec = codec;
        this.name = name;
        this.defaultSupplier = defaultSupplier;
        this.encoder = new FieldEncoder<>(name, codec);
    }

    public static <A> MapCodec<A> of(Codec<A> codec, String name, Supplier<A> defaultSupplier) {
        return new DefaultedFieldCodec<>(codec, name, defaultSupplier);
    }

    public static <A> MapCodec<A> of(Codec<A> codec, String name, A defaultValue) {
        return new DefaultedFieldCodec<>(codec, name, () -> defaultValue);
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return Stream.of(ops.createString(name));
    }

    @Override
    public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
        var value = input.get(name);
        if (value == null) {
            return DataResult.success(defaultSupplier.get());
        }
        return codec.parse(ops, value);
    }

    @Override
    public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return encoder.encode(input, ops, prefix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        var that = (DefaultedFieldCodec<?>) o;

        if (!codec.equals(that.codec)) return false;
        if (!name.equals(that.name)) return false;
        return defaultSupplier.equals(that.defaultSupplier);
    }

    @Override
    public int hashCode() {
        int result = codec.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + defaultSupplier.hashCode();
        return result;
    }
}
