-- Supabase starter schema for the "Вместе" couple workspace.
-- Run this migration in the Supabase SQL editor before configuring the Android client.

create extension if not exists pgcrypto;

create table if not exists public.couples (
    id uuid primary key default gen_random_uuid(),
    name text not null default 'Наша пара',
    invite_code text not null unique default upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)),
    created_at timestamptz not null default now()
);

create table if not exists public.couple_members (
    couple_id uuid not null references public.couples(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (couple_id, user_id),
    unique (user_id)
);

create table if not exists public.shared_items (
    id uuid primary key default gen_random_uuid(),
    couple_id uuid not null references public.couples(id) on delete cascade,
    kind text not null check (kind in (
        'task', 'calendar_task', 'event', 'wish', 'note', 'gift',
        'time_capsule', 'vote', 'file', 'loyalty_card', 'plan', 'movie_room'
    )),
    payload jsonb not null default '{}'::jsonb,
    created_by uuid not null default auth.uid() references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists shared_items_couple_updated_idx
    on public.shared_items (couple_id, updated_at desc);

create table if not exists public.couple_messages (
    id uuid primary key default gen_random_uuid(),
    couple_id uuid not null references public.couples(id) on delete cascade,
    sender_id uuid not null default auth.uid() references auth.users(id),
    body text not null check (char_length(body) between 1 and 4000),
    created_at timestamptz not null default now()
);
create index if not exists couple_messages_couple_created_idx
    on public.couple_messages (couple_id, created_at desc);

create or replace function public.is_couple_member(target_couple uuid)
returns boolean
language sql stable security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1 from public.couple_members
        where couple_id = target_couple and user_id = (select auth.uid())
    );
$$;

create or replace function public.create_couple(couple_name text default 'Наша пара')
returns table (couple_id uuid, invite_code text)
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare new_id uuid;
begin
    if auth.uid() is null then raise exception 'Sign in first'; end if;
    if exists (select 1 from public.couple_members where user_id = auth.uid()) then
        raise exception 'This account already belongs to a couple';
    end if;
    insert into public.couples(name) values (left(coalesce(nullif(trim(couple_name), ''), 'Наша пара'), 80))
        returning id into new_id;
    insert into public.couple_members(couple_id, user_id) values (new_id, auth.uid());
    return query select c.id, c.invite_code from public.couples c where c.id = new_id;
end;
$$;

create or replace function public.join_couple(code text)
returns uuid
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare target_id uuid; member_count integer;
begin
    if auth.uid() is null then raise exception 'Sign in first'; end if;
    if exists (select 1 from public.couple_members where user_id = auth.uid()) then
        raise exception 'This account already belongs to a couple';
    end if;
    select id into target_id from public.couples
        where invite_code = upper(trim(code)) for update;
    if target_id is null then raise exception 'Invalid invite code'; end if;
    select count(*) into member_count from public.couple_members where couple_id = target_id;
    if member_count >= 2 then raise exception 'This couple is already full'; end if;
    insert into public.couple_members(couple_id, user_id) values (target_id, auth.uid());
    return target_id;
end;
$$;

revoke all on function public.create_couple(text) from public;
revoke all on function public.join_couple(text) from public;
grant execute on function public.create_couple(text) to authenticated;
grant execute on function public.join_couple(text) to authenticated;

alter table public.couples enable row level security;
alter table public.couple_members enable row level security;
alter table public.shared_items enable row level security;
alter table public.couple_messages enable row level security;

create policy "Members can read their couple"
    on public.couples for select to authenticated
    using (public.is_couple_member(id));

create policy "Members can read their member list"
    on public.couple_members for select to authenticated
    using (public.is_couple_member(couple_id));

create policy "Members can read shared items"
    on public.shared_items for select to authenticated
    using (public.is_couple_member(couple_id));
create policy "Members can add shared items"
    on public.shared_items for insert to authenticated
    with check (public.is_couple_member(couple_id) and created_by = auth.uid());
create policy "Members can update shared items"
    on public.shared_items for update to authenticated
    using (public.is_couple_member(couple_id))
    with check (public.is_couple_member(couple_id));
create policy "Members can delete shared items"
    on public.shared_items for delete to authenticated
    using (public.is_couple_member(couple_id));

create policy "Members can read couple messages"
    on public.couple_messages for select to authenticated
    using (public.is_couple_member(couple_id));
create policy "Members can send couple messages"
    on public.couple_messages for insert to authenticated
    with check (public.is_couple_member(couple_id) and sender_id = auth.uid());
create policy "Sender can delete own message"
    on public.couple_messages for delete to authenticated
    using (public.is_couple_member(couple_id) and sender_id = auth.uid());

-- Private object storage: file paths must start with the couple UUID.
insert into storage.buckets (id, name, public)
values ('couple-files', 'couple-files', false)
on conflict (id) do nothing;

create policy "Couple members can read files"
    on storage.objects for select to authenticated
    using (
        bucket_id = 'couple-files'
        and public.is_couple_member((storage.foldername(name))[1]::uuid)
    );
create policy "Couple members can upload files"
    on storage.objects for insert to authenticated
    with check (
        bucket_id = 'couple-files'
        and public.is_couple_member((storage.foldername(name))[1]::uuid)
    );
create policy "Couple members can delete files"
    on storage.objects for delete to authenticated
    using (
        bucket_id = 'couple-files'
        and public.is_couple_member((storage.foldername(name))[1]::uuid)
    );
