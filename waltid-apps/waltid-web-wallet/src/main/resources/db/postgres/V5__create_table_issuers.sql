-- ----------------------------------
-- Issuers table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "issuers"
(
    "id" UUID NOT NULL,
    "name" TEXT COLLATE pg_catalog."default" NOT NULL,
    "description" TEXT COLLATE pg_catalog."default" NOT NULL,
    "ui" TEXT COLLATE pg_catalog."default" NOT NULL,
    "configuration" TEXT COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT "issuers_pkey" PRIMARY KEY ("id")
);
-- ----------------------------------
-- AccountIssuers table
-- ----------------------------------
CREATE TABLE IF NOT EXISTS "account_issuers"
(
    "id" UUID NOT NULL,
    "account" UUID NOT NULL,
    "issuer" UUID NOT NULL,
    CONSTRAINT "account_issuers_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "account_issuers_account_fk" FOREIGN KEY ("account")
        REFERENCES "accounts" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT "account_issuers_issuer_fk" FOREIGN KEY (issuer)
        REFERENCES "issuers" ("id") MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);
-- ----------------------------------
-- AccountIssuers unique index
-- ----------------------------------
CREATE UNIQUE INDEX "account_issuers_account_issuer" ON "account_issuers"("account", "issuer");