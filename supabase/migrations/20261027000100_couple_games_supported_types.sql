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

commit;
