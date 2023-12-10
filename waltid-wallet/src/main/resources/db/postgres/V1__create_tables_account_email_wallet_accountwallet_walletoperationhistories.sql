-- ----------------------------------
-- Emails table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "emails"
(
    "id" UUID NOT NULL,
    "email" TEXT COLLATE pg_catalog."default" NOT NULL,
    "password" TEXT COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT "emails_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "email" UNIQUE ("email")
);
-- ----------------------------------
-- Wallets table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "wallets"
(
    "id" UUID NOT NULL,
    "address" TEXT COLLATE pg_catalog."default" NOT NULL,
    "ecosystem" TEXT COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT "wallets_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "address" UNIQUE ("address")
);
-- ----------------------------------
-- Accounts table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "accounts"
(
    "id" UUID NOT NULL,
    "email" UUID NULL,
    "wallet" UUID NULL,
    CONSTRAINT "accounts_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "accounts_email_wallet_unique" UNIQUE ("email", "wallet")
        INCLUDE("email", "wallet"),
    CONSTRAINT "account_email_fk" FOREIGN KEY ("email")
        REFERENCES "emails" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT "account_wallet_fk" FOREIGN KEY ("wallet")
        REFERENCES "wallets" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);
-- ----------------------------------
-- AccountWallets table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "account_wallets"
(
    "id" UUID NOT NULL,
    "account" UUID NOT NULL,
    "wallet" UUID NOT NULL,
    CONSTRAINT "account_wallets_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "account_wallets_account_fk" FOREIGN KEY ("account")
        REFERENCES "accounts" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT "account_wallets_wallet_fk" FOREIGN KEY ("wallet")
        REFERENCES "wallets" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);
-- ----------------------------------
-- WalletOperationHistories table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "wallet_operation_histories"
(
    "id" UUID NOT NULL,
    "account" UUID NOT NULL,
    "timestamp" TEXT COLLATE pg_catalog."default" NOT NULL,
    "operation" TEXT COLLATE pg_catalog."default" NOT NULL,
    "data" TEXT COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT "wallet_operation_histories_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "wallet_operation_histories_account_fk" FOREIGN KEY ("account")
        REFERENCES "accounts" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);