begin;

alter table public.couple_messages
    add column if not exists reply_to uuid,
    add column if not exists reply_preview text,
    add column if not exists forwarded_from uuid,
    add column if not exists forwarded_from_name text,
    add column if not exists deleted_at timestamptz;

grant update (body, edited_at, deleted_at) on public.couple_messages to authenticated;

create table if not exists public.couple_message_reactions (
    couple_id uuid not null references public.couples(id) on delete cascade,
    message_id uuid not null references public.couple_messages(id) on delete cascade,
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    emoji text not null check (emoji in ('❤️', '😂', '😍', '👍', '🔥', '👏', '😮', '😢')),
    updated_at timestamptz not null default now(),
    primary key (message_id, user_id)
);

create index if not exists couple_message_reactions_couple_idx
    on public.couple_message_reactions (couple_id, message_id);

create table if not exists public.couple_message_receipts (
    message_id uuid not null references public.couple_messages(id) on delete cascade,
    couple_id uuid not null references public.couples(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    recipient_id uuid not null references auth.users(id) on delete cascade,
    delivered_at timestamptz not null default now(),
    read_at timestamptz,
    primary key (message_id, recipient_id),
    check (sender_id <> recipient_id)
);

create index if not exists couple_message_receipts_sender_idx
    on public.couple_message_receipts (couple_id, sender_id, delivered_at desc);

create table if not exists public.couple_chat_presence (
    couple_id uuid not null references public.couples(id) on delete cascade,
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    last_typed_at timestamptz not null default now(),
    primary key (couple_id, user_id)
);

create table if not exists public.couple_story_comments (
    id uuid primary key default gen_random_uuid(),
    couple_id uuid not null references public.couples(id) on delete cascade,
    story_id uuid not null references public.shared_items(id) on delete cascade,
    author_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    body text not null check (char_length(body) between 1 and 2000),
    created_at timestamptz not null default now()
);

create index if not exists couple_story_comments_story_idx
    on public.couple_story_comments (couple_id, story_id, created_at);

create table if not exists public.couple_story_reactions (
    couple_id uuid not null references public.couples(id) on delete cascade,
    story_id uuid not null references public.shared_items(id) on delete cascade,
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    emoji text not null check (emoji in ('❤️', '😂', '😍', '👍', '🔥', '👏', '😮', '😢')),
    updated_at timestamptz not null default now(),
    primary key (story_id, user_id)
);

create index if not exists couple_story_reactions_couple_idx
    on public.couple_story_reactions (couple_id, story_id);

alter table public.couple_message_reactions enable row level security;
alter table public.couple_message_receipts enable row level security;
alter table public.couple_chat_presence enable row level security;
alter table public.couple_story_comments enable row level security;
alter table public.couple_story_reactions enable row level security;

grant select, insert, update, delete on public.couple_message_reactions to authenticated;
grant select on public.couple_message_receipts to authenticated;
grant select, insert, update on public.couple_chat_presence to authenticated;
grant select, insert, update, delete on public.couple_story_comments to authenticated;
grant select, insert, update, delete on public.couple_story_reactions to authenticated;

create policy "Couple members can read message reactions"
    on public.couple_message_reactions for select to authenticated
    using (public.is_couple_member(public.couple_message_reactions.couple_id)
        and exists (
            select 1 from public.couple_messages m
            where m.id = public.couple_message_reactions.message_id
              and m.couple_id = public.couple_message_reactions.couple_id
        ));
create policy "Members can set their own message reactions"
    on public.couple_message_reactions for insert to authenticated
    with check (public.is_couple_member(public.couple_message_reactions.couple_id)
        and user_id = auth.uid()
        and exists (
            select 1 from public.couple_messages m
            where m.id = public.couple_message_reactions.message_id
              and m.couple_id = public.couple_message_reactions.couple_id
        ));
create policy "Members can update their own message reactions"
    on public.couple_message_reactions for update to authenticated
    using (public.is_couple_member(public.couple_message_reactions.couple_id)
        and user_id = auth.uid())
    with check (public.is_couple_member(public.couple_message_reactions.couple_id)
        and user_id = auth.uid()
        and exists (
            select 1 from public.couple_messages m
            where m.id = public.couple_message_reactions.message_id
              and m.couple_id = public.couple_message_reactions.couple_id
        ));
create policy "Members can delete their own message reactions"
    on public.couple_message_reactions for delete to authenticated
    using (public.is_couple_member(public.couple_message_reactions.couple_id)
        and user_id = auth.uid());

create policy "Couple members can read message receipts"
    on public.couple_message_receipts for select to authenticated
    using (public.is_couple_member(couple_id)
        and auth.uid() in (sender_id, recipient_id));

create policy "Couple members can read chat presence"
    on public.couple_chat_presence for select to authenticated
    using (public.is_couple_member(couple_id));
create policy "Members can publish their own chat presence"
    on public.couple_chat_presence for insert to authenticated
    with check (public.is_couple_member(couple_id) and user_id = auth.uid());
create policy "Members can update their own chat presence"
    on public.couple_chat_presence for update to authenticated
    using (public.is_couple_member(couple_id) and user_id = auth.uid())
    with check (public.is_couple_member(couple_id) and user_id = auth.uid());

create policy "Couple members can read story comments"
    on public.couple_story_comments for select to authenticated
    using (public.is_couple_member(public.couple_story_comments.couple_id)
        and exists (
            select 1 from public.shared_items s
            where s.id = public.couple_story_comments.story_id
              and s.couple_id = public.couple_story_comments.couple_id
              and s.kind = 'note' and s.payload->>'app_key' = 'stories'
        ));
create policy "Members can comment on couple stories"
    on public.couple_story_comments for insert to authenticated
    with check (public.is_couple_member(public.couple_story_comments.couple_id)
        and author_id = auth.uid()
        and exists (
            select 1 from public.shared_items s
            where s.id = public.couple_story_comments.story_id
              and s.couple_id = public.couple_story_comments.couple_id
              and s.kind = 'note' and s.payload->>'app_key' = 'stories'
        ));
create policy "Members can edit their own story comments"
    on public.couple_story_comments for update to authenticated
    using (public.is_couple_member(public.couple_story_comments.couple_id) and author_id = auth.uid())
    with check (public.is_couple_member(public.couple_story_comments.couple_id)
        and author_id = auth.uid()
        and exists (
            select 1 from public.shared_items s
            where s.id = public.couple_story_comments.story_id
              and s.couple_id = public.couple_story_comments.couple_id
              and s.kind = 'note' and s.payload->>'app_key' = 'stories'
        ));
create policy "Members can delete their own story comments"
    on public.couple_story_comments for delete to authenticated
    using (public.is_couple_member(public.couple_story_comments.couple_id)
        and author_id = auth.uid());

create policy "Couple members can read story reactions"
    on public.couple_story_reactions for select to authenticated
    using (public.is_couple_member(public.couple_story_reactions.couple_id)
        and exists (
            select 1 from public.shared_items s
            where s.id = public.couple_story_reactions.story_id
              and s.couple_id = public.couple_story_reactions.couple_id
              and s.kind = 'note' and s.payload->>'app_key' = 'stories'
        ));
create policy "Members can set their own story reactions"
    on public.couple_story_reactions for insert to authenticated
    with check (public.is_couple_member(public.couple_story_reactions.couple_id)
        and user_id = auth.uid()
        and exists (
            select 1 from public.shared_items s
            where s.id = public.couple_story_reactions.story_id
              and s.couple_id = public.couple_story_reactions.couple_id
              and s.kind = 'note' and s.payload->>'app_key' = 'stories'
        ));
create policy "Members can update their own story reactions"
    on public.couple_story_reactions for update to authenticated
    using (public.is_couple_member(public.couple_story_reactions.couple_id)
        and user_id = auth.uid())
    with check (public.is_couple_member(public.couple_story_reactions.couple_id)
        and user_id = auth.uid()
        and exists (
            select 1 from public.shared_items s
            where s.id = public.couple_story_reactions.story_id
              and s.couple_id = public.couple_story_reactions.couple_id
              and s.kind = 'note' and s.payload->>'app_key' = 'stories'
        ));
create policy "Members can delete their own story reactions"
    on public.couple_story_reactions for delete to authenticated
    using (public.is_couple_member(couple_id) and user_id = auth.uid());

create or replace function public.ack_couple_message_delivery(message_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if auth.uid() is null then raise exception 'Sign in first'; end if;
    insert into public.couple_message_receipts (
        message_id, couple_id, sender_id, recipient_id, delivered_at
    )
    select m.id, m.couple_id, m.sender_id, auth.uid(), now()
    from public.couple_messages m
    where m.id = message_id
      and m.sender_id <> auth.uid()
      and public.is_couple_member(m.couple_id)
    on conflict (message_id, recipient_id) do update
        set delivered_at = coalesce(public.couple_message_receipts.delivered_at, excluded.delivered_at);
end;
$$;

create or replace function public.ack_couple_message_read(message_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if auth.uid() is null then raise exception 'Sign in first'; end if;
    update public.couple_message_receipts r
    set read_at = coalesce(r.read_at, now())
    where r.message_id = message_id
      and r.recipient_id = auth.uid()
      and public.is_couple_member(r.couple_id);
end;
$$;

create or replace function public.publish_couple_chat_typing()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare target_couple uuid;
begin
    if auth.uid() is null then raise exception 'Sign in first'; end if;
    select couple_id into target_couple
    from public.couple_members
    where user_id = auth.uid();
    if target_couple is null then raise exception 'Join a couple first'; end if;
    insert into public.couple_chat_presence (couple_id, user_id, last_typed_at)
    values (target_couple, auth.uid(), now())
    on conflict (couple_id, user_id) do update set last_typed_at = excluded.last_typed_at;
end;
$$;

revoke all on function public.ack_couple_message_delivery(uuid) from public;
revoke all on function public.ack_couple_message_read(uuid) from public;
revoke all on function public.publish_couple_chat_typing() from public;
grant execute on function public.ack_couple_message_delivery(uuid) to authenticated;
grant execute on function public.ack_couple_message_read(uuid) to authenticated;
grant execute on function public.publish_couple_chat_typing() to authenticated;

commit;
