package com.pvpindex.battles.practice.bot;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NmsSpawnHelper}.
 *
 * <p>These tests run on a plain JVM — no Paper server present.  The intent is to
 * verify the reflection logic in isolation:</p>
 * <ul>
 *   <li>Methods that search for specific NMS classes return {@code null} / throw
 *       predictably when those classes are absent.</li>
 *   <li>Methods that operate on arbitrary {@code Object} instances (e.g.
 *       {@code addFreshEntityReflective}, {@code tryRemoveEntity}) behave
 *       correctly given in-test proxy objects.</li>
 * </ul>
 */
class NmsSpawnHelperTest {

    // ── resolveClientInformationDefault ──────────────────────────────────────

    @Test
    void resolveClientInformationDefault_noNmsClasses_returnsNull() {
        // In the test JVM there are no net.minecraft.* classes; expect null, not an
        // exception.
        assertNull(NmsSpawnHelper.resolveClientInformationDefault());
    }

    // ── resolveServerPlayerConstructor ───────────────────────────────────────

    @Test
    void resolveServerPlayerConstructor_4ParamClass_returnsConstructor()
            throws NoSuchMethodException {
        Constructor<?> ctor =
                NmsSpawnHelper.resolveServerPlayerConstructor(Fake4ParamClass.class);
        assertNotNull(ctor);
        assertEquals(4, ctor.getParameterCount());
    }

    @Test
    void resolveServerPlayerConstructor_noMatchingConstructor_throws() {
        // Object has only a 0-param constructor — should throw.
        assertThrows(NoSuchMethodException.class,
                () -> NmsSpawnHelper.resolveServerPlayerConstructor(Object.class));
    }

    @Test
    void resolveServerPlayerConstructor_2ParamClass_throws() {
        assertThrows(NoSuchMethodException.class,
                () -> NmsSpawnHelper.resolveServerPlayerConstructor(Fake2ParamClass.class));
    }

    // ── addFreshEntityReflective ─────────────────────────────────────────────

    @Test
    void addFreshEntityReflective_1ParamMethod_invoked() throws Exception {
        FakeLevel1Param level = new FakeLevel1Param();
        Object entity = new Object();
        NmsSpawnHelper.addFreshEntityReflective(level, entity);
        assertTrue(level.called, "addFreshEntity(Object) should have been called");
        assertSame(entity, level.receivedEntity);
    }

    @Test
    void addFreshEntityReflective_2ParamMethod_invoked() throws Exception {
        FakeLevel2Param level = new FakeLevel2Param();
        Object entity = new Object();
        NmsSpawnHelper.addFreshEntityReflective(level, entity);
        assertTrue(level.called, "addFreshEntity(Object, FakeSpawnReason) should have been called");
    }

    @Test
    void addFreshEntityReflective_noMatchingMethod_throwsNoSuchMethodException() {
        // Plain Object has no addFreshEntity method.
        assertThrows(NoSuchMethodException.class,
                () -> NmsSpawnHelper.addFreshEntityReflective(new Object(), new Object()));
    }

    // ── tryRemoveEntity ───────────────────────────────────────────────────────

    @Test
    void tryRemoveEntity_nullLevel_doesNotThrow() {
        // nmsLevel is null — the method must silently skip removePlayerImmediately
        // and attempt the entity-level fallbacks.
        assertDoesNotThrow(() -> NmsSpawnHelper.tryRemoveEntity(null, new FakeDiscardable()));
    }

    @Test
    void tryRemoveEntity_entityWithDiscardMethod_fallbackInvoked() {
        // No NMS RemovalReason in the test JVM, so the code falls all the way to
        // entity.discard() which is the last-resort fallback.
        FakeDiscardable entity = new FakeDiscardable();
        NmsSpawnHelper.tryRemoveEntity(null, entity);
        assertTrue(entity.discardCalled,
                "discard() should be called as the last-resort fallback");
    }

    @Test
    void tryRemoveEntity_entityWithNoCleanupMethods_doesNotThrow() {
        // An entity with none of the cleanup methods — must not throw.
        assertDoesNotThrow(() -> NmsSpawnHelper.tryRemoveEntity(null, new Object()));
    }

    @Test
    void tryRemoveEntity_bothLevelAndEntityPresent_doesNotThrow() {
        // Exercises the branch where nmsLevel != null but has no removePlayerImmediately.
        assertDoesNotThrow(
                () -> NmsSpawnHelper.tryRemoveEntity(new Object(), new FakeDiscardable()));
    }

    // ── Helper classes ────────────────────────────────────────────────────────

    /** Represents a class that has a 4-parameter constructor. */
    @SuppressWarnings("unused")
    static class Fake4ParamClass {
        public Fake4ParamClass(Object a, Object b, Object c, Object d) {}
    }

    /** Represents a class with only a 2-parameter constructor (no 4-param match). */
    @SuppressWarnings("unused")
    static class Fake2ParamClass {
        public Fake2ParamClass(Object a, Object b) {}
    }

    /** Simulates a ServerLevel with {@code addFreshEntity(Object)} (1-param variant). */
    @SuppressWarnings("unused")
    static class FakeLevel1Param {
        boolean called;
        Object receivedEntity;

        public void addFreshEntity(Object entity) {
            called = true;
            receivedEntity = entity;
        }
    }

    /** Simulates a ServerLevel with {@code addFreshEntity(Object, FakeSpawnReason)} (2-param). */
    @SuppressWarnings("unused")
    static class FakeLevel2Param {
        boolean called;

        public enum FakeSpawnReason { DEFAULT }

        public void addFreshEntity(Object entity, FakeSpawnReason reason) {
            called = true;
        }
    }

    /** Simulates an NMS entity that exposes {@code discard()}.  */
    @SuppressWarnings("unused")
    static class FakeDiscardable {
        boolean discardCalled;

        public void discard() {
            discardCalled = true;
        }
    }
}
