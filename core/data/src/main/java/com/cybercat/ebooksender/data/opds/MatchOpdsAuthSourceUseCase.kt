package com.cybercat.ebooksender.data.opds

import com.cybercat.ebooksender.util.UrlHostMatcher
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Resolves a saved [OpdsSource] that matches the host of the URL that triggered
 * [OpdsAuthenticationRequiredException]. Used to surface a credentials dialog for
 * the source that is actually being requested instead of a generic error.
 */
class MatchOpdsAuthSourceUseCase @Inject constructor(private val opdsRepository: OpdsRepository) {

    suspend operator fun invoke(error: OpdsAuthenticationRequiredException): OpdsSource? {
        val currentSources = opdsRepository.sources.first()
        return currentSources.firstOrNull { source ->
            UrlHostMatcher.hostsMatch(error.url, source.url)
        }
    }
}
