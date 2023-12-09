-- ----------------------------------
-- Insert issuers table
-- ----------------------------------
INSERT INTO public."issuers" ("id", "name", "description", "ui", "configuration") 
VALUES ('6B638061-E4C6-4636-B4E4-F4BE2FCA582C'::UUID, 'walt.id', 'walt.id issuer portal', 'https://portal.walt.id/credentials?ids=', 'https://issuer.portal.walt.id/.well-known/openid-credential-issuer');
-- ----------------------------------
-- Insert account-issuers table
-- ----------------------------------
INSERT INTO public."account_issuers" ("id", "account", "issuer") 
VALUES ('3FAD4023-9E97-4DD0-8B42-9471517757EF'::UUID, 'C59A7223-BF89-A04A-97B2-7C4F121F83B1', '6B638061-E4C6-4636-B4E4-F4BE2FCA582C');