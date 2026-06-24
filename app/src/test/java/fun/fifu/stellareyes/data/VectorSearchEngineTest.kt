package `fun`.fifu.stellareyes.data

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class VectorSearchEngineTest {

    @After
    fun tearDown() {
        VectorSearchEngine.clear()
    }

    @Test
    fun add_replacesExistingIdAndReturnsDefensiveCopy() {
        VectorSearchEngine.add("face-1", vectorWith(0, 1f), "Alice", "image-a")
        VectorSearchEngine.add("face-1", vectorWith(1, 1f), "Alice Updated", "image-b")

        val stored = VectorSearchEngine.getEntryById("face-1")
        requireNotNull(stored)

        stored.vector[1] = 0f

        assertEquals(1, VectorSearchEngine.getCount())
        assertEquals("Alice Updated", VectorSearchEngine.getNameById("face-1"))
        assertArrayEquals(vectorWith(1, 1f), VectorSearchEngine.getVectorById("face-1"), 0.0001f)
    }

    @Test
    fun searchTop1_returnsMatchingIdAboveThreshold() {
        VectorSearchEngine.add("face-1", vectorWith(0, 1f), "Alice", "image-a")
        VectorSearchEngine.add("face-2", vectorWith(1, 1f), "Bob", "image-b")

        val result = VectorSearchEngine.searchTop1(vectorWith(1, 1f))

        assertEquals("face-2", result.first)
        assertEquals(1f, result.second, 0.0001f)
    }

    @Test
    fun searchTop1_rejectsLowSimilarity() {
        VectorSearchEngine.add("face-1", vectorWith(0, 1f), "Alice", "image-a")

        val result = VectorSearchEngine.searchTop1(vectorWith(1, 1f))

        assertNull(result.first)
        assertEquals(0f, result.second, 0.0001f)
    }

    @Test
    fun normalize_handlesInvalidAndZeroValues() {
        val normalized = VectorSearchEngine.normalize(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, 0f))
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), normalized, 0.0001f)

        val valid = VectorSearchEngine.normalize(floatArrayOf(3f, 4f))
        assertEquals(3f / sqrt(25f), valid[0], 0.0001f)
        assertEquals(4f / sqrt(25f), valid[1], 0.0001f)
    }

    private fun vectorWith(index: Int, value: Float): FloatArray {
        return FloatArray(512).also { it[index] = value }
    }
}
