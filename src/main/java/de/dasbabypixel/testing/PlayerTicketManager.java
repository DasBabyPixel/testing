package de.dasbabypixel.testing;

import java.util.HashSet;
import java.util.Set;

public class PlayerTicketManager {

    private final ChunkManager<?> chunkManager;
    private final PriorityCalculator priorityCalculator;
    private ChunkCache.Ticket[] tickets;
    private ChunkCache.Ticket[] oldTickets;
    private int[] offsets;
    private float radius = -1;
    private boolean unloaded = true;
    private int x = 0;
    private int y = 0;

    public PlayerTicketManager(ChunkManager<?> chunkManager, PriorityCalculator priorityCalculator, float radius) {
        this.chunkManager = chunkManager;
        this.priorityCalculator = priorityCalculator;
        resize(radius);
    }

    public void resize(float radius) {
        synchronized (this) {
            if (this.radius == radius) return;
            unload();
            this.radius = radius;
            offsets = MathHelper.bresenheimOffsets(radius);
            tickets = new ChunkCache.Ticket[offsets.length];
            oldTickets = new ChunkCache.Ticket[offsets.length];
        }
    }

    public boolean unloaded() {
        synchronized (this) {
            return unloaded;
        }
    }

    public int x() {
        synchronized (this) {
            return x;
        }
    }

    public int y() {
        synchronized (this) {
            return y;
        }
    }

    public void unload() {
        synchronized (this) {
            if (unloaded) return;
            unloaded = true;
            for (int i = 0; i < offsets.length; i++) {
                var offset = offsets[i];
                var relativeX = (int) BinaryOperations.x(offset);
                var relativeY = (int) BinaryOperations.y(offset);
                chunkManager.release(x + relativeX, y + relativeY, tickets[i]);
                tickets[i] = null;
            }
        }
    }

    public void move(int newX, int newY) {
        synchronized (this) {
            var oldX = x;
            var oldY = y;

            for (int i = 0; i < offsets.length; i++) {
                var offset = offsets[i];
                var relativeX = (int) BinaryOperations.x(offset);
                var relativeY = (int) BinaryOperations.y(offset);
                var priority = priorityCalculator.priority(newX, newY, relativeX, relativeY);

                // Load the coordinate
                var result = chunkManager.require(newX + relativeX, newY + relativeY, priority);
                oldTickets[i] = tickets[i];
                tickets[i] = result.ticket();
            }
            if (!unloaded) for (int i = 0; i < offsets.length; i++) {
                var offset = offsets[i];
                var relativeX = (int) BinaryOperations.x(offset);
                var relativeY = (int) BinaryOperations.y(offset);
                chunkManager.release(oldX + relativeX, oldY + relativeY, oldTickets[i]);
                oldTickets[i] = null;
            }
            x = newX;
            y = newY;
            unloaded = false;
        }
    }

}
