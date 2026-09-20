// NOTE:
// This file is about logging updates to a file for reference by the player.
// If you're looking for logs sent to the terminal, look at the relevant code

package aesh.kai.client.log;

import aesh.kai.network.UpdateCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static aesh.kai.BlockUpdateViewer.LOGGER;
import static aesh.kai.client.BlockUpdateViewerClient.config;

public final class BULogger {
    private static final BlockingQueue<LogRecord> QUEUE = new LinkedBlockingQueue<>(config.logQueueMax);
    private static Connection con;
    private static Connection conQuery;
    private static PreparedStatement insert;

    private static volatile boolean running = false;
    private static Thread writerThread;

    public record LogRecord(ResourceKey<Level> dim, long gameTick, long pos, UpdateCollector.UpdateType type) {}

    private static final java.util.concurrent.atomic.AtomicLong totalEnqueued = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong totalFlushed = new java.util.concurrent.atomic.AtomicLong();
    private static final Object flushLock = new Object();

    private static final ExecutorService FLUSH_WAIT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BUV-FlushWait");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService QUERY_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BUV-DBQuery");
        t.setDaemon(true);
        return t;
    });

    public static final java.util.concurrent.atomic.AtomicLong totalDropped = new java.util.concurrent.atomic.AtomicLong();

    public static void enqueue(ResourceKey<Level> dim, long tick, UpdateCollector.UpdateEntry entry) {
        if(QUEUE.offer(new LogRecord(dim, tick, entry.pos(), entry.type()))) {
            totalEnqueued.incrementAndGet();
        } else {
            totalDropped.incrementAndGet();
        }
    }

    private static synchronized void flush(List<LogRecord> batch) {
        if(con == null || batch.isEmpty()) return;
        try {
            con.setAutoCommit(false);
            for(LogRecord r : batch) {
                insert.setString(1, r.dim().identifier().toString());
                insert.setLong(2, r.gameTick());
                insert.setLong(3, r.pos());
                insert.setInt(4, r.type().bit);
                insert.addBatch();
            }
            insert.executeBatch();
            con.commit();
            totalFlushed.addAndGet(batch.size());
        } catch(SQLException e) {
            LOGGER.error("Failed to flush log batch", e);
        } finally {
            synchronized(flushLock) {
                flushLock.notifyAll();
            }
        }
    }

    public static CompletableFuture<Void> forceFlushAsync() {
        if(!running) {
            return CompletableFuture.runAsync(() -> {
                List<LogRecord> pending = new ArrayList<>();
                QUEUE.drainTo(pending);
                if(!pending.isEmpty()) flush(pending);
            }, FLUSH_WAIT_EXECUTOR);
        }

        long target = totalEnqueued.get();
        return CompletableFuture.runAsync(() -> {
            long deadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            synchronized(flushLock) {
                while(totalFlushed.get() < target) {
                    long remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000;
                    if(remainingMs <= 0) {
                        LOGGER.warn("Timed out waiting for pending writes to flush");
                        return;
                    }
                    try {
                        flushLock.wait(Math.min(remainingMs, 50));
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, FLUSH_WAIT_EXECUTOR);
    }

    private static void writerLoop() {
        List<LogRecord> batch = new ArrayList<>(4096);
        while(running) {
            try {
                batch.add(QUEUE.take());
                QUEUE.drainTo(batch, 20000);
                try {
                    flush(batch);
                } catch(RuntimeException e) {
                    LOGGER.error("Log writer failed to flush a batch of {} records; dropping and continuing", batch.size(), e);
                }
                batch.clear();
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        LOGGER.warn("LogWriter exiting... [ running = {} ]", running);
    }

    public static void startWriterThread() {
        if(running) return;
        running = true;
        writerThread = new Thread(BULogger::writerLoop, "LogWriter");
        writerThread.setDaemon(true);
        writerThread.start();

        if(config.verboseLogging) LOGGER.info("Started LogWriter");
    }

    public static void stopWriterThread() {
        running = false;
        if(writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(3_000); // wait for writerThread to finish. up to 3 sec
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Interrupted whilst waiting for writerThread to finish", e);
            }
        }

        if(!QUEUE.isEmpty()) {
            List<LogRecord> remaining = new ArrayList<>();
            QUEUE.drainTo(remaining);
            if(!remaining.isEmpty()) {
                flush(remaining);
            }
        }

        try {
            if(insert != null) insert.close();
            if(con != null) con.close();
            if(conQuery != null) conQuery.close();
        } catch(SQLException e) {
            LOGGER.error("Failed to close database resources", e);
        }

        if(config.verboseLogging) LOGGER.info("Stopped update logger writerThread");
    }

    public static void open(Path dbPath) {
        try {
            con = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try(Statement s = con.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("""
                            CREATE TABLE IF NOT EXISTS updates (
                                dimension TEXT NOT NULL,
                                tick      INTEGER NOT NULL,
                                pos       INTEGER NOT NULL,
                                type      INTEGER NOT NULL
                            )
                        """);
                s.execute("CREATE INDEX IF NOT EXISTS idx_updates_dim_tick ON updates(dimension, tick)");
            }
            insert = con.prepareStatement(
                    "INSERT INTO updates (dimension, tick, pos, type) VALUES (?, ?, ?, ?)"
            );

            conQuery = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try(Statement s = conQuery.createStatement()) {
                s.execute("PRAGMA query_only=1");
            }
        } catch(SQLException e) {
            LOGGER.error("Failed to open log database at {}", dbPath, e);
        }
    }

    public record LogEntry(String dim, long tick, long packedPos, int typeOrdinal) {
        public BlockPos pos() {
            return BlockPos.of(packedPos);
        }

        public UpdateCollector.UpdateType type() { return UpdateCollector.UpdateType.fromBit(typeOrdinal); }
    }

    public enum SortKey {
        DIMENSION("dimension"),
        TICK("tick"),
        POS("pos"),
        TYPE("type"),
        ROWID("rowid");

        private final String column;

        SortKey(String column) {
            this.column = column;
        }

        public String column() {
            return column;
        }
    }

    public record SortSpec(SortKey key, boolean ascending) {}

    public record QueryFilter(
            String dimension,
            Long minTick,
            Long maxTick,
            Long packedPos,
            Integer typeOrdinal,
            List<SortSpec> sort,   // primary, secondary, tertiary, ... - Can be left empty
            long limit
    ) {}

    public static CompletableFuture<List<LogEntry>> queryAsync(QueryFilter filter) {
        return CompletableFuture.supplyAsync(() -> queryBlocking(filter), QUERY_EXECUTOR);
    }

    private static List<LogEntry> queryBlocking(QueryFilter filter) {
        List<LogEntry> results = new ArrayList<>();
        if(conQuery == null) return results;

        StringBuilder sql = new StringBuilder("SELECT dimension, tick, pos, type FROM updates WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if(filter.dimension() != null) {
            sql.append(" AND dimension = ?");
            params.add(filter.dimension());
        }
        if(filter.minTick() != null) {
            sql.append(" AND tick >= ?");
            params.add(filter.minTick());
        }
        if(filter.maxTick() != null) {
            sql.append(" AND tick <= ?");
            params.add(filter.maxTick());
        }
        if(filter.packedPos() != null) {
            sql.append(" AND pos = ?");
            params.add(filter.packedPos());
        }
        if(filter.typeOrdinal() != null) {
            sql.append(" AND type = ?");
            params.add(filter.typeOrdinal());
        }

        if(!filter.sort().isEmpty()) {
            sql.append(" ORDER BY ");
            for(int i = 0; i < filter.sort().size(); i++) {
                if(i > 0) sql.append(", ");
                SortSpec spec = filter.sort().get(i);
                sql.append(spec.key().column()).append(spec.ascending() ? " ASC" : " DESC");
            }
        }

        sql.append(" LIMIT ").append(Math.max(1, filter.limit()));

        try(PreparedStatement ps = conQuery.prepareStatement(sql.toString())) {
            for(int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    results.add(new LogEntry(
                            rs.getString("dimension"),
                            rs.getLong("tick"),
                            rs.getLong("pos"),
                            rs.getInt("type")));
                }
            }
        } catch(SQLException e) {
            LOGGER.error("Failed to run filtered query", e);
        }
        return results;
    }
}