alter table public.couple_messages
    add column if not exists edited_at timestamptz;

grant update (body, edited_at) on public.couple_messages to authenticated;

create policy "Senders can edit own couple messages"
    on public.couple_messages for update to authenticated
    using (
        public.is_couple_member(couple_id)
        and sender_id = auth.uid()
    )
    with check (
        public.is_couple_member(couple_id)
        and sender_id = auth.uid()
    );
