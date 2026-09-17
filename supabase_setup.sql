-- ============================================================================
-- ROADBOOK: PRODUCTION SUPABASE SQL MIGRATION (HARDENED MULTI-TENANT)
-- ============================================================================
-- Run this in your Supabase Dashboard -> SQL Editor
-- This script is completely idempotent and safe to run on fresh or existing databases.
-- ============================================================================

-- Enable required extensions
create extension if not exists "uuid-ossp";
create extension if not exists "pgcrypto";

-- ----------------------------------------------------------------------------
-- 1. BASE TABLES DEFINITION
-- ----------------------------------------------------------------------------

-- Businesses Table
create table if not exists public.businesses (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    join_code text not null,
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- Case-insensitive uniqueness on join_code (e.g. ABCD and abcd are treated as identical)
create unique index if not exists idx_businesses_join_code_case_insensitive 
on public.businesses (upper(trim(join_code)));

-- User Profiles Table (Linked to auth.users)
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    business_id uuid references public.businesses(id) on delete cascade,
    name text not null,
    email text not null,
    role text not null check (role in ('ADMIN', 'MANAGER', 'DRIVER')),
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- Vehicles Table
create table if not exists public.vehicles (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    name text not null,
    number_plate text not null,
    type text not null,
    notes text default '',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- Drivers Table
create table if not exists public.drivers (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    name text not null,
    phone text not null,
    payment_type text not null,
    rate double precision not null default 0.0,
    assigned_vehicle_id uuid,
    notes text default '',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- Bookings Table
create table if not exists public.bookings (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    vehicle_id uuid not null,
    driver_id uuid,
    date bigint not null,
    customer_route text not null,
    amount double precision not null default 0.0,
    payment_status text not null default 'PENDING',
    notes text default '',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- Expenses Table
create table if not exists public.expenses (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    vehicle_id uuid not null,
    driver_id uuid,
    category text not null,
    amount double precision not null default 0.0,
    date bigint not null,
    notes text default '',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- Driver Payments Table
create table if not exists public.driver_payments (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    driver_id uuid not null,
    vehicle_id uuid,
    amount double precision not null default 0.0,
    date bigint not null,
    notes text default '',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- ----------------------------------------------------------------------------
-- 2. COMPOSITE KEYS & CROSS-BUSINESS RELATIONSHIP CONSTRAINTS
-- Safely drop legacy single-column FKs to avoid conflicts, then enforce
-- same-tenant composite foreign keys (e.g. referencing (id, business_id)).
-- ----------------------------------------------------------------------------

-- Ensure composite uniqueness on vehicles and drivers for composite FK targets
do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.vehicles'::regclass
      and conname = 'uq_vehicles_id_business'
  ) then
    alter table public.vehicles add constraint uq_vehicles_id_business unique (id, business_id);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.drivers'::regclass
      and conname = 'uq_drivers_id_business'
  ) then
    alter table public.drivers add constraint uq_drivers_id_business unique (id, business_id);
  end if;
end $$;

-- Drop obsolete single-column foreign keys if present on existing databases
do $$
declare
  r record;
begin
  -- 1) drivers.assigned_vehicle_id referencing vehicles(id)
  for r in (
    select conname
    from pg_constraint
    where conrelid = 'public.drivers'::regclass
      and contype = 'f'
      and conkey = array(
        select attnum
        from pg_attribute
        where attrelid = 'public.drivers'::regclass
          and attname = 'assigned_vehicle_id'
      )
      and confrelid = 'public.vehicles'::regclass
  ) loop
    execute format('alter table public.drivers drop constraint if exists %I', r.conname);
  end loop;

  -- 2) bookings.vehicle_id referencing vehicles(id)
  for r in (
    select conname
    from pg_constraint
    where conrelid = 'public.bookings'::regclass
      and contype = 'f'
      and conkey = array(
        select attnum
        from pg_attribute
        where attrelid = 'public.bookings'::regclass
          and attname = 'vehicle_id'
      )
      and confrelid = 'public.vehicles'::regclass
  ) loop
    execute format('alter table public.bookings drop constraint if exists %I', r.conname);
  end loop;

  -- 3) bookings.driver_id referencing drivers(id)
  for r in (
    select conname
    from pg_constraint
    where conrelid = 'public.bookings'::regclass
      and contype = 'f'
      and conkey = array(
        select attnum
        from pg_attribute
        where attrelid = 'public.bookings'::regclass
          and attname = 'driver_id'
      )
      and confrelid = 'public.drivers'::regclass
  ) loop
    execute format('alter table public.bookings drop constraint if exists %I', r.conname);
  end loop;

  -- 4) expenses.vehicle_id referencing vehicles(id)
  for r in (
    select conname
    from pg_constraint
    where conrelid = 'public.expenses'::regclass
      and contype = 'f'
      and conkey = array(
        select attnum
        from pg_attribute
        where attrelid = 'public.expenses'::regclass
          and attname = 'vehicle_id'
      )
      and confrelid = 'public.vehicles'::regclass
  ) loop
    execute format('alter table public.expenses drop constraint if exists %I', r.conname);
  end loop;

  -- 5) expenses.driver_id referencing drivers(id)
  for r in (
    select conname
    from pg_constraint
    where conrelid = 'public.expenses'::regclass
      and contype = 'f'
      and conkey = array(
        select attnum
        from pg_attribute
        where attrelid = 'public.expenses'::regclass
          and attname = 'driver_id'
      )
      and confrelid = 'public.drivers'::regclass
  ) loop
    execute format('alter table public.expenses drop constraint if exists %I', r.conname);
  end loop;

  -- 6) driver_payments.driver_id referencing drivers(id)
  for r in (
    select conname
    from pg_constraint
    where conrelid = 'public.driver_payments'::regclass
      and contype = 'f'
      and conkey = array(
        select attnum
        from pg_attribute
        where attrelid = 'public.driver_payments'::regclass
          and attname = 'driver_id'
      )
      and confrelid = 'public.drivers'::regclass
  ) loop
    execute format('alter table public.driver_payments drop constraint if exists %I', r.conname);
  end loop;

  -- 7) driver_payments.vehicle_id referencing vehicles(id)
  for r in (
    select conname
    from pg_constraint
    where conrelid = 'public.driver_payments'::regclass
      and contype = 'f'
      and conkey = array(
        select attnum
        from pg_attribute
        where attrelid = 'public.driver_payments'::regclass
          and attname = 'vehicle_id'
      )
      and confrelid = 'public.vehicles'::regclass
  ) loop
    execute format('alter table public.driver_payments drop constraint if exists %I', r.conname);
  end loop;
