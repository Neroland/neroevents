package za.co.neroland.neroevents.event;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

public final class DefinitionFields {

    private DefinitionFields() {
    }

    public static <T> MapCodec<Optional<T>> optional(Codec<T> codec, String name) {
        //? if >=1.20.5 {
        return codec.optionalFieldOf(name);
        //?} else {
        /*return new MapCodec<Optional<T>>() {
            @Override
            public <U> DataResult<Optional<T>> decode(DynamicOps<U> ops, MapLike<U> input) {
                U value = input.get(name);
                return value == null ? DataResult.success(Optional.empty())
                        : codec.parse(ops, value).map(Optional::of);
            }

            @Override
            public <U> RecordBuilder<U> encode(Optional<T> value, DynamicOps<U> ops, RecordBuilder<U> prefix) {
                return value.isPresent() ? prefix.add(name, codec.encodeStart(ops, value.get())) : prefix;
            }

            @Override
            public <U> Stream<U> keys(DynamicOps<U> ops) {
                return Stream.of(ops.createString(name));
            }
        };
        *///?}
    }

    public static <T> MapCodec<T> optional(Codec<T> codec, String name, T fallback) {
        return optional(codec, name).xmap(value -> value.orElse(fallback),
                value -> Objects.equals(value, fallback) ? Optional.empty() : Optional.of(value));
    }
}
