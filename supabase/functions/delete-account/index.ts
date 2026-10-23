// Deploy with: supabase functions deploy delete-account
// Configure SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY as function secrets.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return new Response("Unauthorized", { status: 401 });

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return new Response("Deletion service is not configured", { status: 503 });

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const token = authorization.slice("Bearer ".length);
  const { data: { user }, error: authError } = await admin.auth.getUser(token);
  if (authError || !user) return new Response("Unauthorized", { status: 401 });

  const { error } = await admin.auth.admin.deleteUser(user.id);
  if (error) return new Response(error.message, { status: 500 });
  return Response.json({ deleted: true });
});
