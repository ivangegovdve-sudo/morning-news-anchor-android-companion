package com.blumenkraft.morningnewsanchor.companion.data.repository

import com.blumenkraft.morningnewsanchor.companion.data.models.ArchiveLogEntry
import com.blumenkraft.morningnewsanchor.companion.data.models.CatalogItemWithSubscription
import com.blumenkraft.morningnewsanchor.companion.data.models.NewsletterCatalogEntry
import com.blumenkraft.morningnewsanchor.companion.data.models.NewsletterSubscription
import com.blumenkraft.morningnewsanchor.companion.data.models.NewsletterSubscriptionInsert
import com.blumenkraft.morningnewsanchor.companion.data.models.NewsletterSubscriptionUpdate
import com.blumenkraft.morningnewsanchor.companion.data.models.SubscriptionStatus
import com.blumenkraft.morningnewsanchor.companion.data.supabase.AuthRepository
import com.blumenkraft.morningnewsanchor.companion.data.supabase.Tables
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The inbox surface: newsletter subscriptions plus the archive audit trail.
 *
 * This is where the brief's "inbox rules" map onto reality — there is no `inbox_rules`
 * table and no pattern/action model. What the backend actually supports is subscribing to
 * entries in a shared `newsletter_catalog` and pausing them. See ARCHITECTURE.md.
 */
@Singleton
class InboxRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: AuthRepository,
) {
    /** The shared catalog. Not user-scoped — every signed-in user sees the same rows. */
    suspend fun listCatalog(): List<NewsletterCatalogEntry> =
        postgrest.from(Tables.NEWSLETTER_CATALOG)
            .select {
                order("sort_order", Order.ASCENDING)
                order("name", Order.ASCENDING)
            }
            .decodeList()

    suspend fun listSubscriptions(): List<NewsletterSubscription> =
        postgrest.from(Tables.USER_NEWSLETTER_SUBSCRIPTIONS)
            .select { filter { eq("user_id", auth.requireUserId()) } }
            .decodeList()

    /**
     * Joins catalog + subscriptions client-side.
     *
     * Done in two queries rather than a Postgrest embedded join because the catalog is the
     * left side and the subscription is the optional one; expressing that as an embed would
     * need a nullable to-one relationship the FK doesn't describe in that direction.
     */
    suspend fun listCatalogWithSubscriptions(): List<CatalogItemWithSubscription> {
        val catalog = listCatalog()
        val subsByCatalogId = listSubscriptions().associateBy { it.catalogId }
        return catalog.map { entry ->
            CatalogItemWithSubscription(entry, subsByCatalogId[entry.id])
        }
    }

    suspend fun subscribe(catalogId: String): NewsletterSubscription =
        postgrest.from(Tables.USER_NEWSLETTER_SUBSCRIPTIONS)
            .insert(
                NewsletterSubscriptionInsert(
                    userId = auth.requireUserId(),
                    catalogId = catalogId,
                )
            ) { select() }
            .decodeSingle()

    suspend fun setPaused(subscriptionId: String, paused: Boolean): NewsletterSubscription =
        postgrest.from(Tables.USER_NEWSLETTER_SUBSCRIPTIONS)
            .update(NewsletterSubscriptionUpdate(paused = paused)) {
                filter {
                    eq("id", subscriptionId)
                    eq("user_id", auth.requireUserId())
                }
                select()
            }
            .decodeSingle()

    /**
     * Marks the subscription removed rather than deleting the row, matching the web app's
     * `status` column. Keeps `ingested_items.catalog_id` history intact.
     */
    suspend fun unsubscribe(subscriptionId: String): NewsletterSubscription =
        postgrest.from(Tables.USER_NEWSLETTER_SUBSCRIPTIONS)
            .update(NewsletterSubscriptionUpdate(status = SubscriptionStatus.REMOVED)) {
                filter {
                    eq("id", subscriptionId)
                    eq("user_id", auth.requireUserId())
                }
                select()
            }
            .decodeSingle()

    /** What the weekly Gmail sweep archived. Read-only. */
    suspend fun listArchiveLog(limit: Long = 100): List<ArchiveLogEntry> =
        postgrest.from(Tables.ARCHIVE_LOG)
            .select {
                filter { eq("user_id", auth.requireUserId()) }
                order("archived_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()
}
