package hr.algebra.azul.network.persistence;

import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.io.IOException;
import java.util.stream.Stream;

public class GameStatePersistenceManager {
    private static final Logger LOGGER = Logger.getLogger(GameStatePersistenceManager.class.getName());
    private static final String SAVE_DIR = "game_saves";
    private static final String TEMP_DIR = "temp_saves";
    private static final int MAX_SAVES_PER_GAME = 5;
    private static final long AUTO_SAVE_INTERVAL = 30000;

    private final String gameId;
    private final GameClient gameClient;
    private final Map<String, PlayerConnectionState> playerStates;
    private final ScheduledExecutorService autoSaveExecutor;
    private final ExecutorService taskExecutor;
    private final Queue<PersistenceTask> taskQueue;
    private volatile boolean isRunning;

    public GameStatePersistenceManager(String gameId, GameClient gameClient) {
        this.gameId = gameId;
        this.gameClient = gameClient;
        this.playerStates = new ConcurrentHashMap<>();
        this.autoSaveExecutor = Executors.newSingleThreadScheduledExecutor();
        this.taskExecutor = Executors.newSingleThreadExecutor();
        this.taskQueue = new ConcurrentLinkedQueue<>();
        this.isRunning = true;

        initializeDirectories();
        startTaskProcessor();
        startAutoSave();
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(SAVE_DIR));
            Files.createDirectories(Paths.get(TEMP_DIR));
        } catch (IOException e) {
            LOGGER.severe("Failed to create save directories: " + e.getMessage());
        }
    }

    private void startTaskProcessor() {
        taskExecutor.submit(() -> {
            while (isRunning) {
                try {
                    PersistenceTask task = taskQueue.poll();
                    if (task != null) {
                        processTask(task);
                    } else {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.severe("Error processing persistence task: " + e.getMessage());
                }
            }
        });
    }

    private void startAutoSave() {
        autoSaveExecutor.scheduleAtFixedRate(
                () -> saveGameState(SaveTrigger.AUTO),
                AUTO_SAVE_INTERVAL,
                AUTO_SAVE_INTERVAL,
                TimeUnit.MILLISECONDS
        );
    }

    public CompletableFuture<Boolean> saveGameState(SaveTrigger trigger) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        taskQueue.offer(new SaveStateTask(trigger, future));
        return future;
    }

    public CompletableFuture<Optional<NetworkGameState>> loadGameState(String saveId) {
        CompletableFuture<Optional<NetworkGameState>> future = new CompletableFuture<>();
        taskQueue.offer(new LoadStateTask(saveId, future));
        return future;
    }

    public CompletableFuture<Optional<NetworkGameState>> loadLatestState() {
        CompletableFuture<Optional<NetworkGameState>> future = new CompletableFuture<>();
        
        try {
            Path saveDir = Paths.get(SAVE_DIR);
            if (!Files.exists(saveDir)) {
                future.complete(Optional.empty());
                return future;
            }

            // Find the latest save file based on last modified time
            Optional<Path> latestSave;
            try (Stream<Path> files = Files.list(saveDir)) {
                latestSave = files
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> path.getFileName().toString().startsWith(gameId))
                    .max(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }));
            }

            if (latestSave.isPresent()) {
                String saveId = latestSave.get().getFileName().toString();
                // Remove .json extension and gameId prefix
                saveId = saveId.substring(gameId.length() + 1, saveId.length() - 5);
                return loadGameState(saveId);
            } else {
                future.complete(Optional.empty());
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to load latest state: " + e.getMessage());
            future.complete(Optional.empty());
        }

        return future;
    }

    private void processTask(PersistenceTask task) {
        try {
            if (task instanceof SaveStateTask saveTask) {
                processSaveTask(saveTask);
            } else if (task instanceof LoadStateTask loadTask) {
                processLoadTask(loadTask);
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to process task: " + e.getMessage());
            task.getFuture().completeExceptionally(e);
        }
    }

    private void processSaveTask(SaveStateTask task) {
        try {
            String saveId = UUID.randomUUID().toString();
            String filename = String.format("%s_%s.json", gameId, saveId);
            Path savePath = Paths.get(SAVE_DIR, filename);
            Path tempPath = Paths.get(TEMP_DIR, filename + ".tmp");

            NetworkGameState currentState = NetworkGameState.getCurrentState(gameId);
            GameStateSnapshot snapshot = new GameStateSnapshot(
                    saveId,
                    gameId,
                    LocalDateTime.now(),
                    task.trigger,
                    currentState
            );

            // Save to temporary file first
            Files.writeString(tempPath, snapshot.toString());
            Files.move(tempPath, savePath, StandardCopyOption.ATOMIC_MOVE);

            // Clean up old saves if we exceed the maximum
            cleanupOldSaves();

            task.getFuture().complete(true);
        } catch (Exception e) {
            LOGGER.severe("Failed to save game state: " + e.getMessage());
            task.getFuture().complete(false);
        }
    }

    private void cleanupOldSaves() {
        try {
            Path saveDir = Paths.get(SAVE_DIR);
            if (!Files.exists(saveDir)) {
                return;
            }

            List<Path> saves;
            try (Stream<Path> files = Files.list(saveDir)) {
                saves = files
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> path.getFileName().toString().startsWith(gameId))
                    .sorted(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).reversed())
                    .collect(java.util.stream.Collectors.toList());
            }

            // Remove oldest saves if we exceed the maximum
            if (saves.size() > MAX_SAVES_PER_GAME) {
                for (int i = MAX_SAVES_PER_GAME; i < saves.size(); i++) {
                    try {
                        Files.delete(saves.get(i));
                    } catch (IOException e) {
                        LOGGER.warning("Failed to delete old save: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to cleanup old saves: " + e.getMessage());
        }
    }

    private void processLoadTask(LoadStateTask task) {
        try {
            String filename = String.format("%s_%s.json", gameId, task.saveId);
            Path savePath = Paths.get(SAVE_DIR, filename);
            if (!Files.exists(savePath)) {
                task.getFuture().complete(Optional.empty());
                return;
            }

            String content = Files.readString(savePath);
            GameStateSnapshot snapshot = GameStateSnapshot.fromString(content);
            task.getFuture().complete(Optional.of(snapshot.gameState()));
        } catch (Exception e) {
            LOGGER.severe("Failed to load game state: " + e.getMessage());
            task.getFuture().complete(Optional.empty());
        }
    }

    public void cleanup() {
        isRunning = false;
        autoSaveExecutor.shutdownNow();
        taskExecutor.shutdownNow();
        cleanupTempFiles();
    }

    private void cleanupTempFiles() {
        try {
            Files.list(Paths.get(TEMP_DIR))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            LOGGER.warning("Failed to delete temp file: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.warning("Failed to cleanup temp directory: " + e.getMessage());
        }
    }

    private record GameStateSnapshot(
            String id,
            String gameId,
            LocalDateTime timestamp,
            SaveTrigger trigger,
            NetworkGameState gameState
    ) {
        public static GameStateSnapshot fromString(String content) {
            // Implementation of deserialization
            return null; // Placeholder
        }
    }

    private interface PersistenceTask {
        CompletableFuture<?> getFuture();
    }

    private record SaveStateTask(
            SaveTrigger trigger,
            CompletableFuture<Boolean> future
    ) implements PersistenceTask {
        @Override
        public CompletableFuture<?> getFuture() {
            return future;
        }
    }

    private record LoadStateTask(
            String saveId,
            CompletableFuture<Optional<NetworkGameState>> future
    ) implements PersistenceTask {
        @Override
        public CompletableFuture<?> getFuture() {
            return future;
        }
    }

    public enum SaveTrigger {
        AUTO,
        MANUAL,
        CHECKPOINT,
        DISCONNECTION,
        SHUTDOWN
    }
}
