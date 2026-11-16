create table accounts (
    id         uuid primary key,
    user_id    varchar(100)   not null unique,
    cash       numeric(19, 4) not null check (cash >= 0),
    currency   varchar(3)     not null default 'INR',
    created_at timestamptz    not null default now()
);

create table positions (
    id         uuid primary key,
    account_id uuid           not null references accounts (id),
    symbol     varchar(32)    not null,
    quantity   bigint         not null check (quantity > 0),
    avg_cost   numeric(19, 4) not null,
    unique (account_id, symbol)
);

create table orders (
    id              uuid primary key,
    account_id      uuid           not null references accounts (id),
    symbol          varchar(32)    not null,
    side            varchar(8)     not null,
    quantity        bigint         not null check (quantity > 0),
    status          varchar(24)    not null,
    source          varchar(16)    not null,
    idempotency_key varchar(100)   not null,
    reference_price numeric(19, 4) not null,
    fill_price      numeric(19, 4),
    rationale       text,
    created_at      timestamptz    not null,
    approved_at     timestamptz,
    approved_by     varchar(100),
    filled_at       timestamptz,
    unique (account_id, idempotency_key)
);
create index orders_account_status_idx on orders (account_id, status);
create index orders_account_created_idx on orders (account_id, created_at);

-- Append-only: proposal, approval, fill, rejection, cancellation, expiry.
create table audit_events (
    id         bigserial primary key,
    account_id uuid         not null references accounts (id),
    order_id   uuid,
    event_type varchar(40)  not null,
    actor      varchar(100) not null,
    detail     text,
    created_at timestamptz  not null
);
create index audit_account_idx on audit_events (account_id, id);
