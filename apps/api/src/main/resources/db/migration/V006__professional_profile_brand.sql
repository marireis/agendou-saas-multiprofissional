ALTER TABLE public_profiles
 ADD COLUMN contact_email TEXT NOT NULL DEFAULT '',
 ADD COLUMN contact_phone TEXT NOT NULL DEFAULT '',
 ADD COLUMN service_mode TEXT NOT NULL DEFAULT 'UNSET' CHECK(service_mode IN ('UNSET','IN_PERSON','ONLINE','HYBRID')),
 ADD COLUMN location TEXT NOT NULL DEFAULT '',
 ADD COLUMN logo_png BYTEA,
 ADD COLUMN logo_version UUID,
 ADD CONSTRAINT logo_size CHECK(logo_png IS NULL OR octet_length(logo_png)<=2097152),
 ADD CONSTRAINT logo_version_pair CHECK((logo_png IS NULL)=(logo_version IS NULL));
-- Existing RLS and grants cover these fields; originals and filenames are never retained.
