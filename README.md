# Morning News Anchor — Android Companion

Mobile client for [Morning News Anchor](https://forest-voice-news.lovable.app). The web app does
the heavy lifting — a `pg_cron` job generates a narrated briefing every morning. This app is the
phone half: it reads what the backend produced, plays it, and lets you change the settings that
shape tomorrow's briefing.

It is a **companion**, not a standalone: it shares the web app's Supabase project
(`ghsmnpqigzocbiatpjid`), so you sign in with the same Google account and see the same data. It
contains no pipeline of its own.

## What it does

| Screen | What it touches |
|---|---|
| **Today** | `briefings` for today + `cron_runs` for what the backend last did |
| **Briefings** | `briefings` + `briefing_stories`, with audio playback |
| **Feeds** | `feeds` — add / enable / delete RSS sources |
| **Inbox** | `newsletter_catalog`, `user_newsletter_subscriptions`, `archive_log` |
| **Settings** | `preferences` — voice, delivery time, timezone, auto-send |

Audio plays through a Media3 foreground service, so a briefing keeps going with the screen off.
New briefings arrive over Supabase Realtime while the app is open.

## Setup

1. **Clone and configure**

   ```bash
   cp local.properties.example local.properties
   ```

   Fill in:

   | Key | Where it comes from |
   |---|---|
   | `SUPABASE_URL` | `https://ghsmnpqigzocbiatpjid.supabase.co` (already in the example) |
   | `SUPABASE_ANON_KEY` | Supabase dashboard → Settings → API, or the web app's `VITE_SUPABASE_PUBLISHABLE_KEY` |
   | `GOOGLE_WEB_CLIENT_ID` | Google Cloud console → the **Web application** OAuth client |
   | `WEB_APP_BASE_URL` | defaults to the Lovable deployment |

   `local.properties` is git-ignored. The anon key is a *publishable* key and is safe in the
   client binary — every table is protected by row-level security, and the app never holds a
   service-role key. `CRON_SECRET` must **never** be added here; see
   [ARCHITECTURE.md](ARCHITECTURE.md#manual-trigger-run-now).

2. **Google Sign-In** needs three things to line up, and it fails opaquely if any is off:
   - `GOOGLE_WEB_CLIENT_ID` is the **Web** client ID, not the Android one. Credential Manager
     wants the *server* client ID.
   - Supabase → Auth → Providers → Google must be enabled with that same client ID.
   - An **Android** OAuth client must exist in Google Cloud for your package name
     (`com.blumenkraft.morningnewsanchor.companion`, or `.debug` for debug builds) with your
     signing certificate's SHA-1. Get it with:
     ```bash
     ./gradlew signingReport
     ```

3. **Build**

   ```bash
   ./gradlew assembleDebug
   ```

   Requires JDK 17+ and an Android SDK with platform 36.

## Status

Everything above works against the live schema. Two things are deliberately not finished:

- **"Run now" is disabled.** The only existing trigger endpoint authenticates with a shared
  `CRON_SECRET` and takes its `userId` from the request body, so shipping it in an APK would let
  anyone generate briefings for any user. It needs a small JWT-authenticated endpoint first —
  the full reasoning and the required shape are in
  [`BriefingTriggerApi.kt`](app/src/main/java/com/blumenkraft/morningnewsanchor/companion/data/remote/BriefingTriggerApi.kt).
  Flip `BriefingTriggerRepository.TRIGGER_ENDPOINT_DEPLOYED` once it exists.
- **Notifications are realtime-only.** They fire while the app is open. Waking the phone for a
  briefing needs FCM and a server-side push; see
  [ARCHITECTURE.md](ARCHITECTURE.md#notifications).

## Verified

- `./gradlew assembleDebug` — passes, no warnings
- `./gradlew assembleRelease` — passes; R8 minification succeeds (3.9 MB APK)
- `./gradlew testDebugUnitTest` — 13/13 pass; these assert the models' column mapping against
  the real schema, so a migration that renames a column breaks the build rather than the app

Not verified: no run on a device or emulator, and no call against live Supabase — that needs
credentials in `local.properties` and a signed-in account.

## Stack

Kotlin · Jetpack Compose · MVVM + repositories · Hilt · Coroutines/Flow ·
[supabase-kt](https://github.com/supabase-community/supabase-kt) (auth, postgrest, storage,
realtime) · Media3/ExoPlayer · Retrofit · Gradle Kotlin DSL · minSdk 26 / targetSdk 35
