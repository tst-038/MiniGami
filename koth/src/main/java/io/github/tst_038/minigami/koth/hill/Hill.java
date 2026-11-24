package io.github.tst_038.minigami.koth.hill;

import io.github.tst_038.minigami.core.capture.Capture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The hill of one KOTH round: who holds it, who is taking it, and the points the holder earns. A player
 * who stands on it alone for the capture time takes it and keeps it until someone else does, or until
 * {@link #drop()}. Server thread only.
 */
public final class Hill {

    private final int captureSeconds;
    private final Scoring scoring;
    private Capture<UUID> capture;
    private long heldSeconds;

    public Hill(int captureSeconds, Scoring scoring) {
        this.captureSeconds = captureSeconds;
        this.scoring = scoring;
        this.capture = new Capture<>(captureSeconds, true);
    }

    /**
     * One second passes. A capture earns nothing that second; the new holder starts counting from it.
     *
     * @param onPoint the players standing on the hill, each once
     */
    public HillTick tick(List<UUID> onPoint) {
        if (capture.tick(onPoint)) {
            heldSeconds = 0;
            return new HillTick(capture.owner(), Optional.empty(), 0);
        }
        if (capture.owner().isEmpty()) {
            return HillTick.NOTHING;
        }
        return ++heldSeconds % scoring.everySeconds() == 0
                ? new HillTick(Optional.empty(), capture.owner(), scoring.points())
                : HillTick.NOTHING;
    }

    /** The holder is gone: the hill is free again. */
    public void drop() {
        capture = new Capture<>(captureSeconds, true);
        heldSeconds = 0;
    }

    public Optional<UUID> holder() {
        return capture.owner();
    }

    /** The player taking the hill, while it is not done. */
    public Optional<UUID> taker() {
        return capture.taker();
    }

    /** Seconds the taker has stood on it alone so far. */
    public int progress() {
        return capture.progress();
    }
}
