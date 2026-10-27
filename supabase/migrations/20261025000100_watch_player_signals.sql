begin;

alter table public.watch_signals
    drop constraint if exists watch_signals_kind_check;

alter table public.watch_signals
    add constraint watch_signals_kind_check
    check (kind in (
        'offer',
        'answer',
        'ice',
        'chat',
        'close',
        'player_source',
        'playback',
        'playback_request'
    ));

commit;
