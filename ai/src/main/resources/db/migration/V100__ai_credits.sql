-- Kept apart from the trading ledger on purpose: nothing in the core reads or writes this table.
create table ai_credits (
    user_id    varchar(100) primary key,
    balance    integer     not null check (balance >= 0),
    updated_at timestamptz not null default now()
);
