import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const ADMIN_EMAIL = "argonmcadmin@gmail.com";
const MAX_BODY_BYTES = 4096;

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => {
    const entities: Record<string, string> = {
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    };
    return entities[character];
  });
}

async function secureEquals(left: string, right: string): Promise<boolean> {
  const encoder = new TextEncoder();
  const [leftHash, rightHash] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(left)),
    crypto.subtle.digest("SHA-256", encoder.encode(right)),
  ]);
  const a = new Uint8Array(leftHash);
  const b = new Uint8Array(rightHash);
  let difference = 0;
  for (let index = 0; index < a.length; index++) difference |= a[index] ^ b[index];
  return difference === 0;
}

function getServiceKey(): string | null {
  const secretKeysRaw = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (secretKeysRaw) {
    try {
      const keys = JSON.parse(secretKeysRaw);
      if (typeof keys.default === "string") return keys.default;
    } catch {
      // Fall through to the legacy injected environment variable.
    }
  }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? null;
}

function databaseHeaders(serviceKey: string, prefer?: string): Headers {
  const headers = new Headers({
    "Content-Type": "application/json",
    "apikey": serviceKey,
  });
  // New Supabase secret API keys are not JWTs and must only be sent as apikey.
  // Legacy service_role JWTs still use the Authorization header.
  if (!serviceKey.startsWith("sb_secret_")) {
    headers.set("Authorization", `Bearer ${serviceKey}`);
  }
  if (prefer) headers.set("Prefer", prefer);
  return headers;
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const expectedToken = Deno.env.get("ARGON_APPLICATION_WEBHOOK_SECRET");
  if (!expectedToken) return json({ error: "Application notification is not configured" }, 503);

  const suppliedToken = request.headers.get("x-argon-token") ?? "";
  if (!(await secureEquals(suppliedToken, expectedToken))) {
    return json({ error: "Unauthorized" }, 401);
  }

  const resendApiKey = Deno.env.get("RESEND_API_KEY");
  const sender = Deno.env.get("ARGON_EMAIL_FROM");
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = getServiceKey();
  if (!resendApiKey || !sender || !supabaseUrl || !serviceKey) {
    return json({ error: "Email or database service is not configured" }, 503);
  }

  const declaredLength = Number(request.headers.get("content-length") ?? "0");
  if (declaredLength > MAX_BODY_BYTES) return json({ error: "Request too large" }, 413);

  let input: Record<string, unknown>;
  try {
    input = await request.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  const firstName = typeof input.first_name === "string" ? input.first_name.trim() : "";
  const username = typeof input.minecraft_username === "string" ? input.minecraft_username.trim() : "";
  const playerUuid = typeof input.player_uuid === "string" ? input.player_uuid : "";
  const edition = typeof input.edition === "string" ? input.edition : "";
  const serverName = typeof input.server_name === "string" ? input.server_name.trim() : "";
  const serverVersion = typeof input.server_version === "string" ? input.server_version.trim() : "";
  const submittedAt = typeof input.submitted_at === "string" ? input.submitted_at : "";

  if (!/^[A-Za-z][A-Za-z' -]{0,29}$/.test(firstName) ||
      !/^[A-Za-z0-9_]{1,16}$/.test(username) ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(playerUuid) ||
      !["JAVA", "BEDROCK"].includes(edition) ||
      !serverName || serverName.length > 80 ||
      !serverVersion || serverVersion.length > 160 ||
      !Number.isFinite(Date.parse(submittedAt))) {
    return json({ error: "Application data failed validation" }, 400);
  }

  const record = {
    player_uuid: playerUuid,
    minecraft_username: username,
    first_name: firstName,
    edition,
    server_name: serverName,
    server_version: serverVersion,
    submitted_at: new Date(submittedAt).toISOString(),
  };

  const databaseResponse = await fetch(
    `${supabaseUrl}/rest/v1/minecraft_applications`,
    {
      method: "POST",
      headers: databaseHeaders(serviceKey, "return=representation"),
      body: JSON.stringify(record),
    },
  );

  if (!databaseResponse.ok) {
    const details = (await databaseResponse.text()).slice(0, 500);
    console.error("Application database insert failed:", details);
    return json({ error: "Could not save application" }, 500);
  }

  const insertedRows = await databaseResponse.json();
  const applicationId = insertedRows?.[0]?.id;
  if (typeof applicationId !== "string") {
    console.error("Application row was saved but no row ID was returned");
    return json({ error: "Application saved but notification could not be tracked" }, 500);
  }

  const submittedLabel = new Date(record.submitted_at).toLocaleString("en-GB", {
    timeZone: "UTC",
    dateStyle: "medium",
    timeStyle: "long",
  }) + " UTC";

  const html = `
    <h2>New Minecraft application</h2>
    <table cellpadding="6" cellspacing="0" border="1" style="border-collapse:collapse">
      <tr><th align="left">Real first name</th><td>${escapeHtml(firstName)}</td></tr>
      <tr><th align="left">Minecraft username</th><td>${escapeHtml(username)}</td></tr>
      <tr><th align="left">Minecraft UUID</th><td>${escapeHtml(playerUuid)}</td></tr>
      <tr><th align="left">Edition</th><td>${escapeHtml(edition)}</td></tr>
      <tr><th align="left">Submitted at</th><td>${escapeHtml(submittedLabel)}</td></tr>
      <tr><th align="left">Server</th><td>${escapeHtml(serverName)}</td></tr>
      <tr><th align="left">Server version</th><td>${escapeHtml(serverVersion)}</td></tr>
    </table>
    <p>Privacy note: no player IP address is collected or included in this application.</p>
    <p>Application record: ${escapeHtml(applicationId)}</p>
  `;

  const plainText = [
    "New Minecraft application",
    "",
    `Real first name: ${firstName}`,
    `Minecraft username: ${username}`,
    `Minecraft UUID: ${playerUuid}`,
    `Edition: ${edition}`,
    `Submitted at: ${submittedLabel}`,
    `Server: ${serverName}`,
    `Server version: ${serverVersion}`,
    "",
    "Privacy note: no player IP address is collected or included in this application.",
    `Application record: ${applicationId}`,
  ].join("\n");

  const emailResponse = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${resendApiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: sender,
      to: [ADMIN_EMAIL],
      subject: `Minecraft application — ${username}`,
      html,
      text: plainText,
    }),
  });

  const emailResult = await emailResponse.json().catch(() => ({}));
  if (!emailResponse.ok) {
    const message = typeof emailResult?.message === "string"
      ? emailResult.message.slice(0, 500)
      : `Resend returned HTTP ${emailResponse.status}`;
    const updateUrl = new URL(`${supabaseUrl}/rest/v1/minecraft_applications`);
    updateUrl.searchParams.set("id", `eq.${applicationId}`);
    await fetch(updateUrl, {
      method: "PATCH",
      headers: databaseHeaders(serviceKey),
      body: JSON.stringify({ notification_error: message }),
    });
    console.error("Application email failed:", message);
    return json({ error: "Application was stored, but the notification email failed" }, 502);
  }

  const updateUrl = new URL(`${supabaseUrl}/rest/v1/minecraft_applications`);
  updateUrl.searchParams.set("id", `eq.${applicationId}`);
  const updateResponse = await fetch(updateUrl, {
    method: "PATCH",
    headers: databaseHeaders(serviceKey),
    body: JSON.stringify({
      notification_sent_at: new Date().toISOString(),
      email_provider_id: typeof emailResult?.id === "string" ? emailResult.id : null,
      notification_error: null,
    }),
  });

  if (!updateResponse.ok) {
    console.error("Email sent, but application notification status could not be updated");
  }

  return json({ ok: true, email_sent: true, application_id: applicationId });
});
