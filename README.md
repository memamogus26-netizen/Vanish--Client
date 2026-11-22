# Vanish Client (Meteor addon, Minecraft 1.21.11)

Adds a **Vanish Client** category to Meteor with:

| Module | What it does |
|---|---|
| `human-aim-assist` | Aim assist with micro rotations (your original module). |
| `trails` | Ribbon trail behind players. Colors / Render / Style / Dimensions. "Only F5" (on by default) hides your own trail in first person. |
| `kill-effect` | Particles, Thunder or Blood when you kill something. Separate effect for friends' kills. |
| `target-esp` | Circle, Ghosts, Crystals or Lightning on your current target, with separate friend settings. |
| `hands-shader` | Shader list incl. Fire and Dual mode. **Settings only for now**, see below. |

## Build
JDK 21, then `./gradlew build` (jar in `build/libs`). Pushing to GitHub also builds it via the workflow.
If a version "does not exist", update `yarn`, `fabric-loader` and `loom` in `gradle/libs.versions.toml`.

## Known limits
- **Hands Shader does not draw yet.** Drawing a shader on the first-person hand needs a mixin / shader hook written against the exact 1.21.11 mappings. The module holds the modes, Dual mode and Fire colour logic, ready for that hook.
- Kill Effect: the client can't see who dealt damage. Your kills = something you were hitting that died. Friend kills = a friend within 6 blocks when it died.
- Target ESP "target" = the entity you last hit (attack key + crosshair), kept for `follow-time` seconds.
- Ghost "texture" is approximated with glow layers (Soft / Bloom / Smoke), not real textures.
- Not compiled by the author. If the build fails, send the errors.
