package com.blumenkraft.morningnewsanchor.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Inbox handling in this backend is NOT rule-based.
 *
 * There is no `inbox_rules` table. Instead the web app models the inbox as:
 *   newsletter_catalog            — a curated, app-wide list of known newsletters
 *   user_newsletter_subscriptions — which of those a given user follows, and whether paused
 *   archive_log                   — an audit trail of messages the weekly sweep archived
 *
 * So the companion's "inbox" screen manages subscriptions and reviews the archive log.
 * See ARCHITECTURE.md "Schema deltas".
 */

/** Row of `public.newsletter_catalog` — shared across all users, readable by `anon`. */
@Serializable
data class NewsletterCatalogEntry(
    val id: String,
    val slug: String,
    val name: String,
    val publisher: String? = null,
    val description: String? = null,
    val category: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("subscribe_url") val subscribeUrl: String? = null,
    @SerialName("sender_email_pattern") val senderEmailPattern: String? = null,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/** Row of `public.user_newsletter_subscriptions`. */
@Serializable
data class NewsletterSubscription(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("catalog_id") val catalogId: String,
    val status: String = SubscriptionStatus.ACTIVE,
    val paused: Boolean = false,
    @SerialName("added_at") val addedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class NewsletterSubscriptionInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("catalog_id") val catalogId: String,
    val status: String = SubscriptionStatus.ACTIVE,
    val paused: Boolean = false,
)

@Serializable
data class NewsletterSubscriptionUpdate(
    val paused: Boolean? = null,
    val status: String? = null,
)

object SubscriptionStatus {
    const val ACTIVE = "active"
    const val PENDING = "pending"
    const val REMOVED = "removed"
}

/** A catalog entry paired with the current user's subscription, if any. Assembled client-side. */
data class CatalogItemWithSubscription(
    val catalog: NewsletterCatalogEntry,
    val subscription: NewsletterSubscription?,
) {
    val isSubscribed: Boolean get() = subscription != null && subscription.status != SubscriptionStatus.REMOVED
    val isPaused: Boolean get() = subscription?.paused == true
}

/** Row of `public.archive_log` — read-only from the client. */
@Serializable
data class ArchiveLogEntry(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("gmail_message_id") val gmailMessageId: String,
    val reason: String,
    val sender: String? = null,
    val subject: String? = null,
    @SerialName("archived_at") val archivedAt: String,
)
