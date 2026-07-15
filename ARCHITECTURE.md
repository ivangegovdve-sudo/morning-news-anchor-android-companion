# Architecture

## Shape

```
Compose screen  →  ViewModel (UiState + intents)  →  Repository  →  supabase-kt  →  Postgres/Storage
                                                          ↑
                                                    AuthRepository (session, userId)
```

Nothing exotic: MVVM with a repository seam, Hilt for wiring, one `SupabaseClient` singleton.
Repositories expose `suspend` functions returning decoded models; ViewModels own a
`StateFlow<UiState>` and never leak Supabase types upward.

Auth is a **gate, not a destination**. `CompanionApp` composes the nav graph only when the session
is `SignedIn`, which is why `AuthRepository.requireUserId()` can throw rather than return null —
no data screen can exist without a user.

## The backend is not ours

The web app owns the schema, the pipeline, and the cron. This app is a reader plus a settings
editor. Two consequences run through the code:

- **Additive schema changes must not break the client.** `ignoreUnknownKeys = true` on the shared
  `Json`, and `ModelSerializationTest` pins the column names we do depend on.
- **We never hold privileged credentials.** No service-role key, no `CRON_SECRET`. Everything goes
  through RLS as the `authenticated` role.

### Schema deltas

The original spec for this app described a schema that doesn't match production. What's actually
there (per the web app's generated `types.ts`):

| Spec said | Reality |
|---|---|
| `rss_feeds` | `feeds`, with a `kind` discriminator and a `tags` array |
| `inbox_rules (kind, pattern, action)` | **Doesn't exist.** Inbox = `newsletter_catalog` + `user_newsletter_subscriptions` + `archive_log` |
| `user_connections` | **Doesn't exist.** Closest are `user_provider_keys` and `telegram_links` |
| `preferences.ai_provider`, `source_mode`, `ai_model`, `*_api_key`, `pocket_tts_url` | None exist. Keys live encrypted in `user_provider_keys`; prefs instead carry `anchor_name`, `tone`, `interests[]`, `skip_topics[]`, `narration_style_prompt`, `auto_send`, `backup_to_drive`, `onboarding_done` |
| `preferences.onboarding_complete` | `onboarding_done` |
| `briefings.story_count` | Doesn't exist — stories live in `briefing_stories`, count them |
| `cron_runs.completed_at`, `items_ingested` | `finished_at`; detail is a JSON blob in `details`; the run type is `kind` |
| `ingested_items.summary`, `importance_score` | Neither exists; ranked output lands in `briefing_stories` |

The models here follow reality, not the spec.

## Audio

This is the part most likely to surprise someone.

**A briefing is not one MP3.** It's an intro plus one audio file per story:
`briefings.intro_audio_url` and `briefing_stories.audio_url`. The player builds a queue from
them, in `position` order. `BriefingRepository.buildQueue` skips tracks with no audio, so a
half-generated briefing is still partly playable.

**Those columns hold storage paths, not URLs**, despite the `_url` naming. The real layout is:

```
bucket: audio            (private)
path:   {userId}/{briefingId}/{slug}.mp3      e.g. u1/b1/intro.mp3, u1/b1/story-03.mp3
```

**The app signs its own URLs.** The web app signs server-side with the service-role key, but it
doesn't have to — the bucket's RLS policy is:

```sql
bucket_id = 'audio' AND (storage.foldername(name))[1] = auth.uid()::text
```

which grants an authenticated user SELECT on their own objects, and that's sufficient to create a
signed URL. So `StorageUrlResolver` signs client-side, and playback needs no backend endpoint. URLs
are cached for an hour with a 15-minute refresh margin — a briefing can be ten minutes of audio, so
a URL must outlive the track, not merely be valid when handed over.

**Known gap:** if a queue plays long enough for a signed URL to expire mid-playback, ExoPlayer will
fail on that item. Re-signing on expiry isn't implemented.

## Realtime

`BriefingRepository.observeBriefings()` opens a channel filtered to `user_id = <me>` on the
`briefings` table. The channel unsubscribes when collection stops, so it must be scoped to a
ViewModel, not a singleton.

An INSERT fires when the pipeline *starts* a briefing — the row arrives `pending` with no audio.
"Ready to listen" is an UPDATE reaching `status in (ready, sent)`. Treating the insert as
completion is the easy mistake here.

## Manual trigger ("Run now")

**Currently disabled.** `BriefingTriggerRepository.TRIGGER_ENDPOINT_DEPLOYED = false`.

The existing endpoint already supports a forced single-user run:

```
POST /api/cron/morning-briefing
Authorization: Bearer <CRON_SECRET>
{ "force": true, "userId": "<uuid>", "skipDelivery": false }
```

The app must not call it:

1. `CRON_SECRET` is a static shared secret. Anything in an APK is public — `apktool` recovers it
   in seconds. No obfuscation changes that.
2. The endpoint reads `userId` from the **body** and never checks it against a caller identity.
   Whoever has the secret can generate briefings for arbitrary users and burn everyone's LLM and
   TTS budget.

What's needed instead — a Supabase Edge Function is the natural home, since it verifies JWTs for
free:

- verify the caller's Supabase JWT;
- take the user id **from the verified token**, never the body;
- rate-limit per user (the pipeline is expensive);
- keep `CRON_SECRET` / service-role server-side and call the pipeline itself;
- return `cron_runs.id` so the client can follow progress.

Then set `TRIGGER_ENDPOINT_DEPLOYED = true` and point `BriefingTriggerApi.RUN_NOW_PATH` at it.

## Notifications

Realtime only, today: the app learns about a new briefing while it's open and holding a socket.
That needs no FCM project and no server push, which is why it's the default.

It does **not** wake the phone at 08:00. That requires Firebase Cloud Messaging plus something
server-side to send the push when the pipeline finishes — most naturally the same Edge Function
that owns the trigger. `MorningNewsCompanionApp` already creates the `briefing_ready` channel, and
`POST_NOTIFICATIONS` is declared; the runtime permission request and the FCM service are not
written.

A `WorkManager` poll is the cheap middle option, at the cost of battery and up-to-15-minute lag.
WorkManager is wired for on-demand init (`Configuration.Provider` + the manifest removes
`WorkManagerInitializer`, or the default initializer would race Hilt's factory).

## Version pinning

`gradle/libs.versions.toml` mirrors the set that builds today in the sibling `Velune` app rather
than "newest of everything". Three constraints are load-bearing:

- **Hilt ≥ 2.60 requires AGP ≥ 9.0.** AGP and Hilt move together. (This is a hard build failure,
  not a warning.)
- **AGP 9 has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` is now a build
  error, which is why the plugins block omits it.
- **KSP tracks Kotlin's line.** KSP left the paired `<kotlin>-<ksp>` scheme after `2.2.21-2.0.5`;
  the current line is versioned independently (KSP 2.3.x ↔ Kotlin 2.3.x).

Also note `supabase-kt` is `io.github.jan-tennert.supabase` — a group with `jan-tennant` in it
does not exist. `ktor` must track whatever supabase-kt resolves (3.4.3 for supabase 3.6.0).

`kotlinx-datetime` 0.7 moved `Clock` into the Kotlin stdlib; `kotlinx.datetime.Clock` is gone, so
`DashboardViewModel` imports `kotlin.time.Clock`.

## Testing

`ModelSerializationTest` decodes Postgrest-shaped JSON for every table into the models. It's cheap
insurance on the one thing most likely to silently rot: a column rename in a migration turning a
field into `null` at runtime. It also pins the `explicitNulls = false` behaviour that makes partial
`PreferencesUpdate` PATCHes safe — with explicit nulls, saving one setting would blank the rest of
the row.
