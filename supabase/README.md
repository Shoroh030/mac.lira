# Вместе — Supabase setup

The Android and PC clients are configured for the shared Supabase project using its URL and **publishable** key. The publishable key is intended for client apps; never put a `service_role` or secret key in either client or in GitHub.

## Apply the database schema

The clients currently receive missing-table errors until the migrations have run. Confirm that the Supabase project's URL ends in `vdmcaltwnhlqrvwvchwi.supabase.co`, then apply these files in order:

1. `migrations/20261020000100_shared_couple_space.sql`
2. `migrations/20261020000200_watch_party.sql`
3. `migrations/20261020000300_couple_games.sql`
4. `migrations/20261021000100_chat_delivery_ack.sql`
5. `migrations/20261023000100_together_fixes.sql`

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

The migrations create two-person couple membership, invite-code RPCs, profiles, shared items and chat with row-level security, private file storage, pair-only WebRTC room signaling, and couple-scoped turn-based game sessions with atomic move updates. The latest migration enables Connect Four, Checkers, and Dots and Boxes, and makes every new watch-room start close prior active rooms and use a fresh room ID so stale SDP/ICE signals cannot be reused. Normal room shutdown also marks its room closed. Video and voice media travel directly between the two devices; Supabase stores room offers, ICE metadata, and chat text. A TURN relay is not bundled, so restrictive networks may need a TURN service.

Android asks the host to approve screen capture and microphone access. Browser or Android streaming may be blocked by protected-content services; the app does not bypass those restrictions. The PC client is `together/desktop/index.html`; open it in Chrome or Edge and sign in to the same couple to sync data and join a room.

Before production, add backups and retention, then review privacy, account deletion, and abuse controls.

## Account deletion

The Android profile includes a delete-account action. It calls `functions/delete-account/index.ts`, which verifies the signed-in user's access token and deletes that Auth user through the Supabase Admin API. Deploy the function once with the Supabase CLI, then configure the function's `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` secrets. Keep the service-role key only in Supabase function secrets; never add it to this repository or the Android build. Until the function is deployed, account deletion will report a server error.
