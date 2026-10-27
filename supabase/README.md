# Вместе — Supabase setup

The Android and PC clients are configured for the shared Supabase project using its URL and **publishable** key. The publishable key is intended for client apps; never put a `service_role` or secret key in either client or in GitHub.

## Apply the database schema

For a new Supabase project, confirm its URL ends in `vdmcaltwnhlqrvwvchwi.supabase.co`, inspect its migration history, then apply these files in order:

1. `migrations/20261020000100_shared_couple_space.sql`
2. `migrations/20261020000200_watch_party.sql`
3. `migrations/20261020000300_couple_games.sql`
4. `migrations/20261021000100_chat_delivery_ack.sql`
5. `migrations/20261023000100_together_fixes.sql`
6. `migrations/20261023000200_chat_message_edits.sql`
7. `migrations/20261025000100_watch_player_signals.sql`
8. `migrations/20261026000100_watch_camera_renegotiation.sql` (legacy compatibility only)
9. `migrations/20261027000100_couple_games_supported_types.sql`
10. `migrations/20261028000100_chat_story_interactions.sql`

The game-types migration reasserts the supported game constraint, including Connect Four, Checkers, and Dots and Boxes, without rewriting an earlier migration. The chat/story-interactions migration adds message replies, forwards, soft deletion, reactions, delivery/read receipts, typing presence, and story comments/reactions; it also changes message delivery acknowledgement from deleting the queued message to recording a receipt. Apply it before using those Android chat and story controls. The camera-renegotiation migration is required only for clients that send signal kind `renegotiate`; the current Android client instead sends an `offer` signal with a renegotiation-request payload, which is allowed by the base schema.

Remote state was verified on 2026-10-24: the nine migrations then present were recorded through `20261027000100`, and `supabase db push --dry-run --linked` reported no pending migrations. Read-only SQL checks confirmed the eight supported game types, watch signal kinds through `renegotiate`, `couple_messages.edited_at`, and the sender/couple-scoped message-edit policy. The subsequently added `20261028000100_chat_story_interactions.sql` has not been included in that remote verification; inspect the linked migration history and dry-run before applying it. The four migrations pending at the time of the earlier verification were applied after review; they added columns/policies or replaced CHECK constraints and did not update or delete user rows. A schema-only `db dump` was attempted but could not run because Docker/Podman is not installed. The database password was reset in the Dashboard after owner authorization; update any direct PostgreSQL clients or CI that still use the previous password. Never put the database password in source control or chat.

To deploy with the Supabase CLI, authenticate and link this repository, then inspect the remote migration history before applying anything:

```sh
supabase login
supabase link --project-ref vdmcaltwnhlqrvwvchwi
supabase migration list --linked
supabase db push --dry-run
```

Review the dry-run and migration history. If they show only the intended pending migrations, apply them with `supabase db push`. Do not run `supabase db reset` against a shared or production project. The repository's `config.toml` records the project reference; credentials must stay outside Git.

The Supabase GitHub integration deploys only changes that have been committed and pushed to its configured branch, and only if database migration deployment is enabled for that project. Linking GitHub alone does not apply uncommitted local migrations. After deployment, verify the migration history and confirm that `couples`, `shared_items`, `couple_profiles`, `watch_rooms`, `watch_signals`, and `couple_games` are available through the API.

## What the schema provides

The migrations create two-person couple membership, invite-code RPCs, profiles, shared items and chat with row-level security, private file storage, pair-only WebRTC room signaling, and couple-scoped turn-based game sessions with atomic move updates. The game-types migration enables Connect Four, Checkers, and Dots and Boxes without editing the original game migration. The fixes migration makes every new watch-room start close prior active rooms and use a fresh room ID so stale SDP/ICE signals cannot be reused; the Android client requires explicit confirmation before starting a room when active rooms are already listed. The chat-edits migration adds a timestamp and owner-only update policy for message text. The chat/story-interactions migration stores pair-only replies, forwards, message reactions, delivery/read receipts, typing timestamps, story comments, and story reactions; it retains delivered messages instead of deleting them on acknowledgement. The watch-player migration permits `player_source`, `playback`, and `playback_request` room signals. The optional camera-renegotiation migration adds legacy signal kind `renegotiate`; use it only when deploying a client that sends that kind. Current Android clients instead send an `offer` signal with a request payload, already allowed by the base schema. Daily question prompts and answers use existing pair-scoped `note` items and do not require a separate schema migration. Playback control state and SDP/ICE remain room signaling only; video is not uploaded to Supabase. Normal room shutdown also marks its room closed. Chat attachments use private couple storage and are downloaded to local app storage; the recipient records delivery/read status, and the uploaded object is deleted after the recipient has saved it locally. A TURN relay is not bundled, so restrictive networks may need a TURN service.

The Android watch room shares and opens any valid HTTPS link on both devices. YouTube videos and public HTTPS MP4, HLS (`.m3u8`), and WebM URLs also synchronize playback controls; other sites may not. DRM and embed restrictions are not bypassed. Playback and text chat work without camera or microphone permissions. Both media channels start off and request permission only after the corresponding button is tapped. Opt-in camera and microphone tracks are sent to the partner over WebRTC; they are not uploaded to Supabase. Screen capture is not supported. A TURN relay is not bundled, so restrictive networks may need a TURN service. The PC client is `together/desktop/index.html`; open it in Chrome or Edge and sign in to the same couple to sync data and join a room.

Before production, add backups and retention, then review privacy, account deletion, and abuse controls.

## Account deletion

The Android profile includes a delete-account action. It calls `functions/delete-account/index.ts`, which verifies the signed-in user's access token and deletes that Auth user through the Supabase Admin API. Deploy the function once with the Supabase CLI, then configure the function's `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` secrets. Keep the service-role key only in Supabase function secrets; never add it to this repository or the Android build. Until the function is deployed, account deletion will report a server error.