end $$;

-- Enforce composite foreign keys (ensuring cross-business references are physically impossible)
do $$
begin
  -- drivers.assigned_vehicle_id -> vehicles(id, business_id)
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.drivers'::regclass
      and conname = 'fk_drivers_assigned_vehicle'
  ) then
    alter table public.drivers
      add constraint fk_drivers_assigned_vehicle
      foreign key (assigned_vehicle_id, business_id)
      references public.vehicles(id, business_id)
      on delete set null;
  end if;

  -- bookings.vehicle_id -> vehicles(id, business_id)
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.bookings'::regclass
      and conname = 'fk_bookings_vehicle'
  ) then
    alter table public.bookings
      add constraint fk_bookings_vehicle
      foreign key (vehicle_id, business_id)
      references public.vehicles(id, business_id)
      on delete cascade;
  end if;

  -- bookings.driver_id -> drivers(id, business_id)
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.bookings'::regclass
      and conname = 'fk_bookings_driver'
  ) then
    alter table public.bookings
      add constraint fk_bookings_driver
      foreign key (driver_id, business_id)
      references public.drivers(id, business_id)
      on delete set null;
  end if;

  -- expenses.vehicle_id -> vehicles(id, business_id)
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.expenses'::regclass
      and conname = 'fk_expenses_vehicle'
  ) then
    alter table public.expenses
      add constraint fk_expenses_vehicle
      foreign key (vehicle_id, business_id)
      references public.vehicles(id, business_id)
      on delete cascade;
  end if;

  -- expenses.driver_id -> drivers(id, business_id)
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.expenses'::regclass
      and conname = 'fk_expenses_driver'
  ) then
    alter table public.expenses
      add constraint fk_expenses_driver
      foreign key (driver_id, business_id)
      references public.drivers(id, business_id)
      on delete set null;
  end if;

  -- driver_payments.driver_id -> drivers(id, business_id)
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.driver_payments'::regclass
      and conname = 'fk_driver_payments_driver'
  ) then
    alter table public.driver_payments
      add constraint fk_driver_payments_driver
      foreign key (driver_id, business_id)
      references public.drivers(id, business_id)
      on delete cascade;
  end if;

  -- driver_payments.vehicle_id -> vehicles(id, business_id)
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.driver_payments'::regclass
      and conname = 'fk_driver_payments_vehicle'
  ) then
    alter table public.driver_payments
      add constraint fk_driver_payments_vehicle
      foreign key (vehicle_id, business_id)
      references public.vehicles(id, business_id)
      on delete set null;
  end if;
