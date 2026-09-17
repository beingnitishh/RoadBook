-- ============================================================================
-- ROADBOOK SUPABASE SETUP SCRIPT
-- Run this in your Supabase project's SQL Editor (Dashboard -> SQL Editor)
-- ============================================================================

-- Enable UUID extension
create extension if not exists "uuid-ossp";

-- 1. Businesses Table
create table if not exists public.businesses (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    join_code text unique not null,
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- 2. User Profiles Table (Linked to auth.users)
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    business_id uuid references public.businesses(id) on delete cascade,
    name text not null,
    email text not null,
    role text not null check (role in ('ADMIN', 'MANAGER', 'DRIVER')),
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- 3. Vehicles Table
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

-- 4. Drivers Table
create table if not exists public.drivers (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    name text not null,
    phone text not null,
    payment_type text not null,
    rate double precision not null default 0.0,
    assigned_vehicle_id uuid references public.vehicles(id) on delete set null,
    notes text default '',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- 5. Bookings Table
create table if not exists public.bookings (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    vehicle_id uuid not null references public.vehicles(id) on delete cascade,
    driver_id uuid references public.drivers(id) on delete set null,
    date bigint not null,
    customer_route text not null,
    amount double precision not null default 0.0,
    payment_status text not null default 'PENDING',
    notes text default '',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- 6. Expenses Table
create table if not exists public.expenses (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    vehicle_id uuid not null references public.vehicles(id) on delete cascade,
    driver_id uuid references public.drivers(id) on delete set null,
    category text not null,
    amount double precision not null default 0.0,
    date bigint not null,
    notes text default '',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- 7. Driver Payments Table
create table if not exists public.driver_payments (
    id uuid primary key default gen_random_uuid(),
    business_id uuid not null references public.businesses(id) on delete cascade,
    driver_id uuid not null references public.drivers(id) on delete cascade,
    vehicle_id uuid references public.vehicles(id) on delete set null,
    amount double precision not null default 0.0,
    date bigint not null,
    notes text default '',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- Enable full replica identity so Realtime payloads include complete records on UPDATE and DELETE
alter table public.businesses replica identity full;
alter table public.profiles replica identity full;
alter table public.vehicles replica identity full;
alter table public.drivers replica identity full;
alter table public.bookings replica identity full;
alter table public.expenses replica identity full;
alter table public.driver_payments replica identity full;

-- Indexes for high-performance querying
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

-- ============================================================================
-- HELPER FUNCTIONS FOR ROW LEVEL SECURITY (RLS)
-- ============================================================================

create or replace function public.get_auth_business_id()
returns uuid
language sql stable security definer
set search_path = public
as $$
  select business_id from public.profiles where id = auth.uid();
$$;

create or replace function public.get_auth_role()
returns text
language sql stable security definer
set search_path = public
as $$
  select role from public.profiles where id = auth.uid();
$$;

-- Automatically create profile row when user registers via Supabase Auth
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email, name, role)
  values (
    new.id,
    coalesce(new.email, ''),
    coalesce(new.raw_user_meta_data->>'name', split_part(coalesce(new.email, 'User'), '@', 1)),
    coalesce(new.raw_user_meta_data->>'role', 'DRIVER')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ============================================================================
-- ENABLE ROW LEVEL SECURITY
-- ============================================================================

alter table public.businesses enable row level security;
alter table public.profiles enable row level security;
alter table public.vehicles enable row level security;
alter table public.drivers enable row level security;
alter table public.bookings enable row level security;
alter table public.expenses enable row level security;
alter table public.driver_payments enable row level security;

-- ============================================================================
-- ROW LEVEL SECURITY POLICIES
-- ============================================================================

-- BUSINESSES
drop policy if exists "Users can view their business or join" on public.businesses;
create policy "Users can view their business or join"
on public.businesses for select
using (auth.uid() is not null);

drop policy if exists "Authenticated users can insert business" on public.businesses;
create policy "Authenticated users can insert business"
on public.businesses for insert
with check (auth.uid() is not null);

drop policy if exists "Admins can update their business" on public.businesses;
create policy "Admins can update their business"
on public.businesses for update
using (id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN')
with check (id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

-- PROFILES
drop policy if exists "Users can view own profile and business members" on public.profiles;
create policy "Users can view own profile and business members"
on public.profiles for select
using (id = auth.uid() or (business_id is not null and business_id = public.get_auth_business_id()));

drop policy if exists "Users can insert own profile" on public.profiles;
create policy "Users can insert own profile"
on public.profiles for insert
with check (id = auth.uid());

drop policy if exists "Users can update own profile or Admin update business members" on public.profiles;
create policy "Users can update own profile or Admin update business members"
on public.profiles for update
using (id = auth.uid() or (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN'))
with check (id = auth.uid() or (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN'));

-- VEHICLES
drop policy if exists "View vehicles in business" on public.vehicles;
create policy "View vehicles in business"
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

drop policy if exists "Admins and Managers can delete vehicles" on public.vehicles;
create policy "Admins and Managers can delete vehicles"
on public.vehicles for delete
using (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

-- DRIVERS
drop policy if exists "View drivers in business" on public.drivers;
create policy "View drivers in business"
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

drop policy if exists "Admins and Managers can delete drivers" on public.drivers;
create policy "Admins and Managers can delete drivers"
on public.drivers for delete
using (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

-- BOOKINGS
drop policy if exists "View bookings in business" on public.bookings;
create policy "View bookings in business"
on public.bookings for select
using (business_id = public.get_auth_business_id());

drop policy if exists "Admins and Managers can insert bookings" on public.bookings;
create policy "Admins and Managers can insert bookings"
on public.bookings for insert
with check (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

drop policy if exists "Admins and Managers can update bookings" on public.bookings;
create policy "Admins and Managers can update bookings"
on public.bookings for update
using (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'))
with check (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

drop policy if exists "Admins and Managers can delete bookings" on public.bookings;
create policy "Admins and Managers can delete bookings"
on public.bookings for delete
using (business_id = public.get_auth_business_id() and public.get_auth_role() in ('ADMIN', 'MANAGER'));

-- EXPENSES
drop policy if exists "View expenses in business" on public.expenses;
create policy "View expenses in business"
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

-- DRIVER PAYMENTS
drop policy if exists "View driver payments in business" on public.driver_payments;
create policy "View driver payments in business"
on public.driver_payments for select
using (business_id = public.get_auth_business_id() and public.get_auth_role() = 'ADMIN');

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

-- ============================================================================
-- SUPABASE REALTIME CONFIGURATION (Idempotent)
-- ============================================================================

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
