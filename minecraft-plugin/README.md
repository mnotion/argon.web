# Argon Applications

Paper plugin for the Argon Minecraft application lobby.

## Player flow
- Players who are **not on the Argon plugin whitelist** are sent to `application_lobby` every time they join.
- If they have not submitted a name, they spawn on the lobby's barrier spawn tile and are frozen in place.
- A large on-screen title repeats the instruction to enter `/send <real first name>`.
- A valid `/send` submission unlocks movement and lets the player parkour in Adventure mode while waiting for approval.
- Players who have already submitted a name return to the lobby with parkour unlocked while their application is pending.
- Approved players are sent directly to the `jawaaa` survival spawn on join.
- On approval, the plugin restores the inventory and game mode saved when the player entered the application lobby.

## Staff commands
- `/app list`
- `/app approve <player>` (also accepts `/app whitelist <player>`)
- `/app reject <player>`

## Required server setup
- Have a **void world** loaded with the exact name `application_lobby`.
- Set that world's spawn where the player should wait. The plugin places a barrier block beneath the spawn point if the block is air.
- Have the survival world loaded with the exact name `jawaaa`.
- Keep the vanilla Minecraft server whitelist **disabled** if unapproved players must be able to join the lobby. The plugin maintains its own Argon approval list. Enabling the vanilla server whitelist blocks unapproved players before this plugin can send them to the lobby.

## Build and install
Requires Java 21 and Maven. Run `mvn package` in this directory and upload `target/argon-applications-0.1.0.jar` to the server's `plugins/` folder, then restart.

## Data and limitations
Applications are stored locally in `plugins/ArgonApplications/applications.yml`. This version does not yet sync with ArgonWeb or automatically update the vanilla Minecraft whitelist. First names are self-reported and are not verified.