end $$;

-- ----------------------------------------------------------------------------
-- 3. REPLICA IDENTITY & PERFORMANCE INDEXES
-- ----------------------------------------------------------------------------

alter table public.businesses replica identity full;
alter table public.profiles replica identity full;
alter table public.vehicles replica identity full;
alter table public.drivers replica identity full;
alter table public.bookings replica identity full;
alter table public.expenses replica identity full;
alter table public.driver_payments replica identity full;

create index if not exists idx_profiles_business on public.profiles(business_id);
create index if not exists idx_vehicles_business on public.vehicles(business_id);
create index if not exists idx_drivers_business on public.drivers(business_id);
create index if not exists idx_bookings_business on public.bookings(business_id);
create index if not exists idx_bookings_vehicle on public.bookings(vehicle_id);
create index if not exists idx_bookings_driver on public.bookings(driver_id);
create index if not exists idx_bookings_date on public.bookings(date);
create index if not exists idx_expenses_business on public.expenses(business_id);
create index if not exists idx_expenses_vehicle on public.expenses(vehicle_id);
create index if not exists idx_driver_payments_business on public.driver_payments(business_id);
create index if not exists idx_driver_payments_driver on public.driver_payments(driver_id);

-- ----------------------------------------------------------------------------
-- 4. SECURITY DEFINER HELPER FUNCTIONS
-- Strict SET search_path = '' to eliminate search-path hijacking.
-- Fully schema-qualified database objects.
-- Revoked from public; granted only to authenticated role.
-- ----------------------------------------------------------------------------

create or replace function public.get_auth_business_id()
returns uuid
language sql stable security definer
set search_path = ''
as $$
  select p.business_id from public.profiles p where p.id = auth.uid();
$$;

revoke all on function public.get_auth_business_id() from public;
grant execute on function public.get_auth_business_id() to authenticated;

create or replace function public.get_auth_role()
returns text
language sql stable security definer
set search_path = ''
as $$
  select p.role from public.profiles p where p.id = auth.uid();
$$;

revoke all on function public.get_auth_role() from public;
grant execute on function public.get_auth_role() to authenticated;

-- ----------------------------------------------------------------------------
-- 5. BUSINESS RPCs: CREATE & JOIN
-- ----------------------------------------------------------------------------

-- Atomically create a new business and assign the creator as ADMIN
create or replace function public.create_business(p_name text, p_join_code text)
returns jsonb
language plpgsql security definer
set search_path = ''
as $$
declare
  v_user_id uuid;
  v_curr_business_id uuid;
  v_clean_name text;
  v_clean_code text;
  v_business public.businesses%rowtype;
