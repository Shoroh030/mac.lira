# Вместе — Supabase setup

The Android and PC clients are configured for the shared Supabase project using its URL and **publishable** key. The publishable key is intended for client apps; never put a `service_role` or secret key in either client or in GitHub.

## Apply the database schema

The clients currently receive missing-table errors until the migrations have run. In the Supabase Dashboard for the configured project, open **SQL Editor**, then run these files in order:

1. `migrations/20261020000100_shared_couple_space.sql`
2. `migrations/20261020000200_watch_party.sql`
3. `migrations/20261020000300_couple_games.sql`

Alternatively, use the Supabase GitHub integration with this repository's root as the working directory and deploy the `main` branch. Confirm the integration is set to deploy production migrations. After the migration completes, `couples`, `shared_items`, `couple_profiles`, `watch_rooms`, `watch_signals`, and `couple_games` should be available through the API.

## What the schema provides

The migrations create two-person couple membership, invite-code RPCs, profiles, shared items and chat with row-level security, private file storage, pair-only WebRTC room signaling, and couple-scoped turn-based game sessions with atomic move updates. Video and voice media travel directly between the two devices; Supabase stores room offers, ICE metadata, and chat text. A TURN relay is not bundled, so restrictive networks may need a TURN service.

Android asks the host to approve screen capture and microphone access. Browser or Android streaming may be blocked by protected-content services; the app does not bypass those restrictions. The PC client is `together/desktop/index.html`; open it in Chrome or Edge and sign in to the same couple to sync data and join a room.

Before production, add backups and retention, then review privacy, account deletion, and abuse controls.
