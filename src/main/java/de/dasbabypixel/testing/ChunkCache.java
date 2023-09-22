package de.dasbabypixel.testing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static de.dasbabypixel.testing.BinaryOperations.combine;

public class ChunkCache<T> {
    private static final VarHandle VALUE;
    private static final VarHandle COUNT;

    static {
        try {
            var lookup = MethodHandles.lookup();
            VALUE = lookup.findVarHandle(Entry.class, "value", Object.class);
            COUNT = lookup.findVarHandle(Entry.class, "count", int.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }


    // A cache to load Entry instances. Also ensures there are never multiple entry instances with the same key
    private final LoadingCache<Long, Entry<T>> loader = Caffeine.newBuilder().weakValues().build(Entry::new);
    // The cache where all entries are saved
    private final LoadingCache<Long, Entry<T>> cache = Caffeine.newBuilder().build(loader::get);

    public Entry<T> require(int chunkX, int chunkY) {
        var key = combine(chunkX, chunkY);
        var entry = loader.get(key);
        if (entry.require() == 0) {
            // We are the first, so we got to save the entry into the cache
            cache.put(key, entry);
        }
        return entry;
    }

    public @Nullable Entry<T> entry(int chunkX, int chunkY) {
        return cache.get(combine(chunkX, chunkY));
    }

    public @Nullable Entry<T> release(int chunkX, int chunkY) {
        var key = combine(chunkX, chunkY);
        var entry = cache.getIfPresent(key);
        if (entry == null) throw new IllegalStateException("Wrong reference counting");
        return entry;
    }

    public static class Entry<T> {
        private T value;
        private int count;

        public Entry(long key) {
        }

        private int require() {
            return (int) COUNT.getAndAdd(this, 1);
        }

        private boolean release() {
            var n = (int) COUNT.getAndAdd(this, -1) - 1;
            if (n < 0) throw new IllegalStateException();
            return n == 0;
        }
    }
}
