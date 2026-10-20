-- Pair-only screen broadcast signaling. Media travels peer-to-peer over WebRTC;
-- this database stores only room metadata, SDP/ICE signaling and text chat.
create table if not exists public.watch_rooms (
    id uuid primary key,
    couple_id uuid not null references public.couples(id) on delete cascade,
    host_id uuid not null default auth.uid() references auth.users(id),
    title text not null default 'Совместный просмотр',
    state text not null default 'active' check (state in ('active', 'closed')),
    created_at timestamptz not null default now()
);

create index if not exists watch_rooms_couple_state_idx
    on public.watch_rooms(couple_id, state, created_at desc);

create table if not exists public.watch_signals (
    id bigint generated always as identity primary key,
    room_id uuid not null references public.watch_rooms(id) on delete cascade,
    couple_id uuid not null references public.couples(id) on delete cascade,
    sender_id uuid not null default auth.uid() references auth.users(id),
    kind text not null check (kind in ('offer', 'answer', 'ice', 'chat', 'close')),
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists watch_signals_room_id_idx on public.watch_signals(room_id, id);

create table if not exists public.couple_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    couple_id uuid not null references public.couples(id) on delete cascade,
    display_name text not null,
    avatar text not null default '♡',
    bio text not null default '',
    updated_at timestamptz not null default now()
);

alter table public.couple_profiles enable row level security;
grant select, insert, update on public.couple_profiles to authenticated;
create policy "Couple members can read each other's profiles" on public.couple_profiles
    for select to authenticated using (public.is_couple_member(couple_id));
create policy "Users can create their own couple profile" on public.couple_profiles
    for insert to authenticated with check (public.is_couple_member(couple_id) and user_id = auth.uid());
create policy "Users can update their own couple profile" on public.couple_profiles
    for update to authenticated using (public.is_couple_member(couple_id) and user_id = auth.uid())
    with check (public.is_couple_member(couple_id) and user_id = auth.uid());

alter table public.watch_rooms enable row level security;
alter table public.watch_signals enable row level security;

grant select, insert, update on public.watch_rooms to authenticated;
grant select, insert on public.watch_signals to authenticated;
grant usage, select on sequence public.watch_signals_id_seq to authenticated;

create policy "Couple members can read watch rooms" on public.watch_rooms
    for select to authenticated using (public.is_couple_member(couple_id));
create policy "Couple members can create watch rooms" on public.watch_rooms
    for insert to authenticated with check (
        public.is_couple_member(couple_id) and host_id = auth.uid()
    );
create policy "Couple members can update watch rooms" on public.watch_rooms
    for update to authenticated using (public.is_couple_member(couple_id))
    with check (public.is_couple_member(couple_id));
create policy "Couple members can read room signals" on public.watch_signals
    for select to authenticated using (
        public.is_couple_member(couple_id)
        and exists (select 1 from public.watch_rooms r where r.id = room_id and r.couple_id = watch_signals.couple_id)
    );
create policy "Couple members can send room signals" on public.watch_signals
    for insert to authenticated with check (
        public.is_couple_member(couple_id) and sender_id = auth.uid()
        and exists (select 1 from public.watch_rooms r where r.id = room_id and r.couple_id = watch_signals.couple_id and r.state = 'active')
    );
