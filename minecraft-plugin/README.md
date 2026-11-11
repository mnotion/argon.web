# Argon Applications

Paper plugin for the Argon Minecraft server. It uses the server's existing world and spawn; it does not create, load, or teleport players between worlds.

## Player flow
- Players who are **not on the Argon plugin whitelist** join in Adventure mode at the server's normal join location.
- They can move around freely, but cannot interact with blocks or entities, open/use inventories, drop or pick up items, attack, or take damage.
- Players who have not submitted their real first name see a repeating prompt to use `/send <real first name>`. Submitting it records the application; chat and movement remain available.
- Players remain in the same world while staff review their application.
- When staff approve a player with `/app approve <player>`, restrictions are lifted immediately if they are online. Their previously saved inventory and game mode are restored without teleporting them, and they see a large green `enjoy` title.
- Approved players join normally on future connections. Operators and players with the bypass permission are not restricted.

## Staff commands
- `/app list`
- `/app approve <player>` (also accepts `/app whitelist <player>`)
- `/app remove <player>` (revokes Argon approval and returns an online player to the application state)
- `/app reject <player>`
- `/app decline <player> <reason>` (displays a large red `REJECTED` title with the reason underneath and remembers the decision)
- `/app decline <player> <reason>` (shows the player a large red `REJECTED` title with the reason beneath it; the decision is saved for future joins)

## Player commands
- `/afk` toggles AFK protection. Mobs and environmental damage are ignored and hunger is paused. AFK players cannot hit or be hit by players, including with projectiles. A player who has dealt or received PvP damage must wait 60 seconds after the last hit before entering AFK mode. Moving 2 blocks automatically exits AFK mode.
- `/colour`, `/color`, `/colours`, or `/colors` lists 94 chat-name colours.
- `/colour <colour>` changes only the colour of the player's name in chat. The colour is randomly assigned on first join and saved. The palette includes standard Minecraft colours and 78 additional RGB shades. Exact RGB shades are intended for Java; Bedrock clients using Geyser may display the nearest supported legacy shade.

Unapproved players see a large `READ CHAT!` title when they join, followed by spaced instructions in chat. The reminder uses the action bar.

## Application email notifications
When a pending player submits `/send <real first name>`, the plugin sends their submitted first name, Minecraft username, UUID, Java/Bedrock edition, submission time, server name and server version to the Supabase Edge Function. The function stores the application in the private `public.minecraft_applications` table and emails **argonmcadmin@gmail.com** through Resend. IP addresses are not collected.

Configure the following Supabase Edge Function secrets:
- `ARGON_APPLICATION_WEBHOOK_SECRET`: a private shared token
- `RESEND_API_KEY`: an API key from Resend
- `ARGON_EMAIL_FROM`: a sender address on a domain verified with Resend

Set the same `ARGON_APPLICATION_WEBHOOK_SECRET` as `application-notification.secret` in `plugins/ArgonApplications/config.yml`. The endpoint is prefilled by default. Never commit the shared token or Resend API key to GitHub.

The email function is protected by the shared token and has JWT verification disabled because it is called by the Minecraft server, not a signed-in Supabase user.

## Server setup
- Keep the vanilla Minecraft server whitelist **disabled** if unapproved players should be able to join. The plugin maintains its own Argon approval list; the vanilla whitelist blocks players before this plugin can handle them.
- No separate application world, Multiverse setup, or parkour world is required.

## Build and install
Requires Java 21 and Maven. Run `mvn package` in this directory and upload the generated JAR from `target/` to the server's `plugins/` folder, replacing the old ArgonApplications JAR, then restart.

## Data and limitations
Applications are stored locally in `plugins/ArgonApplications/applications.yml` and, when the notification endpoint is configured, in Supabase. First names are self-reported and are not verified. IP addresses are deliberately not stored.
