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

-- Is this address already taken?
--
-- Supabase deliberately refuses to answer that, to stop anyone harvesting
-- which addresses are registered. The app asks anyway, because the
-- alternative is worse for the person signing up: without it, entering an
-- address you already have quietly turns into a sign-in code, and "forgot my
-- password" on an unknown address silently does nothing.
--
-- The trade is narrow on purpose: a boolean, nothing else, and no way to list
-- anything. Someone holding the public anon key can test one address at a
-- time -- the same thing almost any "email already in use" form allows.
create or replace function public.email_registered(check_email text)
    returns boolean
    language sql
    security definer
    set search_path = ''
as $$
    select exists (
        select 1 from auth.users
        where lower(email) = lower(trim(check_email))
    );
$$;

revoke all on function public.email_registered(text) from public;
grant execute on function public.email_registered(text) to anon, authenticated;

-- ===========================================================================
-- Accounts, consent and data-subject requests.
--
-- Written against the Belarusian personal data law (07.05.2021 No. 99-З) and
-- the advertising law, which between them require: consent as the basis for
-- processing, consent to advertising given separately and in advance,
-- withdrawal at any time without explanation, and fifteen days to act on a
-- written request from the person.
--
-- The shape follows from that: consent is an append-only ledger rather than a
-- flag, because what has to be provable is *when* someone agreed and to which
-- wording -- and a flag that gets overwritten proves nothing.
-- ===========================================================================

create table if not exists public.profiles (
    user_id    uuid        primary key references auth.users (id) on delete cascade,
    email      text        not null,
    created_at timestamptz not null default now(),
    -- Unsubscribing from a mailing must work straight from the email, with no
    -- signing in first, so the link carries this instead of a session.
    unsubscribe_token uuid not null default gen_random_uuid()
);

create unique index if not exists profiles_unsubscribe_token_idx
    on public.profiles (unsubscribe_token);

alter table public.profiles enable row level security;
revoke all on public.profiles from anon;

drop policy if exists profiles_owner on public.profiles;
create policy profiles_owner on public.profiles
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- A profile row appears with the account, not on the app's next round trip.
create or replace function public.handle_new_user()
    returns trigger
    language plpgsql
    security definer
    set search_path = ''
as $$
begin
    insert into public.profiles (user_id, email)
    values (new.id, new.email)
    on conflict (user_id) do update set email = excluded.email;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- Accounts that predate the trigger still need a profile.
insert into public.profiles (user_id, email)
select id, email from auth.users
on conflict (user_id) do nothing;

do $$ begin
    create type public.consent_kind as enum ('privacy_policy', 'marketing_email');
exception when duplicate_object then null;
end $$;

create table if not exists public.consents (
    id         bigint generated always as identity primary key,
    user_id    uuid    not null references auth.users (id) on delete cascade,
    kind       public.consent_kind not null,
    granted    boolean not null,
    -- Which wording was agreed to. New wording needs fresh agreement.
    policy_version text not null default '',
    -- Where the person clicked, so a consent can be traced to its screen.
    source     text    not null,
    created_at timestamptz not null default now()
);

create index if not exists consents_latest_idx
    on public.consents (user_id, kind, created_at desc);

alter table public.consents enable row level security;
revoke all on public.consents from anon;

-- Read and append only, on purpose: no update or delete policy exists, so
-- consent history cannot be rewritten after the fact -- not even by its owner.
drop policy if exists consents_read on public.consents;
create policy consents_read on public.consents
    for select to authenticated using (user_id = auth.uid());

drop policy if exists consents_append on public.consents;
create policy consents_append on public.consents
    for insert to authenticated with check (user_id = auth.uid());

-- The latest word on each kind of consent.
create or replace view public.current_consents
    with (security_invoker = on) as
select distinct on (user_id, kind)
    user_id, kind, granted, policy_version, created_at
from public.consents
order by user_id, kind, created_at desc;

do $$ begin
    create type public.data_request_kind as enum ('access', 'deletion', 'withdraw_consent');
exception when duplicate_object then null;
end $$;

create table if not exists public.data_requests (
    id         bigint generated always as identity primary key,
    user_id    uuid not null references auth.users (id) on delete cascade,
    kind       public.data_request_kind not null,
    created_at timestamptz not null default now(),
    -- The law allows fifteen days to act on a request; storing the deadline
    -- makes an overdue one visible instead of merely regrettable.
    due_at     timestamptz not null default (now() + interval '15 days'),
    completed_at timestamptz,
    note       text not null default ''
);

create index if not exists data_requests_open_idx
    on public.data_requests (due_at) where completed_at is null;

alter table public.data_requests enable row level security;
revoke all on public.data_requests from anon;

drop policy if exists data_requests_read on public.data_requests;
create policy data_requests_read on public.data_requests
    for select to authenticated using (user_id = auth.uid());

drop policy if exists data_requests_append on public.data_requests;
create policy data_requests_append on public.data_requests
    for insert to authenticated with check (user_id = auth.uid());

-- Unsubscribe straight from a mailing, without a session. Returns false for
-- an unknown token rather than saying whether it ever existed.
create or replace function public.unsubscribe_marketing(token uuid)
    returns boolean
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    target uuid;
begin
    select user_id into target from public.profiles where unsubscribe_token = token;
    if target is null then
        return false;
    end if;
    insert into public.consents (user_id, kind, granted, source)
    values (target, 'marketing_email', false, 'email_unsubscribe');
    return true;
end;
$$;

revoke all on function public.unsubscribe_marketing(uuid) from public;
grant execute on function public.unsubscribe_marketing(uuid) to anon, authenticated;

-- Everything the account has, in one document, for the right to know what is
-- being processed.
create or replace function public.export_my_data()
    returns jsonb
    language sql
    security invoker
    set search_path = ''
as $$
    select jsonb_build_object(
        'account', (
            select to_jsonb(p) - 'unsubscribe_token'
            from public.profiles p where p.user_id = auth.uid()
        ),
        'consents', coalesce((
            select jsonb_agg(to_jsonb(c) order by c.created_at)
            from public.consents c where c.user_id = auth.uid()
        ), '[]'::jsonb),
        'requests', coalesce((
            select jsonb_agg(to_jsonb(r) order by r.created_at)
            from public.data_requests r where r.user_id = auth.uid()
        ), '[]'::jsonb),
        'records', coalesce((
            select jsonb_agg(jsonb_build_object(
                'entity', s.entity, 'uid', s.uid, 'deleted', s.deleted,
                'updated_at', s.updated_at, 'payload', s.payload
            ) order by s.entity, s.uid)
            from public.sync_rows s where s.user_id = auth.uid()
        ), '[]'::jsonb),
        'exported_at', now()
    );
$$;

grant execute on function public.export_my_data() to authenticated;

-- Erasure, carried out rather than promised: deleting the account cascades
-- through every table that references it.
create or replace function public.delete_my_account()
    returns void
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    me uuid := auth.uid();
begin
    if me is null then
        raise exception 'not signed in';
    end if;
    delete from auth.users where id = me;
end;
$$;

revoke all on function public.delete_my_account() from public;
grant execute on function public.delete_my_account() to authenticated;
