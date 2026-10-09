# Supabase backup (optional)

Nama can upload one backup row per user. Create this table in the Supabase SQL editor:

```sql
create table if not exists public.nama_backups (
  user_id uuid primary key references auth.users (id) on delete cascade,
  data jsonb not null,
  updated_at timestamptz not null default now()
);

alter table public.nama_backups enable row level security;

create policy "own backup: read"   on public.nama_backups for select using (auth.uid() = user_id);
create policy "own backup: insert" on public.nama_backups for insert with check (auth.uid() = user_id);
create policy "own backup: update" on public.nama_backups for update using (auth.uid() = user_id);
```

Then in Nama: Settings › Backup & sync › Supabase, enter the project URL, the anon key, and an email/password.
Bank cards are never included in backups.

Supabase may not be reachable from Iran without a VPN. Test it first; a self-hosted Supabase on an Iranian server works the same way.
