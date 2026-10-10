# Rocraft

**2018-era Roblox inside Minecraft 26.2 (Fabric).** Your real Roblox avatar (R6 or R15), Roblox's animations and emotes, the classic Roblox gear, the Roblox camera and mouse, the 2018 top bar, chat and settings menu, and Roblox movement physics, all inside a normal Minecraft world.

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
- **Body packages** (Robloxian 2.0, Man, Woman, Boy, Penguin and so on) use the meshes Roblox puts on an R6 character (each part's R6 CharacterMesh), with your clothing wrapped on. Without a package you get the classic blocky R6 body.
- **Accessories** (hats, hair and so on) and your **face** are loaded from Roblox, not the default smile. Dynamic heads are supported too. Layered clothing (3D jackets, shirts, shoes) is left off, as Roblox does on R6 characters.
- **Roblox R6 animations** (idle, walk, jump, fall, climb, sit, tool hold, sword slash and lunge) are read from Roblox's own animation files and crossfaded the way Roblox's Animate script does.
- **The 2017 Guest:** with no username set you play as the Guest (R cap, ROBLOX Jacket, Black Jeans). Other players always appear as the Guest, since Rocraft can't know their Roblox accounts.
- **R15 avatars:** pick R15 under **Avatar Type** on the Settings avatar page (where you enter your username and Open Cloud key; R6 is the default) and you get the R15 body. Robloxian mobs use whatever their Roblox avatar or outfit uses. It is built the way Roblox builds it: the default rig from your install, with your body packages' R15 parts and joint positions swapped in. Clothing is laid onto the R15 parts with Roblox's own compositing meshes. R15 uses Roblox's 2018 R15 Animate animations. Avatar scaling (height, width, proportions) and animation packs aren't applied yet.
- **Emote wheel:** press **.** or click the emotes button on the top bar. It is Roblox's wheel, using the textures from your install, with your account's equipped emotes in their slots and their catalog thumbnails. Click a slot or press 1-8 to play an emote; **.** or Esc closes the wheel. With nothing equipped it offers the classic emotes. As on Roblox, emotes need an R15 avatar. Newer emotes (CurveAnimation) are supported.
- **Chat emotes:** `/e dance`, `/e wave`, `/e laugh`, `/e cheer`, `/e point` (R6 or R15 versions). Moving cancels them, as in Roblox.
- **Flying** (creative) works like HD Admin / Adonis fly: no animation, and the body faces wherever the camera looks, pitch included.
- **Your name floats above your head** in Roblox's font, with a green and red health bar once you're hurt.
- The settings menu's **Avatar** tab shows a live render of your loaded character.

### Classic gear
Everything is in the **Rocraft creative-inventory tab**. Each item is the real Roblox model, grip, icon and sounds, and behaves the way its original script does (speeds, damage, reloads). Damage is scaled ×0.2, so Roblox's 100 health maps to Minecraft's 20.

**Weapons**

| Gear | What it does |
|---|---|
| Linked Sword | The blade hurts whatever it touches: 5 just held, 10 for half a second after a click (slash), 30 for a second after a quick double click (lunge). The lunge throws you a little forward and up. Classic slash, lunge and unsheath sounds |
| Rocket Launcher | 60 studs/s rocket, no gravity, trailing Roblox Fire and its looping whoosh, no reload. Explodes on anything it touches (you included, once it's clear of you): radius 8, 60 damage, knockback. Other projectiles can shoot it down |
| Superball | Random-color ball at 200 studs/s, 25 damage. It bounces off everything, and the damage halves each time it hits a non-character |
| Slingshot | Red 1-stud pellet at 85 studs/s, no reload, 8 damage (it can hit you too), halved on each bounce and gone below 1 |
| Paintball Gun | Roblox's ClassicPaintballGun: 300 studs/s paintball in one of 7 colors, 2 damage, no reload. Paints the Roblox Part it hits and splats three plates of paint that clean up after 2 minutes |
| Bomb | Click to plant the classic time bomb: a mirror ball (Reflectance 1) that ticks faster and faster, then explodes. It rolls, bounces, and gets kicked along when you walk into it |

**Building and getting around**

| Gear | What it does |
|---|---|
| Trowel | Builds a 12×4 wall of real Roblox Parts (studded 4×1.2×2 bricks, one random BrickColor) where you click |
| Gravity Coil | Cancels 75% of gravity while held |
| Dual Gravity Coil | Cancels 85% of gravity while held |
| Speed Coil | Doubles your walk speed while held |
| Regeneration Coil | Heals 3 health every second while held |
| Green Balloon | Lifts you up in slow surges, arm raised, no jumping. It swells as it rises and pops 150 studs up |
| Blue Rolling Hoverboard | Click to drop it, then step on or right-click it to ride. W/S speed up to 35 studs/s, A/D turn, Space ollies, Shift steps off, and hitting it puts it back in your inventory |

**Food and fun**

| Gear | What it does |
|---|---|
| Bloxy Cola, Taco, Burger, Chicken, Pizza | Eat and drink with the Roblox poses and sounds |
| Teddy | Hug it and it says one of its classic lines |
| Save the Noobs Protest Sign | Click to shout one of its three angry lines |

**Hats:** Dominus Aureus, Dominus Rex, Doge, LOLHOO and Mr. Tentacles. Click one to put it in your helmet slot; it sits on your head exactly where Roblox puts it.

### Robloxian mobs
Shedletsky, Stickmasterluke, Roblox, builderman, Nikilis, clockwork, mrflimflam, DenisDaily, Linkmon99, PGHLego1945, TonyWong_0086, MyUsernamesThis and Sk3tchyt roam the Overworld (rarely) wearing their **real Roblox avatars**, each with a spawn egg in the Rocraft tab.
- Some wear a chosen saved outfit instead of their current avatar: Shedletsky (Classic Telamon), builderman ("2015", R6), MyUsernamesThis ("uniqueee boi", R15) and Sk3tchyt ("new reg", R15). DenisDaily wears a fixed look.
- Peaceful and empty-handed, like a Roblox NPC: they wander, look at you, and run if you hit them.
- 100 health, walk speed 16, their name and health bar overhead, and the same Roblox footstep, jump and landing sounds as you.
- Knocked out, they "oof" and **fall apart** like a Roblox character: head, torso and limbs drop and topple as separate pieces, cleared away after 5 s.

**How gear feels**
- No Minecraft arm swing: the Rocket Launcher, Superball, Slingshot, Paintball Gun, Bomb and Trowel fire on every click. The rest keep their Roblox debounce: sword lunge 1 s, Bloxy Cola 3 s, food 0.8 s, Teddy 2 s. Only the sword swings.
- In first person you see the tool's 3D Roblox model in your hand, not a flat icon.
- **Explosions** are Roblox-style (flash, shockwave, fireball, sparks, smoke), not Minecraft's. Rocket and bomb blasts **tear blocks loose**: they are thrown out of the crater in every direction (about 10 blocks, some much further), tumbling end over end, then bounce, slide and tip flat before turning into item drops after resting for 5 s. Roblox Parts caught in the blast lose their joints and are thrown, tumble and settle the same way, landing flat.

### Camera and controls (Roblox-style)
- **Free cursor**, shown as Roblox's arrow.
- **Right-drag** rotates the camera; the **scroll wheel** zooms (0.5 to 400 studs).
- **Left click** uses the held gear. With anything else, Minecraft mining and placing act on the **block under the mouse**, not the one at the screen centre.
- A plain right click (no drag) still does Minecraft's "use" for non-Roblox items. It never triggers gear, as in Roblox.
- **First person:** zoom all the way in and the mouse switches to mouse-look automatically.
- No camera shake while walking, and the camera stops at walls instead of going through them.

### Movement
- Roblox **walk speed, jump height and gravity**, converted from studs to Minecraft blocks.
- Optional fall damage and hunger, both off by default like Roblox.
- The **"oof"** sound when you die, and your character falls apart instead of Minecraft's fall-over, with no hurt sounds, sprint dust or death smoke.

### 2018 interface
- **Top bar:** menu, chat and backpack buttons. Your name sits above "Account: 13+", with a health bar under them once you've been hurt.
- **Leaderstats** (Level, KOs, Wipeouts) appear in the top bar, each with its heading above its value.
- A **Backpack-style hotbar**: click a slot or press its number to equip it, and again to unequip. No player list.
- The backpack icon turns blue while your inventory is open, and the chat icon shows a count of unread messages.
- **2018 chat:** fills from the top like Roblox's, opening with `Chat '/?' or '/help' for a list of chat commands.`. `[Name]: message` in SourceSans, names colored by Roblox's own name-color algorithm, messages fading after 30 seconds, and the classic hint `To chat click here or press "/" key`. `/?` and `/help` list the chat commands; `/help <command>` still explains a Minecraft command.
- **Roblox notifications** (the see-through 2018 boxes in the bottom right) replace Minecraft's "Advancement Made!" and "New Recipes Unlocked!" toasts.
- **Settings menu:** open it with the **Rocraft** button next to Friends on the title screen. It looks like the 2018 Roblox settings menu, with **Avatar**, **Gameplay** and **Graphics** tabs.

### Performance
Two common optimisation-mod features are built in, each in the settings menu's Graphics tab:
- **Hide Unseen Entities** (like EntityCulling): mobs, players and block entities (chests, signs, banners...) behind solid blocks aren't drawn. Roblox avatars are many meshes each, so this saves more than it would in vanilla.
- **Unfocused FPS Limit** (like Dynamic FPS): 30 FPS while the game window isn't focused.

If a mod that already does the job is installed, Rocraft leaves it to that mod and the setting shows "By <mod>": EntityCulling or Sodium for the first, Dynamic FPS for the second.

Rocraft also watches its own frame times and scales back its own extra work while frames run over budget (**Adaptive Performance**, on by default):
- Roblox flames, sparks and smoke are thinned out. The explosion flash and shockwave always show.
- Avatars further than 32 blocks are animated every 2nd or 3rd frame, and half as often again while over budget.
- Rocraft's caches are emptied when the Java heap is over 85% full.

**Stable Frame Pacing** (off by default) caps the frame rate at the highest common step (60, 72, 90, 120...) your slowest 10% of frames can keep up with, so frames arrive evenly instead of racing and then stalling.

Avatar meshes are drawn the way Roblox draws them: as plain triangles (not Minecraft's quads, which need a repeated fourth corner), switching to the mesh's own lower-detail LODs past about 9 and 18 blocks. Meshes without LODs (older accessories) always draw at full detail.

If the game sits at exactly 60 FPS, that is VSync matching a 60 Hz monitor (Video Settings > VSync, or the monitor's refresh rate in Windows display settings), not Rocraft.

The performance patches are optional: if one can't apply (another mod changed the same code), the game still starts without it.

### Stats (F3)
With the 2018 interface on, F3 shows Roblox-style stats panels under the top bar instead of Minecraft's debug screen:
- **Meters:** Physics, Render, GPU load and Memory each have a bar showing how much of their budget they use: green under 75%, yellow up to 100%, red when over. Physics' budget is 50 ms a tick. Render's is your frame limit (VSync: your monitor's refresh rate). Memory's is 85% of the heap.
- **Frame graph:** the last 240 frames as bars, coloured the same way, with the frame budget as a white line. A tall bar is a stutter.
- **FPS, World, Kernel:** server and render rates, network packets, ping, entities, Parts, debris, chunks, position, biome, light, ragdolls.
- **Graphics, Performance, Timing:** GPU, backend and load; what each optimisation is doing this frame; the likely bottleneck; detected optimisation mods; frame times (median, p90, p99); recent stutters and their cause (GC, chunk building or other); memory, allocation rate and GC.

### Sounds and particles
- **Character sounds** from your install (RbxCharacterSounds): footsteps, jump, landing, falling wind, climbing, swimming and splash, replacing Minecraft's.
- **Gear sounds** from Roblox:
  - the classic sword, coils, cola, foods and Teddy;
  - the bomb tick and blast, the rocket whoosh and boom, the superball boing, the slingshot, trowel and paintball;
  - the balloon pop, the hoverboard's drop, ollie, landing and braking, and the protest sign's shouts.
- **Roblox's own Fire, Smoke and Explosion effects**, built from the install's particle textures and color/alpha ramps. Fire and explosions glow additively.
- Burning in lava or fire shows Roblox Fire on your character instead of Minecraft's flames.

---

## Where the Roblox assets come from
**Nothing from Roblox is in this repo or the mod jar.** At runtime Rocraft gets everything from two places:

1. **Your local Roblox install** (`%LOCALAPPDATA%\Roblox\Versions\...`): the R6 meshes, fonts, UI textures, particle textures, stud textures and character sounds.
2. **The Roblox Open Cloud API**, using your own key: avatar accessories, faces, clothing, animations, gear and hat models, textures, icons and sounds.

The oldest tools (Rocket Launcher, Superball, Slingshot, Bomb, Trowel, the foods and Teddy) point at old built-in `rbxasset://` files, for example `rbxasset://fonts/timebomb.mesh`. Roblox no longer ships those files with the client; it keeps website copies of them with normal asset IDs. Rocraft carries only that list of IDs plus each tool's grip and size numbers (`classic_tools.json`), and downloads the actual meshes, textures and sounds with your key.

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
| `avatarType` | R6 | Your avatar's rig: `R6` or `R15` |
| `robloxMovement` | on | Roblox speed, jump and gravity |
| `fallDamage` | off | Minecraft fall damage |
| `hunger` | off | Minecraft hunger |
| `hud2018` | on | The 2018 top bar, hotbar and chat |
| `robloxFont` | on | SourceSans text |
| `robloxCamera` | on | Roblox camera and mouse |

All of these can be changed in the in-game **Settings** menu.

---

## Performance mods
Rocraft has its own light optimizations (entity culling, unfocused FPS limit, adaptive particles and animation). When a mod that does the same job is installed, Rocraft leaves that job to it (the F3 panel shows "by <mod>").

Tested together with Rocraft (Minecraft 26.2, Fabric API 0.161.0). No mixin failures, and the avatar, HUD, chat, F3 panels, emote wheel and settings all render normally.

| Mod | Version tested | Works with Rocraft? | Notes |
|---|---|---|---|
| Sodium | 0.9.2 | Yes | Sodium draws the chunks itself, so the F3 panel shows chunks and chunk builds as "by Sodium". |
| Sodium Extra | 0.9.4 | Yes | If you turn particles off in its settings, Roblox explosion and sparkle effects go too. |
| Lithium | 0.25.3 | Yes | Roblox movement, gear physics and falling parts behave the same. |
| FerriteCore | 9.0.0 | Yes | Memory only. |
| ImmediatelyFast | 1.16.5 | Yes | The 2018 HUD, Roblox fonts and menus draw correctly. |
| EntityCulling | 1.11.3 | Yes | Rocraft hands its entity culling over to it. |
| MoreCulling | 1.8.1 | Yes | Needs Cloth Config. |
| BadOptimizations | 2.4.1 | Yes | |
| Cloth Config | 26.2.155 | Yes | Library for MoreCulling. |

None of them conflict with Rocraft. Mods that Rocraft also detects and works alongside: C2ME, ModernFix, Dynamic FPS (takes over the unfocused FPS limit), Iris, Krypton, Noisium, VMP. Those haven't been tested in game yet.

## Known issues
- Command suggestions (the popup when you type `/`) don't show while the 2018 chat is on.
- Particle sizes and lifetimes are estimates.
- The hoverboard can climb one-block steps (Roblox's can't); otherwise it would get stuck on Minecraft terrain everywhere. Its ride sounds are only heard by the rider.
- The Paintball Gun only recolors Roblox Parts (like Trowel walls), not Minecraft blocks.
- The balloon's lift is close to Roblox's but not exact, because Minecraft adds air drag.
- Fallen body parts and explosion debris don't collide with each other, and body parts treat the ground under the character as flat.
- R15 characters fall apart as six R6-sized pieces (their limbs stay straight). The avatar picture in Settings and the spawn eggs are still drawn as R6.
- Emotes only show for you; other players don't see them.

## Credits
- **Inspired by [@coah80](https://x.com/coah80) on X.**
- **Vibecoded** with Claude Code (Anthropic).
- Roblox, its assets and the classic gear belong to **Roblox Corporation**. This is an unofficial fan project and is not affiliated with or endorsed by Roblox or Mojang.

## License
MIT, see [LICENSE](LICENSE). It covers the code only, not any Roblox assets.
