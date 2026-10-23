begin;

alter table public.couple_games
    drop constraint if exists couple_games_game_type_check;

alter table public.couple_games
    add constraint couple_games_game_type_check
    check (game_type in (
        'tic_tac_toe',
        'sea_battle',
        'durak',
        'guess_tune',
        'couple_quiz',
        'connect_four',
        'checkers',
        'dots_boxes'
    ));

create or replace function public.start_watch_room(
    target_couple_id uuid,
    target_title text
)
returns uuid
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
    created_room_id uuid;
begin
    if auth.uid() is null or not public.is_couple_member(target_couple_id) then
        raise exception 'Only a couple member can start a watch room';
    end if;

    perform pg_advisory_xact_lock(hashtextextended(target_couple_id::text, 0));

    update public.watch_rooms
       set state = 'closed'
     where couple_id = target_couple_id
       and state = 'active';

    insert into public.watch_rooms (id, couple_id, host_id, title, state)
    values (
        gen_random_uuid(),
        target_couple_id,
        auth.uid(),
        coalesce(nullif(btrim(target_title), ''), 'Совместный просмотр'),
        'active'
    )
    returning id into created_room_id;

    return created_room_id;
end;
$$;

revoke all on function public.start_watch_room(uuid, text) from public;
grant execute on function public.start_watch_room(uuid, text) to authenticated;

create or replace function public.close_watch_room(target_room_id uuid)
returns void
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
    update public.watch_rooms
       set state = 'closed'
     where id = target_room_id
       and state = 'active'
       and public.is_couple_member(couple_id);
end;
$$;

revoke all on function public.close_watch_room(uuid) from public;
grant execute on function public.close_watch_room(uuid) to authenticated;

commit;
