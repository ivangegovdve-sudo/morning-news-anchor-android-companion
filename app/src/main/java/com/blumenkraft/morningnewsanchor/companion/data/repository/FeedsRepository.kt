package com.blumenkraft.morningnewsanchor.companion.data.repository

import com.blumenkraft.morningnewsanchor.companion.data.models.Feed
import com.blumenkraft.morningnewsanchor.companion.data.models.FeedInsert
import com.blumenkraft.morningnewsanchor.companion.data.models.FeedKind
import com.blumenkraft.morningnewsanchor.companion.data.models.FeedUpdate
import com.blumenkraft.morningnewsanchor.companion.data.supabase.AuthRepository
import com.blumenkraft.morningnewsanchor.companion.data.supabase.Tables
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/** CRUD over `public.feeds`. */
@Singleton
class FeedsRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: AuthRepository,
) {
    suspend fun listFeeds(): List<Feed> =
        postgrest.from(Tables.FEEDS)
            .select {
                filter { eq("user_id", auth.requireUserId()) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    suspend fun addFeed(
        name: String,
        url: String,
        kind: String = FeedKind.RSS,
        tags: List<String> = emptyList(),
    ): Feed =
        postgrest.from(Tables.FEEDS)
            .insert(
                FeedInsert(
                    userId = auth.requireUserId(),
                    name = name.trim(),
                    url = url.trim(),
                    kind = kind,
                    tags = tags,
                )
            ) { select() }
            .decodeSingle()

    suspend fun setEnabled(id: String, enabled: Boolean): Feed =
        postgrest.from(Tables.FEEDS)
            .update(FeedUpdate(enabled = enabled)) {
                filter {
                    eq("id", id)
                    eq("user_id", auth.requireUserId())
                }
                select()
            }
            .decodeSingle()

    suspend fun updateFeed(id: String, update: FeedUpdate): Feed =
        postgrest.from(Tables.FEEDS)
            .update(update) {
                filter {
                    eq("id", id)
                    eq("user_id", auth.requireUserId())
                }
                select()
            }
            .decodeSingle()

    suspend fun deleteFeed(id: String) {
        postgrest.from(Tables.FEEDS).delete {
            filter {
                eq("id", id)
                eq("user_id", auth.requireUserId())
            }
        }
    }
}
