package io.github.tst_038.minigami.paper.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.board.BoardPriority;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoardStackTest {

    private final List<String> shown = new ArrayList<>();
    private final BoardStack<String, String> stack = new BoardStack<>((player, board) -> shown.add(board));

    @Test
    void theHigherPriorityShowsWhateverTheOrder() {
        stack.add("p", "own", this, "plugin", BoardPriority.PLUGIN);
        stack.add("p", "own", this, "match", BoardPriority.MATCH);
        stack.add("p", "own", this, "night", BoardPriority.NIGHT);
        assertEquals(List.of("plugin", "match"), shown);
    }

    @Test
    void ofEqualPrioritiesTheLatestShows() {
        stack.add("p", "own", this, "first", BoardPriority.NIGHT);
        stack.add("p", "own", this, "second", BoardPriority.NIGHT);
        assertEquals(List.of("first", "second"), shown);
    }

    @Test
    void releasingTheShownClaimShowsTheNext() {
        stack.add("p", "own", this, "night", BoardPriority.NIGHT);
        var match = stack.add("p", "own", this, "match", BoardPriority.MATCH);
        stack.add("p", "own", this, "plugin", BoardPriority.PLUGIN);
        stack.release(match);
        assertEquals(List.of("night", "match", "night"), shown);
    }

    @Test
    void releasingAHiddenClaimChangesNothing() {
        var night = stack.add("p", "own", this, "night", BoardPriority.NIGHT);
        stack.add("p", "own", this, "match", BoardPriority.MATCH);
        stack.release(night);
        assertEquals(List.of("night", "match"), shown);
    }

    @Test
    void replacingAClaimByALaterOneOfTheSameKindNeverShowsTheOriginal() {
        var old = stack.add("p", "own", this, "night 1", BoardPriority.NIGHT);
        stack.add("p", "own", this, "night 2", BoardPriority.NIGHT);
        stack.release(old);
        assertEquals(List.of("night 1", "night 2"), shown);
    }

    @Test
    void theLastReleaseGivesBackTheBoardFromBeforeTheFirstClaim() {
        var night = stack.add("p", "own", this, "night", BoardPriority.NIGHT);
        var match = stack.add("p", "night", this, "match", BoardPriority.MATCH);
        stack.release(night);
        stack.release(match);
        assertEquals("own", shown.getLast());
    }

    @Test
    void aReleaseCountsOnce() {
        var claim = stack.add("p", "own", this, "night", BoardPriority.NIGHT);
        stack.release(claim);
        stack.release(claim);
        assertEquals(List.of("night", "own"), shown);
    }

    @Test
    void theOriginalIsRememberedAgainAfterEveryRelease() {
        stack.release(stack.add("p", "own", this, "night", BoardPriority.NIGHT));
        stack.release(stack.add("p", "changed", this, "night", BoardPriority.NIGHT));
        assertEquals("changed", shown.getLast());
    }

    @Test
    void playersDoNotShareClaims() {
        stack.add("a", "own a", this, "match", BoardPriority.MATCH);
        stack.release(stack.add("b", "own b", this, "night", BoardPriority.NIGHT));
        assertEquals(List.of("match", "night", "own b"), shown);
    }

    @Test
    void droppingAPlayerShowsNothingAndEndsTheirClaims() {
        var claim = stack.add("p", "own", this, "night", BoardPriority.NIGHT);
        stack.drop("p");
        stack.release(claim);
        assertEquals(List.of("night"), shown);
        assertTrue(stack.ownedBy(this).isEmpty());
    }

    @Test
    void aPlayerAfterDropStartsOver() {
        stack.add("p", "own", this, "night", BoardPriority.NIGHT);
        stack.drop("p");
        stack.release(stack.add("p", "new own", this, "night", BoardPriority.NIGHT));
        assertEquals("new own", shown.getLast());
    }

    @Test
    void aClaimIsOpenUntilReleasedOrDropped() {
        var released = stack.add("a", "own", this, "x", BoardPriority.PLUGIN);
        var dropped = stack.add("b", "own", this, "y", BoardPriority.PLUGIN);
        assertTrue(stack.isOpen(released) && stack.isOpen(dropped));
        stack.release(released);
        stack.drop("b");
        assertTrue(!stack.isOpen(released) && !stack.isOpen(dropped));
    }

    @Test
    void aPluginsClaimsAreFoundByOwner() {
        var other = new Object();
        stack.add("p", "own", this, "a", BoardPriority.PLUGIN);
        stack.add("q", "own", other, "b", BoardPriority.PLUGIN);
        assertEquals(1, stack.ownedBy(other).size());
    }
}
