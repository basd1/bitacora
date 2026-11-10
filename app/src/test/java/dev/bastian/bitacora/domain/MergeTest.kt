package dev.bastian.bitacora.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {

    private val base = SyncableFields(
        title = "Heron",
        notes = "by the river",
        category = Category.FAUNA,
        latitude = null,
        longitude = null,
        observedAt = 100L,
        deleted = false,
    )

    private fun clean(result: MergeResult): SyncableFields = (result as MergeResult.Clean).merged

    private fun conflicted(result: MergeResult): MergeResult.Conflicted = result as MergeResult.Conflicted

    @Test
    fun noChangesOnEitherSideKeepsTheBase() {
        assertEquals(base, clean(mergeThreeWay(base, base, base)))
    }

    @Test
    fun onlyLocalChangedTakesTheLocalValue() {
        val local = base.copy(title = "Grey heron")
        assertEquals(local, clean(mergeThreeWay(base, local, base)))
    }

    @Test
    fun onlyRemoteChangedTakesTheRemoteValue() {
        val remote = base.copy(notes = "seen at dawn")
        assertEquals(remote, clean(mergeThreeWay(base, base, remote)))
    }

    @Test
    fun sameChangeOnBothSidesIsNotAConflict() {
        val both = base.copy(title = "Grey heron")
        assertEquals(both, clean(mergeThreeWay(base, both, both)))
    }

    @Test
    fun differentFieldsChangedOnEachSideMergeCleanly() {
        val local = base.copy(title = "Grey heron", latitude = 40.0, longitude = -3.0)
        val remote = base.copy(notes = "seen at dawn", category = Category.OTHER)
        val merged = clean(mergeThreeWay(base, local, remote))
        assertEquals("Grey heron", merged.title)
        assertEquals("seen at dawn", merged.notes)
        assertEquals(Category.OTHER, merged.category)
        assertEquals(40.0, merged.latitude!!, 0.0)
        assertEquals(-3.0, merged.longitude!!, 0.0)
    }

    @Test
    fun sameFieldChangedToDifferentValuesIsAConflict() {
        val local = base.copy(title = "Grey heron", notes = "local notes")
        val remote = base.copy(title = "Purple heron")
        val result = conflicted(mergeThreeWay(base, local, remote))
        assertEquals(setOf(Field.TITLE), result.conflicts)
        // Non-conflicting fields are merged; the conflicting one carries the local value for now.
        assertEquals("local notes", result.partial.notes)
        assertEquals("Grey heron", result.partial.title)
    }

    @Test
    fun everyConflictingFieldIsReported() {
        val local = base.copy(title = "a", category = Category.FLORA, observedAt = 1)
        val remote = base.copy(title = "b", category = Category.WEATHER, observedAt = 2)
        val result = conflicted(mergeThreeWay(base, local, remote))
        assertEquals(setOf(Field.TITLE, Field.CATEGORY, Field.OBSERVED_AT), result.conflicts)
    }

    @Test
    fun resolvingWithRemoteChoicesTakesThoseValues() {
        val local = base.copy(title = "a", notes = "local notes")
        val remote = base.copy(title = "b")
        val result = conflicted(mergeThreeWay(base, local, remote))

        val keepRemote = result.resolve(remote, mapOf(Field.TITLE to Side.REMOTE))
        assertEquals("b", keepRemote.title)
        assertEquals("local notes", keepRemote.notes)

        val keepLocal = result.resolve(remote, mapOf(Field.TITLE to Side.LOCAL))
        assertEquals("a", keepLocal.title)
    }

    @Test
    fun deletionAgainstAnEditRestoresTheRecordWithTheEdit() {
        val local = base.copy(deleted = true)
        val remote = base.copy(title = "Grey heron")
        val merged = clean(mergeThreeWay(base, local, remote))
        assertFalse(merged.deleted)
        assertEquals("Grey heron", merged.title)
    }

    @Test
    fun remoteDeletionAgainstALocalEditAlsoRestoresTheRecord() {
        val local = base.copy(notes = "edited offline")
        val remote = base.copy(deleted = true)
        val merged = clean(mergeThreeWay(base, local, remote))
        assertFalse(merged.deleted)
        assertEquals("edited offline", merged.notes)
    }

    @Test
    fun deletionWithoutAnEditOnTheOtherSideStaysDeleted() {
        assertTrue(clean(mergeThreeWay(base, base.copy(deleted = true), base)).deleted)
        assertTrue(clean(mergeThreeWay(base, base, base.copy(deleted = true))).deleted)
    }

    @Test
    fun deletionOnBothSidesStaysDeleted() {
        val deleted = base.copy(deleted = true)
        assertTrue(clean(mergeThreeWay(base, deleted, deleted)).deleted)
    }

    @Test
    fun restoreOnOneSideBringsTheRecordBack() {
        val deletedBase = base.copy(deleted = true)
        val remote = deletedBase.copy(deleted = false)
        assertFalse(clean(mergeThreeWay(deletedBase, deletedBase, remote)).deleted)
    }

    @Test
    fun blankBaseTreatsDifferentValuesOnBothSidesAsAConflict() {
        val local = base.copy(title = "a")
        val remote = base.copy(title = "b")
        val result = conflicted(mergeThreeWay(SyncableFields.BLANK, local, remote))
        assertTrue(Field.TITLE in result.conflicts)
    }

    @Test
    fun hasContentChangesIgnoresTheDeletedFlag() {
        assertFalse(base.copy(deleted = true).hasContentChangesFrom(base))
        assertTrue(base.copy(title = "x").hasContentChangesFrom(base))
    }
}
