# Вместе — Supabase setup

The Android and PC clients are configured for the shared Supabase project using its URL and **publishable** key. The publishable key is intended for client apps; never put a `service_role` or secret key in either client or in GitHub.

## Apply the database schema

The clients currently receive missing-table errors until the migrations have run. Confirm that the Supabase project's URL ends in `vdmcaltwnhlqrvwvchwi.supabase.co`, then apply these files in order:

1. `migrations/20261020000100_shared_couple_space.sql`
2. `migrations/20261020000200_watch_party.sql`
3. `migrations/20261020000300_couple_games.sql`
4. `migrations/20261021000100_chat_delivery_ack.sql`
5. `migrations/20261023000100_together_fixes.sql`
6. `migrations/20261023000200_chat_message_edits.sql`
7. `migrations/20261025000100_watch_player_signals.sql`
8. `migrations/20261026000100_watch_camera_renegotiation.sql` (legacy compatibility only)

The last migration is committed on the feature branch, but this work did not apply it to Supabase. The remote migration history was not verified, so treat its remote state as unknown and do not apply it without explicit approval. It is required only for clients that send camera renegotiation using signal kind `renegotiate`. The current Android client sends an `offer` signal with a renegotiation request payload, which is already allowed by the base watch-party schema.

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

The migrations create two-person couple membership, invite-code RPCs, profiles, shared items and chat with row-level security, private file storage, pair-only WebRTC room signaling, and couple-scoped turn-based game sessions with atomic move updates. The latest fixes migration enables Connect Four, Checkers, and Dots and Boxes, and makes every new watch-room start close prior active rooms and use a fresh room ID so stale SDP/ICE signals cannot be reused. The chat-edits migration adds a timestamp and owner-only update policy for message text. The watch-player migration permits `player_source`, `playback`, and `playback_request` room signals. The optional camera-renegotiation migration adds legacy signal kind `renegotiate`; use it only when deploying a client that sends that kind. Current Android clients instead send an `offer` signal with a request payload, already allowed by the base schema. Playback control state and SDP/ICE remain room signaling only; video is not uploaded to Supabase. Normal room shutdown also marks its room closed. Chat attachments use private couple storage and are downloaded to local app storage; the recipient acknowledges delivery before the uploaded object is deleted. A TURN relay is not bundled, so restrictive networks may need a TURN service.

The Android watch room synchronizes YouTube videos and public HTTPS MP4, HLS (`.m3u8`), and WebM URLs. Other sites can be opened in the in-app WebView, but their playback controls are not synchronized. DRM and embed restrictions are not bypassed. Playback and text chat work without camera or microphone permissions. Both media channels start off and request permission only after the corresponding button is tapped. Opt-in camera and microphone tracks are sent to the partner over WebRTC; they are not uploaded to Supabase. Screen capture is not supported. A TURN relay is not bundled, so restrictive networks may need a TURN service. The PC client is `together/desktop/index.html`; open it in Chrome or Edge and sign in to the same couple to sync data and join a room.

Before production, add backups and retention, then review privacy, account deletion, and abuse controls.

## Account deletion

The Android profile includes a delete-account action. It calls `functions/delete-account/index.ts`, which verifies the signed-in user's access token and deletes that Auth user through the Supabase Admin API. Deploy the function once with the Supabase CLI, then configure the function's `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` secrets. Keep the service-role key only in Supabase function secrets; never add it to this repository or the Android build. Until the function is deployed, account deletion will report a server error.
