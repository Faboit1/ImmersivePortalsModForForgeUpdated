package qouteall.q_misc_util.my_util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.stream.Stream;

/**
 * Minecraft 26 saves entities through {@link ValueInput}/{@link ValueOutput}.
 * This bridges them to {@link CompoundTag} so the existing (de)serialization code
 * and the save format stay the same.
 */
public final class NbtBridge {
    /**
     * Reads/writes all fields of the current object as a compound tag.
     */
    public static final MapCodec<CompoundTag> WHOLE_COMPOUND = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }
        
        @Override
        public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            CompoundTag tag = new CompoundTag();
            input.entries().forEach(pair -> {
                String key = ops.getStringValue(pair.getFirst()).result().orElse(null);
                if (key != null) {
                    tag.put(key, ops.convertTo(NbtOps.INSTANCE, pair.getSecond()));
                }
            });
            return DataResult.success(tag);
        }
        
        @Override
        public <T> RecordBuilder<T> encode(CompoundTag value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (String key : value.keySet()) {
                Tag element = value.get(key);
                if (element != null) {
                    prefix.add(key, NbtOps.INSTANCE.convertTo(ops, element));
                }
            }
            return prefix;
        }
    };
    
    @SuppressWarnings("deprecation")
    public static CompoundTag readAll(ValueInput input) {
        return input.read(WHOLE_COMPOUND).orElseGet(CompoundTag::new);
    }
    
    @SuppressWarnings("deprecation")
    public static void writeAll(ValueOutput output, CompoundTag tag) {
        output.store(WHOLE_COMPOUND, tag);
    }
    
    public static CompoundTag saveEntityWithoutId(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, entity.registryAccess()
        );
        entity.saveWithoutId(output);
        return output.buildResult();
    }
    
    public static void loadEntity(Entity entity, CompoundTag tag) {
        HolderLookup.Provider lookup = entity.registryAccess();
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, lookup, tag));
    }
}
