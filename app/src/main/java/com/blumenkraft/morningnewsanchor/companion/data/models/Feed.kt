package com.blumenkraft.morningnewsanchor.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row of `public.feeds`.
 *
 * The table is named `feeds`, not `rss_feeds`, and it is not RSS-only — [kind]
 * discriminates the source type.
 */
@Serializable
data class Feed(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val url: String,
    /** See [FeedKind]. Defaults to `rss` server-side. */
    val kind: String = FeedKind.RSS,
    val tags: List<String> = emptyList(),
    val enabled: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Insert payload. `id`, `created_at` and `updated_at` are server-defaulted. */
@Serializable
data class FeedInsert(
    @SerialName("user_id") val userId: String,
    val name: String,
    val url: String,
    val kind: String = FeedKind.RSS,
    val tags: List<String> = emptyList(),
    val enabled: Boolean = true,
)

@Serializable
data class FeedUpdate(
    val name: String? = null,
    val url: String? = null,
    val tags: List<String>? = null,
    val enabled: Boolean? = null,
)

object FeedKind {
    const val RSS = "rss"
    const val ATOM = "atom"
    const val YOUTUBE = "youtube"
}
