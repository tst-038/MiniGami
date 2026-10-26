package io.github.tst_038.minigami.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScopeTest {

    private final List<String> closed = new ArrayList<>();

    private AutoCloseable resource(String name) {
        return () -> closed.add(name);
    }

    @Test
    void registerReturnsTheSameResource() {
        var scope = new Scope();
        var resource = resource("a");

        assertSame(resource, scope.register(resource));
    }

    @Test
    void closesResourcesInReverseOrder() {
        var scope = new Scope();
        scope.register(resource("first"));
        scope.register(resource("second"));
        scope.register(resource("third"));

        scope.close();

        assertEquals(List.of("third", "second", "first"), closed);
    }

    @Test
    void closingTwiceClosesResourcesOnce() {
        var scope = new Scope();
        scope.register(resource("a"));

        scope.close();
        scope.close();

        assertEquals(List.of("a"), closed);
        assertTrue(scope.isClosed());
    }

    @Test
    void failingResourceDoesNotStopTheOthers() {
        var scope = new Scope();
        scope.register(resource("first"));
        scope.register(() -> { throw new IllegalArgumentException("boom"); });
        scope.register(resource("third"));

        var thrown = assertThrows(IllegalStateException.class, scope::close);

        assertEquals(List.of("third", "first"), closed);
        assertEquals("boom", thrown.getCause().getMessage());
    }

    @Test
    void laterFailuresAreAttachedAsSuppressed() {
        var scope = new Scope();
        scope.register(() -> { throw new IllegalArgumentException("second"); });
        scope.register(() -> { throw new IllegalArgumentException("first"); });

        var thrown = assertThrows(IllegalStateException.class, scope::close);

        assertEquals("first", thrown.getCause().getMessage());
        assertEquals("second", thrown.getSuppressed()[0].getMessage());
    }

    @Test
    void closingParentClosesChildInOrder() {
        var parent = new Scope();
        parent.register(resource("parent-before"));
        var child = parent.child();
        child.register(resource("child"));
        parent.register(resource("parent-after"));

        parent.close();

        assertEquals(List.of("parent-after", "child", "parent-before"), closed);
        assertTrue(child.isClosed());
    }

    @Test
    void closingChildLeavesParentOpen() {
        var parent = new Scope();
        parent.register(resource("parent"));
        var child = parent.child();
        child.register(resource("child"));

        child.close();

        assertEquals(List.of("child"), closed);
        assertFalse(parent.isClosed());

        parent.close();

        assertEquals(List.of("child", "parent"), closed);
    }

    @Test
    void registeringOnClosedScopeClosesResourceImmediately() {
        var scope = new Scope();
        scope.close();

        assertThrows(IllegalStateException.class, () -> scope.register(resource("late")));
        assertEquals(List.of("late"), closed);
    }

    @Test
    void creatingChildOfClosedScopeFails() {
        var scope = new Scope();
        scope.close();

        assertThrows(IllegalStateException.class, scope::child);
    }
}