begin
  v_user_id := auth.uid();
  if v_user_id is null then
    raise exception 'Not authenticated';
  end if;

  select p.business_id into v_curr_business_id from public.profiles p where p.id = v_user_id;
  if v_curr_business_id is not null then
    raise exception 'User already belongs to a business';
  end if;

  v_clean_name := pg_catalog.trim(p_name);
  v_clean_code := pg_catalog.upper(pg_catalog.trim(p_join_code));

  if pg_catalog.length(v_clean_name) < 2 then
    raise exception 'Business name must be at least 2 characters';
  end if;

  if pg_catalog.length(v_clean_code) < 4 then
    raise exception 'Join code must be at least 4 characters';
  end if;

  insert into public.businesses (name, join_code)
  values (v_clean_name, v_clean_code)
  returning * into v_business;

  update public.profiles
  set business_id = v_business.id,
      role = 'ADMIN',
      updated_at = pg_catalog.now()
  where id = v_user_id;

  return pg_catalog.to_jsonb(v_business);
end;
$$;

revoke all on function public.create_business(text, text) from public;
grant execute on function public.create_business(text, text) to authenticated;

-- Atomically validate join code and assign user to the business.
-- Joining user is ALWAYS initialized with the DRIVER role.
create or replace function public.join_business_by_code(p_join_code text, p_requested_role text default null)
returns jsonb
language plpgsql security definer
set search_path = ''
as $$
declare
  v_user_id uuid;
  v_curr_business_id uuid;
  v_clean_code text;
  v_business public.businesses%rowtype;
begin
  v_user_id := auth.uid();
  if v_user_id is null then
    raise exception 'Not authenticated';
  end if;

  select p.business_id into v_curr_business_id from public.profiles p where p.id = v_user_id;
  if v_curr_business_id is not null then
    raise exception 'User already belongs to a business';
  end if;

  v_clean_code := pg_catalog.upper(pg_catalog.trim(p_join_code));
  if pg_catalog.length(v_clean_code) < 4 then
    raise exception 'Invalid join code length';
  end if;

  select * into v_business
  from public.businesses b
  where pg_catalog.upper(pg_catalog.trim(b.join_code)) = v_clean_code;

  if not found then
    raise exception 'Invalid join code: business not found';
  end if;

  -- Joining users are ALWAYS initialized as DRIVER (promotion to MANAGER or ADMIN requires an ADMIN)
  update public.profiles
  set business_id = v_business.id,
      role = 'DRIVER',
      updated_at = pg_catalog.now()
  where id = v_user_id;

  return pg_catalog.to_jsonb(v_business);
end;
$$;

revoke all on function public.join_business_by_code(text, text) from public;
grant execute on function public.join_business_by_code(text, text) to authenticated;

-- ----------------------------------------------------------------------------
-- 6. PROFILE LIFECYCLE & IMMUTABILITY TRIGGERS
-- ----------------------------------------------------------------------------

