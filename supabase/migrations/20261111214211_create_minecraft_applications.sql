create table public.minecraft_applications (
  id uuid primary key default gen_random_uuid(),
  player_uuid uuid not null,
  minecraft_username text not null check (char_length(minecraft_username) between 1 and 16),
  first_name text not null check (char_length(first_name) between 1 and 30),
  edition text not null check (edition in ('JAVA', 'BEDROCK')),
  server_name text not null,
  server_version text not null,
  submitted_at timestamptz not null,
  notification_sent_at timestamptz,
  email_provider_id text,
  notification_error text,
  created_at timestamptz not null default now()
);

create index minecraft_applications_player_uuid_created_idx
  on public.minecraft_applications (player_uuid, created_at desc);

alter table public.minecraft_applications enable row level security;

-- The Edge Function is the only application-facing database writer.
revoke all on table public.minecraft_applications
  from public, anon, authenticated, service_role;
grant select, insert, update on table public.minecraft_applications
  to service_role;

comment on table public.minecraft_applications is
  'Private Argon Minecraft applications. Stores player-submitted first name and Minecraft identifiers plus non-network session metadata. IP addresses are deliberately not collected.';
