-- Kosht cloud sync schema.
--
-- One generic row table instead of a mirror of every Room table: sync is
-- per-record (each row merges on its own, last write wins by updated_at),
-- but the app can add or change a field without a server migration. The
-- payload keeps the entity's own field names, so it stays readable, e.g.
--   select payload->>'amountMinor' from sync_rows where entity = 'transactions';
--
-- Apply with:
--   psql -h <pooler-host> -p 5432 -U postgres.<ref> -d postgres -f docs/supabase-schema.sql

create table if not exists public.sync_rows (
    user_id    uuid        not null references auth.users (id) on delete cascade,
    entity     text        not null,
    uid        text        not null,
    -- Device clock, epoch millis. The only input to conflict resolution.
    updated_at bigint      not null,
    -- Tombstone: the row is kept so other devices learn about the delete.
    deleted    boolean     not null default false,
    payload    jsonb       not null default '{}'::jsonb,
    synced_at  timestamptz not null default now(),
    primary key (user_id, entity, uid),
    constraint sync_rows_entity_known check (
        entity in (
            'accounts', 'categories', 'saving_goals', 'transactions',
            'recurring', 'savings', 'challenges', 'debts', 'awards'
        )
    )
);

-- Every pull is "give me everything newer than X".
create index if not exists sync_rows_pull_idx
    on public.sync_rows (user_id, updated_at);

alter table public.sync_rows enable row level security;

-- The APK ships the anon key, so row level security is the only thing
-- standing between one user's data and another's. Signed-out clients get
-- nothing at all.
revoke all on public.sync_rows from anon;

drop policy if exists sync_rows_owner on public.sync_rows;
create policy sync_rows_owner on public.sync_rows
    for all
    to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());
