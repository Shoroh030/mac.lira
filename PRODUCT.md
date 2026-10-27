# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Two partners using separate accounts connected to the same couple space. The primary interaction is shared and should be understandable from the first launch.

## Product Purpose

I'm Here gives partners a private shared space for conversation, everyday plans and small activities. Success means either partner can quickly find the next useful action and understand whether an activity is local, shared, or waiting for the other person.

## Capabilities and Constraints

- The Android app includes chat, shared tasks and calendar, saved ideas and stories, games, loyalty cards, file sharing, and synchronized watch rooms.
- The app keeps much of its data locally and uses Supabase for couple coordination and synchronization.
- Room creation and joining are separate actions. Camera and microphone are opt-in; screen broadcast is not supported.
- Accounts and existing couple data must be preserved during testing. No automatic room replacement or remote database migration.
- Room, media, and cross-device behavior is not fully verified end to end; avoid implying that unverified synchronization is guaranteed.
- Android permissions and system install confirmation remain user-controlled. Debug APKs are for testing, not public release.

## Evidence on Hand

- Android implementation and feature descriptions in `together/`.
- Existing tests and the project roadmap in `docs/ROADMAP.md`.
- No verified external testimonials, performance claims, or marketing evidence.

## Product Principles

- Make the shared state and the next action obvious.
- Keep core partner actions discoverable without prior configuration.
- Explain network, permission, and pairing problems with a safe recovery path.
- Preserve user data and require consent for sensitive device capabilities.
- Keep local actions useful when a partner or network is unavailable.

## Accessibility & Inclusion

Use Android-native navigation and interaction patterns, touch targets of at least 48 dp, scalable system text, and explicit labels for icon-led controls.
