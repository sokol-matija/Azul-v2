package hr.algebra.azul.network.serialization;

import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.zip.*;
import java.io.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class GameStateSerializer {
    private static final Logger LOGGER = Logger.getLogger(GameStateSerializer.class.getName());
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] HMAC_KEY = "AzulGameStateSecret".getBytes();
    private static final int COMPRESSION_THRESHOLD = 1024;
    private static final int CACHE_SIZE = 10;

    private final ObjectMapper mapper;
    private final Map<String, SerializedState> stateCache;
    private final ExecutorService serializationExecutor;
    private final Queue<SerializationTask> taskQueue;
    private volatile boolean isRunning;

    public GameStateSerializer() {
        this.mapper = configureMapper();
        this.stateCache = new ConcurrentHashMap<>();
        this.serializationExecutor = Executors.newSingleThreadExecutor();
        this.taskQueue = new LinkedBlockingQueue<>();
        this.isRunning = true;
        startTaskProcessor();
    }

    private ObjectMapper configureMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        SimpleModule gameModule = new SimpleModule();
        gameModule.addSerializer(Game.class, new GameSerializer());
        gameModule.addDeserializer(Game.class, new GameDeserializer());

        mapper.registerModule(gameModule);
        return mapper;
    }

    private void startTaskProcessor() {
        serializationExecutor.submit(() -> {
            while (isRunning) {
                try {
                    SerializationTask task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        processTask(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.severe("Error processing serialization task: " + e.getMessage());
                }
            }
        });
    }

    public CompletableFuture<byte[]> serializeGameState(NetworkGameState state) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        taskQueue.offer(new SerializationTask(
                TaskType.SERIALIZE,
                state,
                null,
                future
        ));
        return future;
    }

    public CompletableFuture<NetworkGameState> deserializeGameState(byte[] data) {
        CompletableFuture<NetworkGameState> future = new CompletableFuture<>();
        taskQueue.offer(new SerializationTask(
                TaskType.DESERIALIZE,
                null,
                data,
                future
        ));
        return future;
    }

    private void processTask(SerializationTask task) {
        try {
            switch (task.type) {
                case SERIALIZE -> processSerialize(task);
                case DESERIALIZE -> processDeserialize(task);
            }
        } catch (Exception e) {
            task.future.completeExceptionally(e);
        }
    }

    private void processSerialize(SerializationTask task) {
        try {
            GameStateSnapshot snapshot = createSnapshot(task.state);
            String checksum = calculateStateChecksum(snapshot);
            snapshot = snapshot.withChecksum(checksum);

            byte[] serialized = mapper.writeValueAsBytes(snapshot);
            byte[] compressed = compressIfNeeded(serialized);

            cacheState(snapshot, compressed);

            task.future.complete(compressed);
        } catch (Exception e) {
            LOGGER.severe("Serialization failed: " + e.getMessage());
            task.future.completeExceptionally(e);
        }
    }

    private void processDeserialize(SerializationTask task) {
        try {
            byte[] decompressed = decompressIfNeeded(task.data);
            GameStateSnapshot snapshot = mapper.readValue(decompressed, GameStateSnapshot.class);

            if (!verifyStateChecksum(snapshot)) {
                throw new SecurityException("State checksum verification failed");
            }

            NetworkGameState state = reconstructGameState(snapshot);
            task.future.complete(state);
        } catch (Exception e) {
            LOGGER.severe("Deserialization failed: " + e.getMessage());
            task.future.completeExceptionally(e);
        }
    }

    private GameStateSnapshot createSnapshot(NetworkGameState state) {
        return new GameStateSnapshot(
                UUID.randomUUID().toString(),
                state.getGameId(),
                LocalDateTime.now(),
                compressGameState(state),
                createPlayerStates(state),
                null // checksum will be added later
        );
    }

    private CompressedGameState compressGameState(NetworkGameState state) {
        return new CompressedGameState(
                compressFactories(state.getGame().getFactories()),
                compressCentralArea(state.getGame().getCentralArea()),
                compressPlayerBoards(state.getGame().getPlayers()),
                state.getCurrentPlayerId(),
                state.getCurrentPhase()
        );
    }

    private List<CompressedFactory> compressFactories(List<Factory> factories) {
        return factories.stream()
                .map(factory -> new CompressedFactory(
                        factory.getTiles().stream()
                                .map(Tile::getColor)
                                .toList()
                ))
                .toList();
    }

    private CompressedCentralArea compressCentralArea(CentralArea area) {
        return new CompressedCentralArea(
                area.getTiles().stream()
                        .map(Tile::getColor)
                        .toList()
        );
    }

    private Map<String, CompressedPlayerBoard> compressPlayerBoards(List<Player> players) {
        return players.stream()
                .collect(Collectors.toMap(
                        Player::getName,
                        this::compressPlayerBoard
                ));
    }

    private CompressedPlayerBoard compressPlayerBoard(Player player) {
        return new CompressedPlayerBoard(
                player.getScore(),
                compressWall(player.getWall()),
                compressPatternLines(player.getPatternLines()),
                compressNegativeLine(player.getNegativeLine())
        );
    }

    private boolean[][] compressWall(Wall wall) {
        boolean[][] compressed = new boolean[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                compressed[i][j] = wall.hasTile(i, j);
            }
        }
        return compressed;
    }

    private List<List<TileColor>> compressPatternLines(PatternLines lines) {
        List<List<TileColor>> compressed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            compressed.add(lines.getLine(i).stream()
                    .map(Tile::getColor)
                    .toList());
        }
        return compressed;
    }

    private List<TileColor> compressNegativeLine(List<Tile> negativeLine) {
        return negativeLine.stream()
                .map(Tile::getColor)
                .toList();
    }

    private String calculateStateChecksum(GameStateSnapshot snapshot) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(HMAC_KEY, HMAC_ALGORITHM);
            mac.init(keySpec);

            String content = snapshot.id() + snapshot.gameId() +
                    snapshot.timestamp().toString() +
                    mapper.writeValueAsString(snapshot.gameState()) +
                    mapper.writeValueAsString(snapshot.playerStates());

            byte[] hash = mac.doFinal(content.getBytes());
            return Base64.getEncoder().encodeToString(hash);

        } catch (Exception e) {
            LOGGER.severe("Failed to calculate checksum: " + e.getMessage());
            throw new RuntimeException("Checksum calculation failed", e);
        }
    }

    private boolean verifyStateChecksum(GameStateSnapshot snapshot) {
        String calculatedChecksum = calculateStateChecksum(
                snapshot.withChecksum(null)
        );
        return calculatedChecksum.equals(snapshot.checksum());
    }

    private byte[] compressIfNeeded(byte[] data) throws IOException {
        if (data.length < COMPRESSION_THRESHOLD) {
            return data;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
            gzip.finish();
            return baos.toByteArray();
        }
    }

    private byte[] decompressIfNeeded(byte[] data) throws IOException {
        if (!isCompressed(data)) {
            return data;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzip = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    private boolean isCompressed(byte[] data) {
        return data[0] == (byte) GZIPInputStream.GZIP_MAGIC &&
                data[1] == (byte)(GZIPInputStream.GZIP_MAGIC >> 8);
    }

    private void cacheState(GameStateSnapshot snapshot, byte[] serialized) {
        SerializedState cachedState = new SerializedState(
                snapshot,
                serialized,
                System.currentTimeMillis()
        );

        stateCache.put(snapshot.id(), cachedState);
        cleanCache();
    }

    private void cleanCache() {
        if (stateCache.size() > CACHE_SIZE) {
            String oldestKey = stateCache.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().timestamp))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (oldestKey != null) {
                stateCache.remove(oldestKey);
            }
        }
    }

    public void cleanup() {
        isRunning = false;
        serializationExecutor.shutdownNow();
        stateCache.clear();
    }

    // Record classes for serialization
    private record GameStateSnapshot(
            String id,
            String gameId,
            LocalDateTime timestamp,
            CompressedGameState gameState,
            Map<String, CompressedPlayerBoard> playerStates,
            String checksum
    ) {
        public GameStateSnapshot withChecksum(String newChecksum) {
            return new GameStateSnapshot(
                    id, gameId, timestamp, gameState, playerStates, newChecksum
            );
        }
    }

    private record CompressedGameState(
            List<CompressedFactory> factories,
            CompressedCentralArea centralArea,
            Map<String, CompressedPlayerBoard> playerBoards,
            String currentPlayerId,
            NetworkGameState.GamePhase phase
    ) {}

    private record CompressedFactory(List<TileColor> tiles) {}
    private record CompressedCentralArea(List<TileColor> tiles) {}

    private record CompressedPlayerBoard(
            int score,
            boolean[][] wall,
            List<List<TileColor>> patternLines,
            List<TileColor> negativeLine
    ) {}

    private record SerializedState(
            GameStateSnapshot snapshot,
            byte[] serialized,
            long timestamp
    ) {}

    private enum TaskType {
        SERIALIZE,
        DESERIALIZE
    }

    private record SerializationTask(
            TaskType type,
            NetworkGameState state,
            byte[] data,
            CompletableFuture<?> future
    ) {}

    // Custom serializers
    private static class GameSerializer extends JsonSerializer<Game> {
        @Override
        public void serialize(Game game, JsonGenerator gen,
                              SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            // Implement game serialization
            gen.writeEndObject();
        }
    }

    private static class GameDeserializer extends JsonDeserializer<Game> {
        @Override
        public Game deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            ObjectCodec codec = p.getCodec();
            JsonNode node = codec.readTree(p);
            // Implement game deserialization
            return null;
        }
    }
}

