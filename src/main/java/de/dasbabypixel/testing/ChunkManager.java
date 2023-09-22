package de.dasbabypixel.testing;

public class ChunkManager<T> {
    private final ChunkCache<T> chunkCache = new ChunkCache<>();

    private static int chunk(int v) {
        return v >> 4;
    }

    public void require(int x, int y) {
        var cacheEntry = chunkCache.require(chunk(x), chunk(y));
    }

    public void release(int x, int y) {
        var cacheEntry = chunkCache.release(chunk(x), chunk(y));
    }
}
