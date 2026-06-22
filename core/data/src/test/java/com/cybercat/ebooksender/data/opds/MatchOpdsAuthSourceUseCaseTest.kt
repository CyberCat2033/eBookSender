package com.cybercat.ebooksender.data.opds

import com.cybercat.ebooksender.data.opds.OpdsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchOpdsAuthSourceUseCaseTest {

    @Test
    fun matchesCorrectSourceByHost() = runBlocking {
        val matchingSource = OpdsSource(
            id = "source-1",
            title = "Flibusta",
            url = "http://flibusta.is/opds"
        )
        val nonMatchingSource = OpdsSource(
            id = "source-2",
            title = "Gutenberg",
            url = "http://gutenberg.org/opds"
        )

        val repository = FakeOpdsRepository(listOf(matchingSource, nonMatchingSource))
        val useCase = MatchOpdsAuthSourceUseCase(repository)

        // URL in exception matches host of source-1 (ignore path/query)
        val exception = OpdsAuthenticationRequiredException(
            url = "http://flibusta.is/opds/some/book/path?query=abc",
            message = "Authentication required"
        )

        val result = useCase(exception)
        assertEquals(matchingSource, result)
    }

    @Test
    fun returnsNullWhenNoSourcesMatch() = runBlocking {
        val nonMatchingSource = OpdsSource(
            id = "source-2",
            title = "Gutenberg",
            url = "http://gutenberg.org/opds"
        )

        val repository = FakeOpdsRepository(listOf(nonMatchingSource))
        val useCase = MatchOpdsAuthSourceUseCase(repository)

        val exception = OpdsAuthenticationRequiredException(
            url = "http://flibusta.is/opds",
            message = "Authentication required"
        )

        val result = useCase(exception)
        assertNull(result)
    }

    @Test
    fun handlesEmptySourcesList() = runBlocking {
        val repository = FakeOpdsRepository(emptyList())
        val useCase = MatchOpdsAuthSourceUseCase(repository)

        val exception = OpdsAuthenticationRequiredException(
            url = "http://flibusta.is/opds",
            message = "Authentication required"
        )

        val result = useCase(exception)
        assertNull(result)
    }

    private class FakeOpdsRepository(sourcesList: List<OpdsSource>) :
        OpdsRepository(null, null, null, null, null, true) {

        private val sourcesFlow = MutableStateFlow(sourcesList)

        override val sources: Flow<List<OpdsSource>> = sourcesFlow
    }
}
