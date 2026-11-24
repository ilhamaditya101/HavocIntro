# HavocIntro 1.0.0

Plugin source project for **Paper 1.21.1**. It plays a global cinematic showcase for all players online, creates Citizens player NPCs using each online player's skin, then applies a short darkness/blindness transition and sends each player to an independently selected safe random location in a configured world.

## Required server plugins
- Paper 1.21.1
- Citizens compatible with Paper 1.21.1 (required dependency; provides player-skin NPC clones)

## Build
Requires Java 21 and Maven:

```bash
mvn clean package
```

The plugin jar will be in `target/HavocIntro-1.0.0.jar`.

## Install
1. Install a compatible Citizens build into `plugins/`.
2. Put `HavocIntro-1.0.0.jar` into `plugins/`.
3. Start the server once, then edit `plugins/HavocIntro/config.yml`.
4. Set `intro.world` and `random-teleport.world` to existing worlds, and configure the stage center and RTP radius.
5. Restart the server.

## Commands
- `/intro start` — start the intro for everyone online.
- `/intro stop` — stop the active intro and restore player modes/locations.
- `/intro reload` — reload config.
- `/intro auto on|off` — toggle first-join auto intro (saved to config).

Permission: `havocintro.admin` (default OP).

## Notes / limitations
- The stage is built in an already existing world at `intro.center`; this plugin does not generate a map or platform. Build the showcase platform/arena there first.
- The cinematic uses a spectator camera entity and Citizens NPCs. Camera movement is a smooth orbit/approach approximation of the supplied reference, not a frame-perfect recreation of the edited video.
- Minecraft does not expose a universal server-side full-screen black fade API. The ending uses Darkness + Blindness effects during the teleport transition.
- For safety, random teleport checks the ground and two blocks of headroom and retries up to `max-attempts`.
- Auto intro triggers the global intro when a first-time player joins and the delay expires, provided no intro is already running.
