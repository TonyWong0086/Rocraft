# Rocraft

**2018-era Roblox inside Minecraft 26.2 (Fabric).** Your real Roblox avatar, Roblox's R6 animations, the classic StarterPack gear, the Roblox camera and mouse, the 2018 top bar, chat and settings menu, and Roblox movement physics, all inside a normal Minecraft world.

> **This mod is vibecoded.** It was built almost entirely by AI (Claude Code) from prompts, with the user testing in game. Expect rough edges.
>
> Inspired by [@coah80](https://x.com/coah80) on X.

> [!IMPORTANT]
> **You need two things before Rocraft will look like Roblox.** None of Roblox's assets ship with this mod; they're loaded from your PC and your account:
> 1. **Roblox installed** on the same PC ([roblox.com/download](https://www.roblox.com/download)). Rocraft reads the R6 body, fonts, UI, particle textures and sounds from it. Without it, those fall back to plain Minecraft visuals.
> 2. **A Roblox Open Cloud API key** (free; see [Getting an API key](#getting-an-api-key)). It downloads your avatar's clothes and accessories, the gear models, animations and sounds. Without it you only get the Guest's body colors, no animations and no gear models.

---

## Features

### Avatar
- Your **real Roblox avatar** is built from Roblox's own R6 meshes, not a Minecraft skin with blocks.
- **Body colors, shirt and pants** come from your Roblox profile.
- **Accessories** (hats, hair and so on) and your **face** are loaded from Roblox, not the default smile. Dynamic heads are supported too.
- **Roblox R6 animations** (idle, walk, jump, fall, climb, tool hold, sword slash and lunge) are read from Roblox's own animation files and blended the same way Roblox does.
- **No API key?** You play as the **2017 Guest** (the black R cap, ROBLOX Jacket, Black Jeans).
- **Chat emotes:** `/e dance`, `/e wave`, `/e laugh`, `/e cheer`, `/e point`.
- **Flying** (creative) works like HD Admin / Adonis fly: no animation, and the body faces wherever the camera looks.
- **Your name floats above your head**, Roblox-style, with a health bar once you're hurt.

### Classic gear
All gear has its own **Rocraft creative-inventory tab**. The StarterPack can also be handed out automatically.

| Gear | What it does |
|---|---|
| Linked Sword | Click to slash, double-click to lunge |
| Rocket Launcher | Fires a rocket toward the cursor; it explodes Roblox-style |
| Superball | A bouncy ball thrown at the cursor |
| Slingshot | Fires pellets |
| Bomb | The classic ticking bomb that speeds up, then explodes |
| Trowel | Builds a wall of real Roblox Parts (studded 4×1.2×2 bricks) where you click |
| Gravity Coil / Speed Coil | Lower gravity / higher walk speed while held |
| Bloxy Cola, Taco, Burger, Chicken, Pizza | Drink and eat, with the Roblox poses and sounds |
| Teddy | Says its classic lines |

- Gear models, textures and icons come from Roblox, never redrawn.
- Gear numbers (speeds, blast radius) follow the original tool scripts. Gear has no cooldown, except the sword lunge.
- Using gear doesn't swing your arm; only the sword plays its slash and lunge.
- Explosions are Roblox-style: flash, shockwave, fireball, sparks and smoke, with no Minecraft explosion effect.
- **Bomb and Rocket Launcher blasts tear blocks loose.** They fly off and tumble with physics, and become item drops after resting for 5 s.
- The planted bomb is a mirror ball (Reflectance 1), like the original.

### Camera and controls (Roblox-style)
- **Free cursor**, shown as Roblox's arrow.
- **Right-drag** rotates the camera; the **scroll wheel** zooms (0.5 to 400 studs).
- **Left click** uses the held gear, and **Minecraft mining and placing act on the block under the mouse**. **Right click never triggers gear**, as in Roblox.
- **First person:** zoom all the way in and the mouse switches to mouse-look automatically.
- **No camera shake** while walking.
- The camera stops at walls instead of going through them.

### Movement
- Roblox **walk speed, jump height and gravity**, converted from studs to Minecraft blocks.
- Optional fall damage and hunger, both off by default like Roblox.
- The **"oof"** sound when you die, and no Minecraft hurt sounds, sprint dust or death smoke.

### 2018 interface
- **Top bar:** menu, chat and backpack buttons. Your name sits above "Account: 13+", with a health bar under them once you've been hurt.
- **Leaderstats** (Level, KOs, Wipeouts) appear in the top bar, each with its heading above its value.
- A **Backpack-style hotbar**: click a slot or press its number to equip it, then again to unequip. No player list.
- The backpack icon turns blue while your inventory is open, and the chat icon shows a count of unread messages.
- **2018 chat:**
  - `[Name]: message` in SourceSans;
  - each name is colored by Roblox's own name-color algorithm;
  - messages fade after 30 seconds;
  - the classic hint `To chat click here or press "/" key`.
- **Roblox notifications** replace Minecraft's "Advancement Made!" and "New Recipes Unlocked!" toasts.
- A **ForceField** protects you for 10 s after spawning or respawning.
- **Settings menu:** open it with the **Rocraft** button next to Friends on the title screen. The Avatar tab shows your loaded character. It looks like the 2018 Roblox settings menu and has **Avatar**, **Gameplay** and **Graphics** tabs.

### Sounds and particles
- Roblox character sounds from your install (RbxCharacterSounds): footsteps, jump, landing, falling wind, climbing, swimming and splash, replacing Minecraft's.
- Classic Roblox sounds: slash, lunge, unsheath, oof, coils, cola, the bomb tick and explosion, the rocket whoosh and boom, the superball boing, the slingshot and the trowel.
- Roblox's own Fire, Smoke and Explosion effects, built from the install's particle textures and color/alpha ramps. Fire and explosions glow additively.
- Burning in lava or fire shows Roblox Fire on your character, and the rocket trails its Fire (Heat 5, Size 2).

---

## Where the Roblox assets come from
**Nothing from Roblox is in this repo or the mod jar.** At runtime Rocraft gets everything from two places:

1. **Your local Roblox install** (`%LOCALAPPDATA%\Roblox\Versions\...`): the R6 meshes, fonts, UI textures, particle textures, stud textures and character sounds.
2. **The Roblox Open Cloud API**, using your own key: avatar accessories, faces, clothing, animations, gear meshes, textures and sounds.

The classic StarterPack tools (Rocket Launcher, Superball, Slingshot, Bomb, Trowel and the foods) point at old built-in `rbxasset://` files, for example `rbxasset://fonts/timebomb.mesh`. Roblox no longer ships those files with the client; it keeps website copies of them with normal asset IDs. Rocraft carries only that list of IDs plus each tool's grip and size numbers (`classic_tools.json`), and downloads the actual meshes, textures and sounds with your key.

*(Optional, older method: `tools/export_classic_tools.luau` + `tools/receive_export.ps1` export the tools from Roblox Studio into `config/rocraft/legacy`. If that folder exists it's used first; you don't need it.)*

Everything is saved privately in your Minecraft folder: the **"Rocraft Roblox Assets"** resource pack, `config/rocraft/cache` and `config/rocraft/legacy`.

Rocraft **never launches or hooks the Roblox client**. It only reads files and calls the web APIs.

---

## Requirements
- Minecraft **26.2**
- Fabric Loader **0.19+** and **Fabric API**
- **Java 25**
- **Roblox installed** on the same PC (the Roblox Player or Roblox Studio). Rocraft finds it automatically in `%LOCALAPPDATA%\Roblox\Versions`.
- **A Roblox Open Cloud API key**, so the Roblox assets can be downloaded (see below)

### Getting an API key
The key is free and only needs read access. Rocraft uses it to download assets (avatar clothing and accessories, gear, animations, sounds) through Roblox's Asset Delivery API.

1. Sign in at <https://create.roblox.com/dashboard/credentials> and click **Create API Key**.
2. Add these API systems and permissions:
   - **legacy-assets** → `legacy-assets:manage` (asset delivery);
   - **users** → `users.advanced:read`.
3. Under **Accepted IP Addresses**, add `0.0.0.0/0` (or your own IP), then save and copy the key.
4. In Minecraft, click the **Rocraft** button (next to Friends) on the title screen, open the **Avatar** tab, enter your Roblox username and the key, and press **Load Avatar**.
5. Restart Minecraft once so the gear, animations and sounds download into the "Rocraft Roblox Assets" pack.

The same steps are shown in game under **Settings → Avatar**, with a button that opens the credentials page and a check for whether Roblox is installed.

The key is stored **only** in `config/rocraft.json` on your PC and is never logged. **Don't share it.** If it leaks, regenerate it.

---

## Install
1. **Install Roblox** on this PC and open it once so it finishes downloading.
2. **Create an Open Cloud API key** ([Getting an API key](#getting-an-api-key)).
3. Install Fabric Loader for 26.2. Any launcher works; Modrinth App is tested.
4. Put **Fabric API** and the **Rocraft jar** in the `mods` folder.
5. Launch the game. The Rocraft settings open on first run: enter your username and API key there. Rocraft builds and turns on the "Rocraft Roblox Assets" pack automatically.

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
