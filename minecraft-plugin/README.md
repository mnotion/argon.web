# Argon Applications

Paper plugin for the Argon Minecraft server. It uses the server's existing world and spawn; it does not create, load, or teleport players between worlds.

## Player flow
- Players who are **not on the Argon plugin whitelist** join in Adventure mode at the server's normal join location.
- They can move around freely, but cannot interact with blocks or entities, open/use inventories, drop or pick up items, attack, or take damage.
- Players who have not submitted their real first name see a repeating prompt to use `/send <real first name>`. Submitting it records the application; chat and movement remain available.
- Players remain in the same world while staff review their application.
- When staff approve a player with `/app approve <player>`, restrictions are lifted immediately if they are online. Their previously saved inventory and game mode are restored without teleporting them.
- Approved players join normally on future connections. Operators and players with the bypass permission are not restricted.

## Staff commands
- `/app list`
- `/app approve <player>` (also accepts `/app whitelist <player>`)
- `/app remove <player>` (revokes Argon approval and returns an online player to the application state)
- `/app reject <player>`

## Player commands
- `/afk` toggles AFK protection. Mobs and environmental damage are ignored and hunger is paused. Player attacks still work, but cannot reduce an AFK player's health below 1 HP.
- `/colour` lists all shared Java/Bedrock chat-name colours.
- `/colour <colour>` changes only the colour of the player's name in chat. The colour is randomly assigned on first join and saved.

Available colours: black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, dark_gray, blue, green, aqua, red, light_purple, yellow, white.

Unapproved players see a large `READ CHAT!` title when they join, followed by the approval instructions in regular chat-sized text. The reminder uses the action bar rather than a large repeating title.

## Application email notifications
When a pending player submits `/send <real first name>`, the plugin sends their submitted first name, Minecraft username, UUID, Java/Bedrock edition, submission time, server name and server version to the Supabase Edge Function. The function stores the application in the private `public.minecraft_applications` table and emails **argonmcadmin@gmail.com** through Resend. IP addresses are not collected.

Configure the following Supabase Edge Function secrets:
- `ARGON_APPLICATION_WEBHOOK_SECRET`: a private shared token
- `RESEND_API_KEY`: an API key from Resend
- `ARGON_EMAIL_FROM`: a sender address on a domain verified with Resend

Set the same `ARGON_APPLICATION_WEBHOOK_SECRET` as `application-notification.secret` in `plugins/ArgonApplications/config.yml`. The endpoint is prefilled by default. Never commit the shared token or Resend API key to GitHub.

Players are shown a privacy notice before submitting. The email function is protected by the shared token and has JWT verification disabled because it is called by the Minecraft server, not a signed-in Supabase user.

## Server setup
- Keep the vanilla Minecraft server whitelist **disabled** if unapproved players should be able to join. The plugin maintains its own Argon approval list; the vanilla whitelist blocks players before this plugin can handle them.
- No separate application world, Multiverse setup, or parkour world is required.

## Build and install
Requires Java 21 and Maven. Run `mvn package` in this directory and upload the generated JAR from `target/` to the server's `plugins/` folder, replacing the old ArgonApplications JAR, then restart.

## Data and limitations
Applications are stored locally in `plugins/ArgonApplications/applications.yml` and, when the notification endpoint is configured, in Supabase. First names are self-reported and are not verified. IP addresses are deliberately not stored.
