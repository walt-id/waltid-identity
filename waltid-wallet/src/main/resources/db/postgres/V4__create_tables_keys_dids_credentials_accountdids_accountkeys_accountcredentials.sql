-- ----------------------------------
-- Keys table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "keys"
(
    "id" UUID NOT NULL,
    "kid" TEXT COLLATE pg_catalog."default" NOT NULL,
    "document" TEXT COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT "keys_pkey" PRIMARY KEY ("id")
);
-- ----------------------------------
-- Dids table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "dids"
(
    "id" UUID NOT NULL,
    "did" TEXT COLLATE pg_catalog."default" NOT NULL,
    "document" TEXT COLLATE pg_catalog."default" NOT NULL,
    "key" UUID NOT NULL,
    CONSTRAINT "dids_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "did_key_fk" FOREIGN KEY ("key")
        REFERENCES "keys" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);
-- ----------------------------------
-- Credentials table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "credentials"
(
    "id" UUID NOT NULL,
    "cid" TEXT COLLATE pg_catalog."default" NOT NULL,
    "document" TEXT COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT "credentials_pkey" PRIMARY KEY ("id")
);
-- ----------------------------------
-- AccountKeys table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "account_keys"
(
    "id" UUID NOT NULL,
    "account" UUID NOT NULL,
    "key" UUID NOT NULL,
    CONSTRAINT "account_keys_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "account_keys_account_fk" FOREIGN KEY ("account")
        REFERENCES "accounts" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT "account_keys_key_fk" FOREIGN KEY (key)
        REFERENCES "keys" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);
-- ----------------------------------
-- AccountDids table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "account_dids"
(
    "id" UUID NOT NULL,
    "account" UUID NOT NULL,
    "did" UUID NOT NULL,
    "alias" TEXT COLLATE pg_catalog."default" NOT NULL,
    "default" BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT "account_dids_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "account_dids_account_fk" FOREIGN KEY ("account")
        REFERENCES "accounts" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT "account_dids_did_fk" FOREIGN KEY ("did")
        REFERENCES "dids" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);
-- ----------------------------------
-- AccountCredentials table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "account_credentials"
(
    "id" UUID NOT NULL,
    "account" UUID NOT NULL,
    "credential" UUID NOT NULL,
    CONSTRAINT "account_credentials_pkey" PRIMARY KEY (id),
    CONSTRAINT "account_credentials_account_fk" FOREIGN KEY ("account")
        REFERENCES "accounts" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT "account_credentials_credential_fk" FOREIGN KEY ("credential")
        REFERENCES "credentials" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);
-- ----------------------------------
-- Keys index
-- ----------------------------------
CREATE UNIQUE INDEX "keys_kid" ON "keys"("kid");
-- ----------------------------------
-- Dids index
-- ----------------------------------
CREATE UNIQUE INDEX "dids_did" ON "dids"("did");
-- ----------------------------------
-- Credentials index
-- ----------------------------------
CREATE UNIQUE INDEX "credentials_cid" ON "credentials"("cid");