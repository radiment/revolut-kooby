create sequence account_seq;
create sequence transition_seq;

create table account
(
  ID          BIGINT default account_seq.nextval primary key,
  user_id     UUID    not null,
  currency_id int     not null,
  amount      DECIMAL not null,
  constraint uc_user_currency unique (user_id, currency_id)
);

create table transition
(
  ID             BIGINT default transition_seq.nextval primary key,
  account_id     BIGINT  not null,
  transaction_id UUID not null,
  amount         DECIMAL not null,
  type           VARCHAR not null
);
