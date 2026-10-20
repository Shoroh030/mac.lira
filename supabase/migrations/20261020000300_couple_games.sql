-- Shared, turn-based games are scoped to the two members of a couple.
create table if not exists public.couple_games (
    id uuid primary key default gen_random_uuid(),
    couple_id uuid not null references public.couples(id) on delete cascade,
    game_type text not null check (game_type in ('tic_tac_toe', 'sea_battle', 'durak', 'guess_tune', 'couple_quiz')),
    host_id uuid not null default auth.uid() references auth.users(id),
    status text not null default 'waiting' check (status in ('waiting', 'active', 'finished', 'cancelled')),
    state jsonb not null default '{}'::jsonb,
    version bigint not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists couple_games_lookup_idx
    on public.couple_games(couple_id, game_type, status, updated_at desc);
create unique index if not exists couple_games_one_open_per_type_idx
    on public.couple_games(couple_id, game_type)
    where status in ('waiting', 'active');

alter table public.couple_games enable row level security;
grant select, insert, update, delete on public.couple_games to authenticated;

create policy "Couple members can read game sessions"
    on public.couple_games for select to authenticated
    using (public.is_couple_member(couple_id));
create policy "Couple members can create game sessions"
    on public.couple_games for insert to authenticated
    with check (public.is_couple_member(couple_id) and host_id = auth.uid());
create policy "Couple members can update game sessions"
    on public.couple_games for update to authenticated
    using (public.is_couple_member(couple_id))
    with check (public.is_couple_member(couple_id));
create policy "Couple members can delete game sessions"
    on public.couple_games for delete to authenticated
    using (public.is_couple_member(couple_id));

-- Atomic optimistic locking prevents two phones from silently overwriting a move.
create or replace function public.update_couple_game(
    target_id uuid,
    expected_version bigint,
    new_state jsonb,
    new_status text
)
returns table (id uuid, version bigint, status text, state jsonb)
language plpgsql security invoker
set search_path = public, pg_temp
as $$
begin
    return query
    update public.couple_games g
       set state = new_state,
           status = new_status,
           version = g.version + 1,
           updated_at = now()
     where g.id = target_id
       and g.version = expected_version
       and public.is_couple_member(g.couple_id)
    returning g.id, g.version, g.status, g.state;
end;
$$;

revoke all on function public.update_couple_game(uuid, bigint, jsonb, text) from public;
grant execute on function public.update_couple_game(uuid, bigint, jsonb, text) to authenticated;
