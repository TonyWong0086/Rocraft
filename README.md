# Rocraft

**2018-era Roblox inside Minecraft 26.2 (Fabric).** Your real Roblox avatar, Roblox's R6 animations, the classic StarterPack gear, the Roblox camera and mouse, the 2018 top bar, chat and settings menu, and Roblox movement physics, all inside a normal Minecraft world.

> **This mod is vibecoded.** It was built almost entirely by AI (Claude Code) from prompts, with the user testing in game. Expect rough edges.
>
> Inspired by [@coah80](https://x.com/coah80) on X.

---

## Features

### Avatar
- Your **real Roblox avatar** is built from Roblox's own R6 meshes, not a Minecraft skin with blocks.
- **Body colors, shirt and pants** come from your Roblox profile.
- **Accessories** (hats, hair and so on) and your **face** are loaded from Roblox, not the default smile. Dynamic heads are supported too.
- **Roblox R6 animations** (idle, walk, jump, fall, climb, tool hold, sword slash and lunge) are read from Roblox's own animation files and blended the same way Roblox does.
- **No API key?** You play as a **Guest**, like old Roblox.

### Classic gear
All gear has its own **Rocraft creative-inventory tab**. The StarterPack can also be handed out automatically.

| Gear | What it does |
|---|---|
| Linked Sword | Click to slash, double-click to lunge |
| Rocket Launcher | Fires a rocket toward the cursor; it explodes Roblox-style |
| Superball | A bouncy ball thrown at the cursor |
| Slingshot | Fires pellets |
| Bomb | The classic ticking bomb that speeds up, then explodes |
| Trowel | Builds a wall where you click |
| Gravity Coil / Speed Coil | Lower gravity / higher walk speed while held |
| Bloxy Cola, Taco, Burger, Chicken, Pizza | Drink and eat, with the Roblox poses and sounds |
| Teddy | Says its classic lines |

- Gear models, textures and icons come from Roblox, never redrawn.
- Gear numbers (speeds, cooldowns, blast radius) follow the original tool scripts.
- Explosions are Roblox-style, with Roblox's fireball, smoke and spark particles and no Minecraft explosion effect.

### Camera and controls (Roblox-style)
- **Free cursor**, shown as Roblox's arrow.
- **Right-drag** rotates the camera; the **scroll wheel** zooms (0.5 to 400 studs).
- **Left click** uses the held gear. **Right click never triggers gear**, as in Roblox.
- **First person:** zoom all the way in and the mouse switches to mouse-look automatically.
- **No camera shake** while walking.
- The camera stops at walls instead of going through them.

### Movement
- Roblox **walk speed, jump height and gravity**, converted from studs to Minecraft blocks.
- Optional fall damage and hunger, both off by default like Roblox.
- The **"oof"** sound when you die, and no Minecraft hurt sounds, sprint dust or death smoke.

### 2018 interface
- **Top bar:** menu, chat and backpack buttons. Your name sits above "Account: 13+"; the health bar takes that spot when you're hurt.
- **Leaderstats** (Level, KOs, Wipeouts) appear in the top bar, each with its heading above its value.
- A **Backpack-style hotbar** and no player list.
- **2018 chat:**
  - `[Name]: message` in SourceSans;
  - each name is colored by Roblox's own name-color algorithm;
  - messages fade after 30 seconds;
  - the classic hint `To chat click here or press "/" key`.
- **Settings menu:** open it with the **Rocraft** button on the title screen. It looks like the 2018 Roblox settings menu and has **Avatar**, **Gameplay** and **Graphics** tabs.

### Sounds and particles
- Classic Roblox sounds: slash, lunge, unsheath, oof, coils, cola, the bomb tick and explosion, the rocket whoosh and boom, the superball boing, the slingshot and the trowel.
- Roblox particle textures for smoke, fire, sparks and explosions, colored and faded the way Roblox does it.

---

## Where the Roblox assets come from
**Nothing from Roblox is in this repo or the mod jar.** At runtime Rocraft gets everything from three places:

1. **Your local Roblox install** (`%LOCALAPPDATA%\Roblox\Versions\...\content`): the R6 meshes, fonts, UI textures, particle textures and some sounds.
2. **The Roblox Open Cloud API**, using your own key: avatar accessories, faces, clothing, gear meshes and textures, and animations.
3. **An optional export from Roblox Studio** (`tools/export_classic_tools.luau` + `tools/receive_export.ps1`): classic tools that only exist as built-in `rbxasset://` content. It writes to `config/rocraft/legacy`.

Everything is saved privately in your Minecraft folder: the **"Rocraft Roblox Assets"** resource pack, `config/rocraft/cache` and `config/rocraft/legacy`.

Rocraft **never launches or hooks the Roblox client**. It only reads files and calls the web APIs.

---

## Requirements
- Minecraft **26.2**
- Fabric Loader **0.19+** and **Fabric API**
- **Java 25**
- **Roblox installed** on the same PC (Player or Studio)
- *(Optional)* a **Roblox Open Cloud API key** to load your own avatar

### Getting an API key
1. Go to <https://create.roblox.com/dashboard/credentials> and create an API key.
2. Give it these permissions:
   - `legacy-assets:manage` (asset delivery);
   - `users:read`.
3. In Minecraft, click **Rocraft** on the title screen, open the **Avatar** tab, and enter your Roblox username and the key.

The key is stored **only** in `config/rocraft.json` on your PC and is never logged. **Don't share it.** If it leaks, regenerate it.

---

## Install
1. Install Fabric Loader for 26.2. Any launcher works; Modrinth App is tested.
2. Put **Fabric API** and the **Rocraft jar** in the `mods` folder.
3. Launch the game. Rocraft builds and turns on the "Rocraft Roblox Assets" pack the first time it runs.

## Build from source
```bash
./gradlew build
```
The jar lands in `build/libs/rocraft-1.0.0.jar`.

Extra Gradle tasks for checking things without starting Minecraft:
- `./gradlew bench` checks the movement physics against Roblox: jump height, the fit to Minecraft's physics, and the R6 rest pose.
- `./gradlew rbxDump` dumps the contents of a Roblox model file.

---

## Settings (`config/rocraft.json`)
| Setting | Default | Meaning |
|---|---|---|
| `username` | | Your Roblox username |
| `apiKey` | | Your Open Cloud key (private) |
| `robloxMovement` | on | Roblox speed, jump and gravity |
| `fallDamage` | off | Minecraft fall damage |
| `hunger` | off | Minecraft hunger |
| `starterPack` | on | Gives you the classic StarterPack |
| `hud2018` | on | The 2018 top bar, hotbar and chat |
| `robloxFont` | on | SourceSans text |
| `robloxCamera` | on | Roblox camera and mouse |

All of these can be changed in the in-game **Settings** menu.

---

## Known issues
- Command suggestions (the popup when you type `/`) don't show while the 2018 chat is on.
- Particle sizes and lifetimes are estimates.
- Some gear needs the Studio export (see above) to show its real model.

## Credits
- **Inspired by [@coah80](https://x.com/coah80) on X.**
- **Vibecoded** with Claude Code (Anthropic).
- Roblox, its assets and the classic gear belong to **Roblox Corporation**. This is an unofficial fan project and is not affiliated with or endorsed by Roblox or Mojang.

## License
See [LICENSE](LICENSE). It covers the code only, not any Roblox assets.
