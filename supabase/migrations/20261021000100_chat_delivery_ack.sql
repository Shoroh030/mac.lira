-- Chat rows are a delivery queue. A receiver acknowledges only after saving locally.
create or replace function public.ack_couple_message_delivery(message_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if auth.uid() is null then raise exception 'Sign in first'; end if;
    delete from public.couple_messages m
    where m.id = message_id
      and m.sender_id <> auth.uid()
      and public.is_couple_member(m.couple_id);
end;
$$;
revoke all on function public.ack_couple_message_delivery(uuid) from public;
grant execute on function public.ack_couple_message_delivery(uuid) to authenticated;
