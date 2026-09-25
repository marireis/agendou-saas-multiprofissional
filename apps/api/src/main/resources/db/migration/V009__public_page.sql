ALTER TABLE public_profiles ADD COLUMN published BOOLEAN NOT NULL DEFAULT false;

-- Narrow anonymous projection: no private payment settings, user identity or tenant ID.
CREATE FUNCTION public.read_public_page(requested_slug text) RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, public AS $$
 SELECT jsonb_build_object(
  'name',t.display_name,'slug',t.slug,'description',p.description,'timezone',p.timezone,
  'contactEmail',p.contact_email,'contactPhone',p.contact_phone,'serviceMode',p.service_mode,
  'location',p.location,'hasLogo',p.logo_png IS NOT NULL,'bookingAvailable',false,
  'services',coalesce((SELECT jsonb_agg(jsonb_build_object('id',s.id,'name',s.name,
   'description',s.description,'durationMinutes',s.duration_minutes,'priceCents',s.price_cents,
   'depositCents',(s.price_cents*s.deposit_percent+99)/100) ORDER BY s.created_at,s.id)
   FROM public.services s WHERE s.tenant_id=t.id AND s.active),'[]'::jsonb))
 FROM public.tenants t JOIN public.public_profiles p ON p.tenant_id=t.id
 WHERE t.slug=requested_slug AND p.published
$$;
CREATE FUNCTION public.read_public_logo(requested_slug text) RETURNS bytea
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, public AS $$
 SELECT p.logo_png FROM public.tenants t JOIN public.public_profiles p ON p.tenant_id=t.id
 WHERE t.slug=requested_slug AND p.published
$$;
REVOKE ALL ON FUNCTION public.read_public_page(text),public.read_public_logo(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.read_public_page(text),public.read_public_logo(text) TO agendou_runtime;
