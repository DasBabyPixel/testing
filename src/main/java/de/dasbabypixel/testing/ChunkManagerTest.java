package de.dasbabypixel.testing;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class ChunkManagerTest {
    public static void main(String[] args) {
//        testCacheConcurrency();
        test1();
    }

    public static void testCacheConcurrency() {
//        var executor = new PrioritizedExecutor.Default(32);
//        var cache = new ChunkCache<>(generator(), executor, (chunkX, chunkY, data) -> {
//
//        });
//        ConcurrentSkipListSet<ChunkCache.Ticket>[] tickets = new ConcurrentSkipListSet[10000];
//        Arrays.setAll(tickets, value -> new ConcurrentSkipListSet<ChunkCache.Ticket>());
        var service = Executors.newCachedThreadPool();
        var set = new ConcurrentSkipListSet<ChunkCache.Ticket>();
        service.submit(() -> {
            try {
                var last = set.last();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
//        for (int i = 0; i < 10; i++) {
//            var finalI = i;
//            service.submit(() -> {
//                for (int i2 = 0; i2 < 10000; i2++) {
//                    for (int i1 = 0; i1 < 10000000; i1++) {
//                        var ticket = new ChunkCache.Ticket(finalI);
//                        var set = tickets[i1 % tickets.length];
//                        if (!set.add(ticket)) System.out.println("Could not add");
////                        System.out.println(set.last() + " -> " + ticket);
//                        if (!set.remove(ticket)) System.out.println("Could not remove");
//                    }
//                    System.out.println("DOne 1");
//                }
//            });
//        }
    }

    public static void test1() {

        var priorityCount = 8;
        var radius = (float) priorityCount - 1;
        var frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 800);
        frame.setLocation(400, 250);
        AtomicInteger ai = new AtomicInteger();

        var image = new BufferedImage(200, 200, BufferedImage.OPAQUE);
        int rgb = 0xFFFFFF;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, rgb);
            }
        }
        var icon = new ImageIcon(image.getScaledInstance(800, 800, BufferedImage.SCALE_FAST));
        var label = new JLabel(icon);
        frame.add(label);
        var loadedCount = new AtomicInteger();
        var chunkManager = chunkManager(image, icon, frame, priorityCount, loadedCount, ai);
        var priorityCalculator = new PriorityCalculator() {
            @Override
            public int priority(int centerX, int centerY, int relativeX, int relativeY) {
                int distXSq = relativeX * relativeX;
                int distYSq = relativeY * relativeY;
                int prio = (int) Math.sqrt(distXSq + distYSq);
                return Math.clamp(prio, 0, priorityCount - 1);
            }
        };

        frame.setVisible(true);

        var playerTicketManagers = new PlayerTicketManager[50];
        Arrays.setAll(playerTicketManagers, value -> new PlayerTicketManager(chunkManager, priorityCalculator, radius));

        var service = Executors.newCachedThreadPool(r -> {
            var thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
        for (var playerTicketManager : playerTicketManagers) {
            var simulator = new RandomMovementSimulator(playerTicketManager, 0, 0, image.getWidth(), image.getHeight());
            playerTicketManager.move(simulator.x(), simulator.y());
            service.submit(() -> {
                int i = 1;
                while (!playerTicketManager.unloaded()) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
                    long time1 = System.nanoTime();
                    synchronized (playerTicketManager) {
                        if (playerTicketManager.unloaded()) return;
                        i = -i;
                        simulator.tick();
                    }
                    System.out.println(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - time1));
                    if (Thread.interrupted()) return;
                }
            });
//            break;
        }
        frame.addWindowListener(windowListener(chunkManager, service));

        MouseAdapter a = new MouseAdapter() {
            final IntList down = new IntArrayList();

            @Override
            public void mousePressed(MouseEvent e) {
                int x = e.getX() - (label.getWidth() - image.getWidth() * 4) / 2;
                int y = e.getY() - (label.getHeight() - image.getHeight() * 4) / 2;
                down.add(e.getButton());
                if (e.getButton() == MouseEvent.BUTTON1) {
                    service.submit(() -> {
                        playerTicketManagers[0].move(x / 4, y / 4);
                    });
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    service.submit(() -> {
                        playerTicketManagers[1].move(x / 4, y / 4);
                    });
                } else if (e.getButton() == MouseEvent.BUTTON2) {
                    System.out.println(loadedCount.get());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                down.rem(e.getButton());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int x = e.getX() - (label.getWidth() - image.getWidth() * 4) / 2;
                int y = e.getY() - (label.getHeight() - image.getHeight() * 4) / 2;
                for (int btn : down) {
                    if (btn == MouseEvent.BUTTON1) {
                        try {
                            service.submit(() -> {
                                playerTicketManagers[0].move(x / 4, y / 4);
                            }).get();
                        } catch (InterruptedException | ExecutionException ex) {
                            ex.printStackTrace();
                        }
                    } else if (btn == MouseEvent.BUTTON3) {
                        try {
                            service.submit(() -> {
                                playerTicketManagers[1].move(x / 4, y / 4);
                            }).get();
                        } catch (InterruptedException | ExecutionException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        };
        label.addMouseListener(a);
        label.addMouseMotionListener(a);


        Timer timer = new Timer(50, e -> {
            ai.incrementAndGet();
            icon.setImage(image.getScaledInstance(800, 800, BufferedImage.SCALE_FAST));
            frame.repaint();
        });
        timer.start();

        var thread = new Thread(() -> {
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(200));
            for (int i = 0; i < playerTicketManagers.length; i++) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                playerTicketManagers[i].unload();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @NotNull
    private static ChunkManager<Color> chunkManager(BufferedImage image, ImageIcon icon, JFrame frame, int priorityCount, AtomicInteger loadedCount, AtomicInteger ai) {
        var callbacks = callbacks(image, icon, frame, loadedCount, ai);
        return new ChunkManager<>(callbacks, priorityCount, generator());
    }

    @NotNull
    private static ChunkManager.Callbacks<Color> callbacks(BufferedImage image, ImageIcon icon, JFrame frame, AtomicInteger loadedCount, AtomicInteger ai) {
        Runnable redraw = new Runnable() {
            private volatile boolean redrawing = false;
            private volatile boolean again = false;
            private long lastDraw;

            @Override
            public void run() {
                ai.incrementAndGet();
//                if (redrawing) {
//                    again = true;
//                    return;
//                }
//                redrawing = true;
//                work();
            }

            private void work() {
                SwingUtilities.invokeLater(this::work0);
            }

            private void work0() {
                long pause = System.currentTimeMillis() - lastDraw;
                lastDraw = System.currentTimeMillis();
                if (pause > 100) {
                    System.out.println(pause);
                }
                icon.setImage(image.getScaledInstance(800, 800, BufferedImage.SCALE_FAST));
                frame.repaint();
                if (again) {
                    again = false;
                    work();

                    return;
                }
                redrawing = false;
            }
        };

        return new ChunkManager.Callbacks<>() {
            @Override
            public void loaded(int chunkX, int chunkY) {
                loadedCount.incrementAndGet();
                if (chunkX < 0 || chunkX >= image.getWidth() || chunkY < 0 || chunkY >= image.getHeight()) return;
            }

            @Override
            public void unloaded(int chunkX, int chunkY) {
                loadedCount.decrementAndGet();
                if (chunkX < 0 || chunkX >= image.getWidth() || chunkY < 0 || chunkY >= image.getHeight()) return;
                image.setRGB(chunkX, chunkY, 0xFFFFFF);
                redraw.run();
            }

            @Override
            public void generated(int chunkX, int chunkY, Color data) {
                if (chunkX < 0 || chunkX >= image.getWidth() || chunkY < 0 || chunkY >= image.getHeight()) return;
//                    int prev = image.getRGB(chunkX,chunkY);
//                    System.out.println(Integer.toHexString(prev));
                image.setRGB(chunkX, chunkY, data.getRGB());
                redraw.run();

            }
        };
    }

    private static WindowListener windowListener(ChunkManager<?> chunkManager, ExecutorService service) {
        return new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }

            @Override
            public void windowClosing(WindowEvent e) {

            }

            @Override
            public void windowClosed(WindowEvent e) {
                service.shutdownNow();
                try {
                    service.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                chunkManager.shutdown();
            }

            @Override
            public void windowIconified(WindowEvent e) {

            }

            @Override
            public void windowDeiconified(WindowEvent e) {

            }

            @Override
            public void windowActivated(WindowEvent e) {

            }

            @Override
            public void windowDeactivated(WindowEvent e) {

            }
        };
    }

    private static Long2ObjectFunction<Color> generator() {
        return key -> {
//            if (ThreadLocalRandom.current().nextDouble() < 0.1)
//                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
//            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(ThreadLocalRandom.current().nextInt(5)));
            return Color.BLACK;
        };
    }
}