-- Trigger to create initial unassigned profile on auth registration
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, email, name, role, business_id)
  values (
    new.id,
    pg_catalog.coalesce(new.email, ''),
    pg_catalog.coalesce(new.raw_user_meta_data->>'name', pg_catalog.split_part(pg_catalog.coalesce(new.email, 'User'), '@', 1)),
    'DRIVER',
    null
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Trigger to protect profile immutability and role management
create or replace function public.protect_profile_immutability()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
  v_caller_role text;
  v_caller_business uuid;
begin
  v_caller_role := public.get_auth_role();
  v_caller_business := public.get_auth_business_id();

  -- Case 1: Another user is updating this profile -> MUST be ADMIN of the SAME business
  if auth.uid() != old.id then
    if v_caller_role = 'ADMIN' and old.business_id is not null and old.business_id = v_caller_business then
      -- ADMIN cannot move a member to another business
      if new.business_id is distinct from old.business_id then
        raise exception 'Admins cannot transfer members to a different business';
      end if;
      return new;
    else
      raise exception 'Permission denied: only business admins can update member profiles';
    end if;
  end if;

  -- Case 2: The user is updating their own profile
  if auth.uid() = old.id then
    -- Users cannot directly alter their own business_id
    if new.business_id is distinct from old.business_id then
      raise exception 'Direct modification of business_id is prohibited. Use create_business or join_business_by_code.';
    end if;

    -- Users cannot promote themselves or change their own role
    if new.role is distinct from old.role then
      raise exception 'Users cannot alter their own role';
    end if;

    return new;
  end if;

  raise exception 'Permission denied';
end;
$$;

drop trigger if exists trg_protect_profile_immutability on public.profiles;
create trigger trg_protect_profile_immutability
  before update on public.profiles
  for each row
  execute function public.protect_profile_immutability();

-- ----------------------------------------------------------------------------
-- 7. ROW LEVEL SECURITY (RLS) ACTIVATION
-- ----------------------------------------------------------------------------

alter table public.businesses enable row level security;
alter table public.profiles enable row level security;
alter table public.vehicles enable row level security;
alter table public.drivers enable row level security;
alter table public.bookings enable row level security;
alter table public.expenses enable row level security;
alter table public.driver_payments enable row level security;

-- ----------------------------------------------------------------------------
-- 8. ROW LEVEL SECURITY POLICIES
--
-- STRICT MULTI-TENANT ISOLATION:
-- Every query filters on `business_id = public.get_auth_business_id()`.
-- Business A members can never view, insert, update, or delete Business B data.
-- All members (ADMIN, MANAGER, DRIVER) of the same business can VIEW all data.
-- ----------------------------------------------------------------------------

-- ============================================================================
-- TABLE: businesses
-- ============================================================================
drop policy if exists "Members can view their own business" on public.businesses;
create policy "Members can view their own business"
on public.businesses for select
using (id = public.get_auth_business_id());

-- Direct client INSERT into businesses is completely disabled (handled via create_business RPC)
drop policy if exists "Authenticated users can insert business" on public.businesses;

drop policy if exists "Admins can update their business" on public.businesses;
create policy "Admins can update their business"
on public.businesses for update
using (id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN')
with check (id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

drop policy if exists "Admins can delete their business" on public.businesses;
create policy "Admins can delete their business"
on public.businesses for delete
using (id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

-- ============================================================================
-- TABLE: profiles
-- ============================================================================
drop policy if exists "Members can view profiles in same business" on public.profiles;
create policy "Members can view profiles in same business"
on public.profiles for select
using (id = auth.uid() or (business_id is not null and business_id = public.get_auth_business_id()));

drop policy if exists "Users can insert own initial profile" on public.profiles;
create policy "Users can insert own initial profile"
on public.profiles for insert
with check (
  id = auth.uid()
  and business_id is null
  and role = 'DRIVER'
);

drop policy if exists "Profiles update policy" on public.profiles;
create policy "Profiles update policy"
on public.profiles for update
using (
  id = auth.uid()
  or (business_id is not null and business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN')
)
with check (
  id = auth.uid()
  or (business_id is not null and business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN')
);

-- ============================================================================
-- TABLE: vehicles
-- ============================================================================
drop policy if exists "Members can view all vehicles in business" on public.vehicles;
create policy "Members can view all vehicles in business"
on public.vehicles for select
using (business_id = public.get_auth_business_id());

drop policy if exists "Admins and Managers can insert vehicles" on public.vehicles;
create policy "Admins and Managers can insert vehicles"
on public.vehicles for insert
with check (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

drop policy if exists "Admins and Managers can update vehicles" on public.vehicles;
create policy "Admins and Managers can update vehicles"
on public.vehicles for update
using (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'))
with check (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

drop policy if exists "Admins can delete vehicles" on public.vehicles;
create policy "Admins can delete vehicles"
on public.vehicles for delete
using (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

-- ============================================================================
-- TABLE: drivers
-- ============================================================================
drop policy if exists "Members can view all drivers in business" on public.drivers;
create policy "Members can view all drivers in business"
on public.drivers for select
using (business_id = public.get_auth_business_id());

drop policy if exists "Admins and Managers can insert drivers" on public.drivers;
create policy "Admins and Managers can insert drivers"
on public.drivers for insert
with check (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

drop policy if exists "Admins and Managers can update drivers" on public.drivers;
create policy "Admins and Managers can update drivers"
on public.drivers for update
using (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'))
with check (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

drop policy if exists "Admins can delete drivers" on public.drivers;
create policy "Admins can delete drivers"
on public.drivers for delete
using (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

-- ============================================================================
-- TABLE: bookings
-- ============================================================================
drop policy if exists "Members can view all bookings in business" on public.bookings;
create policy "Members can view all bookings in business"
on public.bookings for select
using (business_id = public.get_auth_business_id());

drop policy if exists "Members can insert bookings" on public.bookings;
create policy "Members can insert bookings"
on public.bookings for insert
with check (business_id = public.get_auth_business_id());

drop policy if exists "Members can update bookings" on public.bookings;
create policy "Members can update bookings"
on public.bookings for update
using (business_id = public.get_auth_business_id())
with check (business_id = public.get_auth_business_id());

drop policy if exists "Admins can delete bookings" on public.bookings;
create policy "Admins can delete bookings"
on public.bookings for delete
using (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

-- ============================================================================
-- TABLE: expenses
-- ============================================================================
drop policy if exists "Members can view all expenses in business" on public.expenses;
create policy "Members can view all expenses in business"
on public.expenses for select
using (business_id = public.get_auth_business_id());

drop policy if exists "Members can insert expenses" on public.expenses;
create policy "Members can insert expenses"
on public.expenses for insert
with check (business_id = public.get_auth_business_id());

drop policy if exists "Members can update expenses" on public.expenses;
create policy "Members can update expenses"
on public.expenses for update
using (business_id = public.get_auth_business_id())
with check (business_id = public.get_auth_business_id());

drop policy if exists "Admins and Managers can delete expenses" on public.expenses;
create policy "Admins and Managers can delete expenses"
on public.expenses for delete
using (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

-- ============================================================================
-- TABLE: driver_payments
-- ============================================================================
drop policy if exists "Members can view all driver payments in business" on public.driver_payments;
create policy "Members can view all driver payments in business"
on public.driver_payments for select
using (business_id = public.get_auth_business_id());

drop policy if exists "Admins can insert driver payments" on public.driver_payments;
create policy "Admins can insert driver payments"
on public.driver_payments for insert
with check (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

drop policy if exists "Admins can update driver payments" on public.driver_payments;
create policy "Admins can update driver payments"
on public.driver_payments for update
using (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN')
with check (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

drop policy if exists "Admins can delete driver payments" on public.driver_payments;
create policy "Admins can delete driver payments"
on public.driver_payments for delete
using (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

-- ----------------------------------------------------------------------------
-- 9. REALTIME SUBSCRIPTIONS
-- ----------------------------------------------------------------------------

do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'businesses') then
    alter publication supabase_realtime add table public.businesses;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'profiles') then
    alter publication supabase_realtime add table public.profiles;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'vehicles') then
    alter publication supabase_realtime add table public.vehicles;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'drivers') then
    alter publication supabase_realtime add table public.drivers;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'bookings') then
    alter publication supabase_realtime add table public.bookings;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'expenses') then
    alter publication supabase_realtime add table public.expenses;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'driver_payments') then
    alter publication supabase_realtime add table public.driver_payments;
  end if;
end $$;
