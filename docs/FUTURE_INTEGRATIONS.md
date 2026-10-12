# Future integrations (not implemented in Version 1)

Version 1 has no network code and no `INTERNET` permission. The following are separate projects; none of them is required to use the app.

## What the data model already provides
* Every record has a stable UUID `id`, `createdAt`, `updatedAt` (UTC millis) and a nullable `deletedAt` tombstone — the pieces a sync engine needs for merge, conflict detection and deletion propagation.
* All writes go through `Repository`, so a future sync layer can sit beside it (e.g. an outbox of changed ids) without touching the UI.
* Media is stored by relative path under `files/media`, content-addressable by record id — uploadable later.

## 1. Google sign-in (identity only)
Signing in proves who you are; it does **not** sync data or grant Instagram analytics.
* Needs: a Google Cloud project, OAuth consent screen, an **Android OAuth client** (package `app.socmed.manager` + the SHA-1 of your signing key), and Credential Manager / Sign in with Google in the app.
* Cost: free. Verification: for a personal app with only you as a test user, "Testing" mode is enough (limited to listed test users; tokens may expire weekly). Public use would require Google's app verification.
* Impact: the app must be signed with a *stable* key — the SHA-1 is registered in Google Cloud.

## 2. Account-linked backup / restore
Options: (a) Google Drive `appDataFolder` (needs the Drive API + the `drive.appdata` scope, which is a restricted-ish scope with its own verification if distributed publicly; fine for test users), or (b) your own backend/Firebase Storage (cost + privacy policy + security rules).

## 3. Multi-device sync with offline editing
Needs a backend (Firebase/Firestore, Supabase, or your own API), authentication, per-user security rules, a sync protocol (pull changes since cursor, push outbox), **explicit conflict UI** (two edits of the same record: keep mine / keep theirs / merge fields) and tombstone retention. Expect ongoing hosting cost and a privacy policy.

## 4. Social platform connections, automatic analytics, automatic publishing
Each platform needs its **own** approved API and permissions, separate from Google:
* Instagram: Instagram Graph API (Business/Creator account linked to a Facebook Page, a Meta developer app, App Review for `instagram_basic`, `instagram_manage_insights`, `instagram_content_publish`, business verification for some).
* TikTok, Facebook, YouTube, X, LinkedIn, Pinterest: each has its own developer program, review and rate limits; some do not offer publishing or analytics to personal accounts.
These require a server component (client secrets must not ship in an APK).
Until then, metrics are typed in or imported from CSV, and publishing is done by you (the app can copy captions and share media to the platform's own app).
