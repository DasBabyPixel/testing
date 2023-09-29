package de.dasbabypixel.testing;

import java.util.concurrent.ThreadLocalRandom;

public class RandomMovementSimulator {

    private final PlayerTicketManager playerTicketManager;
    private final float minX, minY, width, height;
    private float x;
    private float y;
    private float dirX;
    private float dirY;

    public RandomMovementSimulator(PlayerTicketManager playerTicketManager, float minX, float minY, float width, float height) {
        this.playerTicketManager = playerTicketManager;
        this.minX = minX;
        this.minY = minY;
        this.width = width;
        this.height = height;
        this.x = ThreadLocalRandom.current().nextFloat(width) + minX;
        this.y = ThreadLocalRandom.current().nextFloat(height) + minY;
    }

    public int x() {
        return (int) x;
    }

    public int y() {
        return (int) y;
    }

    public void tick() {
        var random = ThreadLocalRandom.current();
        if (Math.abs(x - playerTicketManager.x()) >= 1) x = playerTicketManager.x();
        if (Math.abs(y - playerTicketManager.y()) >= 1) y = playerTicketManager.y();

        x += random.nextFloat() * 2 - 1;
        y += random.nextFloat() * 2 - 1;
        while (x < minX) x += width;
        while (x > minX + width) x -= width;
        while (y < minY) y += height;
        while (y > minY + height) y -= height;
        playerTicketManager.move((int) x, (int) y);
    }
}
