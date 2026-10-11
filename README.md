# Rocraft

**2018-era Roblox inside Minecraft 26.2 (Fabric).**

Your real Roblox avatar, Roblox's animations and emotes, the classic gear, the Roblox camera and mouse, the 2018 top bar, chat and settings menu, and Roblox movement physics, all in a normal Minecraft world.

![Your avatar and the Robloxian mobs in a Minecraft world, with the 2018 top bar and the classic gear in the backpack hotbar](screenshots/robloxians.png)

> [!NOTE]
> **This mod is vibecoded.** It was built almost entirely by AI (Claude Code) from prompts, with the user testing in game. Expect rough edges.
> Inspired by [@coah80](https://x.com/coah80) on X.

> [!IMPORTANT]
> **No Roblox assets ship with this mod.** Rocraft loads them from your PC and your account, so you need both of these:
> 1. **Roblox installed** on the same PC ([roblox.com/download](https://www.roblox.com/download)). It provides the R6 body, fonts, UI, particle textures and sounds. Without it, those fall back to plain Minecraft visuals.
> 2. **A free Roblox Open Cloud API key** ([how to get one](#getting-an-api-key)). It downloads your avatar's clothes and accessories, the gear models, animations and sounds. Without it you get the Guest's body colours, no animations and no gear models.

## Contents
- [Quick start](#quick-start)
- [What's in it](#whats-in-it)
- [Controls](#controls)
- [Avatar](#avatar)
- [Classic gear](#classic-gear)
- [Robloxian mobs](#robloxian-mobs)
- [2018 interface](#2018-interface)
- [Sounds and effects](#sounds-and-effects)
- [Performance](#performance)
- [Settings](#settings)
- [Where the Roblox assets come from](#where-the-roblox-assets-come-from)
- [Known issues](#known-issues)
- [Build from source](#build-from-source)

---

## Quick start

**You need:** Minecraft **26.2**, Fabric Loader **0.19+**, **Fabric API**, **Java 25**, Roblox (Player or Studio) installed on the same PC, and an Open Cloud API key.

1. **Install Roblox** and open it once so it finishes downloading. Rocraft finds it automatically in `%LOCALAPPDATA%\Roblox\Versions`.
2. **Create an Open Cloud API key** (below).
3. **Install Fabric Loader** for 26.2. Any launcher works; Modrinth App is tested.
4. Put **Fabric API** and the **Rocraft jar** in the `mods` folder.
5. **Launch the game.** The Rocraft settings open on first run: enter your Roblox username and API key and press **Load Avatar**. Rocraft builds the "Rocraft Roblox Assets" resource pack and turns it on by itself.
6. **Restart Minecraft once** so the gear, animations and sounds finish downloading.

### Getting an API key
The key is free and only needs read access. Rocraft uses it to download assets through Roblox's Asset Delivery API.

1. Sign in at <https://create.roblox.com/dashboard/credentials> and click **Create API Key**.
2. Add these API systems and permissions:
   - **legacy-assets** → `legacy-assets:manage` (asset delivery);
   - **users** → `users.advanced:read`.
3. Under **Accepted IP Addresses**, add `0.0.0.0/0` (or your own IP), then save and copy the key.
4. Paste it in game: **Rocraft** button (next to Friends on the title screen) → **Avatar** tab.

The same steps are in game under **Settings → Avatar**, with a button that opens the credentials page and a check that Roblox is installed.

> [!CAUTION]
> The key is stored **only** in `config/rocraft.json` on your PC and is never logged. **Don't share it.** If it leaks, regenerate it.

---

## What's in it

| | |
|---|---|
| **Your avatar** | Built from Roblox's own meshes, R6 or R15, with your clothes, accessories, face, body package and Roblox's animations |
| **Emotes** | Roblox's emote wheel (`.`) with your equipped emotes, plus `/e dance` and the other chat emotes |
| **Classic gear** | Linked Sword, Rocket Launcher, Superball, Slingshot, Paintball Gun, Bomb, Trowel, coils, Green Balloon, Hoverboard, food and more, each the real Roblox model with its original behaviour |
| **Robloxian mobs** | Shedletsky, builderman, Stickmasterluke and others roam the world in their real avatars, and fall apart when knocked out |
| **Roblox controls** | Free cursor, right-drag camera, scroll zoom, Roblox walk speed, jump and gravity |
| **2018 interface** | Top bar, backpack hotbar, 2018 chat, Roblox notifications, the 2018 settings menu, and Roblox-style F3 stats |
| **Sounds and effects** | Roblox character sounds, gear sounds, Fire, Smoke and Explosion effects, and the "oof" |

---

## Controls

| Input | What it does |
|---|---|
| Mouse | Free cursor, drawn as Roblox's arrow |
| Right-drag | Rotate the camera |
| Scroll wheel | Zoom, 0.5 to 400 studs; zoom all the way in for first person with mouse-look |
| Left click | Use the held gear. With anything else, mine or place on the **block under the mouse** |
| Right click (no drag) | Minecraft's "use" for non-Roblox items; it never triggers gear, as in Roblox |
| 1-9 or click a slot | Equip that hotbar slot; press again to unequip |
| `.` or the top bar's emotes button | Open the emote wheel; 1-8 or click to play, `.` or Esc to close |
| `/` | Chat; `/?` or `/help` lists the chat commands |
| F3 | Roblox-style stats panels |

The camera doesn't shake while walking and stops at walls instead of going through them.

---

## Avatar
- **Your real Roblox avatar**, built from Roblox's own R6 meshes rather than a Minecraft skin.
- **Body colours, shirt and pants** come from your Roblox profile.
- **Body packages** (Robloxian 2.0, Man, Woman, Boy, Penguin and so on) use each part's R6 CharacterMesh, with your clothing wrapped on. Without one you get the classic blocky R6 body.
- **Accessories** (hats, hair and so on) and your **face** are loaded from Roblox, and dynamic heads are supported. Layered clothing (3D jackets, shirts, shoes) is left off, as Roblox does on R6.
- **Roblox R6 animations** (idle, walk, jump, fall, climb, sit, tool hold, sword slash and lunge) come from Roblox's own animation files, crossfaded the way the Animate script does.
- **Your name floats above your head** in Roblox's font, with a green and red health bar once you're hurt.
- **Flying** (creative) works like HD Admin / Adonis fly: no animation, and the body faces wherever the camera looks, pitch included.
- **The 2017 Guest:** with no username you play as the Guest (R cap, ROBLOX Jacket, Black Jeans). Other players always appear as the Guest, since Rocraft can't know their Roblox accounts.

<details>
<summary><b>R15 avatars</b></summary>

Pick **R15** under **Avatar Type** on the Settings → Avatar page (R6 is the default). The R15 body is built the way Roblox builds it: the default rig from your install, with your body packages' R15 parts and joint positions swapped in. Clothing is laid onto the parts with Roblox's own compositing meshes, and the body moves with Roblox's 2018 R15 Animate animations. Avatar scaling (height, width, proportions) and animation packs aren't applied yet.
</details>

<details>
<summary><b>Emotes</b></summary>

- **Emote wheel:** press `.` or click the emotes button on the top bar. It's Roblox's wheel, drawn with the textures from your install, with your account's equipped emotes in their slots and their catalog thumbnails. With nothing equipped it offers the classic emotes. As on Roblox, the wheel needs an R15 avatar. Newer emotes (CurveAnimation) are supported.
- **Chat emotes:** `/e dance`, `/e wave`, `/e laugh`, `/e cheer`, `/e point` (R6 or R15 versions). Moving cancels them, as in Roblox.
</details>

---

## Classic gear
Everything is in the **Rocraft creative-inventory tab**. Each item uses the real Roblox model, grip, icon and sounds, and behaves the way its original script does (speeds, damage, reloads). Damage is scaled ×0.2, so Roblox's 100 health maps to Minecraft's 20.

### Weapons
| Gear | What it does |
|---|---|
| Linked Sword | The blade hurts whatever it touches: 5 just held, 10 for half a second after a click (slash), 30 for a second after a quick double click (lunge). The lunge throws you a little forward and up. Classic slash, lunge and unsheath sounds |
| Rocket Launcher | 60 studs/s rocket, no gravity, trailing Roblox Fire and its looping whoosh, no reload. Explodes on anything it touches (you included, once it's clear of you): radius 8, 60 damage, knockback. Other projectiles can shoot it down |
| Superball | Random-colour ball at 200 studs/s, 25 damage. It bounces off everything, and the damage halves each time it hits a non-character |
| Slingshot | Red 1-stud pellet at 85 studs/s, no reload, 8 damage (it can hit you too), halved on each bounce and gone below 1 |
| Paintball Gun | Roblox's ClassicPaintballGun: 300 studs/s paintball in one of 7 colours, 2 damage, no reload. Paints the Roblox Part it hits and splats three plates of paint that clean up after 2 minutes |
| Bomb | Click to plant the classic time bomb: a mirror ball (Reflectance 1) that ticks faster and faster, then explodes. It rolls, bounces, and gets kicked along when you walk into it |

### Building and getting around
| Gear | What it does |
|---|---|
| Trowel | Builds a 12×4 wall of real Roblox Parts (studded 4×1.2×2 bricks, one random BrickColor) where you click |
| Gravity Coil | Cancels 75% of gravity while held |
| Dual Gravity Coil | Cancels 85% of gravity while held |
| Speed Coil | Doubles your walk speed while held |
| Regeneration Coil | Heals 3 health every second while held |
| Green Balloon | Lifts you up in slow surges, arm raised, no jumping. It swells as it rises and pops 150 studs up |
| Blue Rolling Hoverboard | Click to drop it, then step on or right-click it to ride. W/S speed up to 35 studs/s, A/D turn, Space ollies, Shift steps off, and hitting it puts it back in your inventory |

### Food, fun and hats
| Gear | What it does |
|---|---|
| Bloxy Cola, Taco, Burger, Chicken, Pizza | Eat and drink with the Roblox poses and sounds |
| Teddy | Hug it and it says one of its classic lines |
| Save the Noobs Protest Sign | Click to shout one of its three angry lines |
| Dominus Aureus, Dominus Rex, Doge, LOLHOO, Mr. Tentacles | Click one to put it in your helmet slot; it sits on your head exactly where Roblox puts it |

### How gear feels
- **No Minecraft arm swing.** The Rocket Launcher, Superball, Slingshot, Paintball Gun, Bomb and Trowel fire on every click. The rest keep their Roblox debounce: sword lunge 1 s, Bloxy Cola 3 s, food 0.8 s, Teddy 2 s. Only the sword swings.
- **In first person** you see the tool's 3D Roblox model in your hand, not a flat icon.
- **Explosions** are Roblox-style (flash, shockwave, fireball, sparks, smoke). Rocket and bomb blasts **tear blocks loose**: they fly out of the crater (about 10 blocks, some much further), tumble, bounce, slide and tip flat, then become item drops after resting for 5 s. Roblox Parts caught in a blast lose their joints and are thrown the same way.

---

## Robloxian mobs
Shedletsky, Stickmasterluke, Roblox, builderman, Nikilis, clockwork, mrflimflam, DenisDaily, Linkmon99, PGHLego1945, TonyWong_0086, MyUsernamesThis and Sk3tchyt roam the Overworld (rarely) in their **real Roblox avatars**. Each has a spawn egg in the Rocraft tab.

- **Saved outfits:** some wear a chosen outfit instead of their current avatar: Shedletsky (Classic Telamon), builderman ("2015", R6), MyUsernamesThis ("uniqueee boi", R15) and Sk3tchyt ("new reg", R15). DenisDaily wears a fixed look.
- **Behaviour:** peaceful and empty-handed, like a Roblox NPC. They wander, look at you, and run if you hit them.
- **Stats:** 100 health and walk speed 16, with their name and health bar overhead and the same Roblox footstep, jump and landing sounds as you.
- **Knockouts:** they "oof" and **fall apart** like a Roblox character. Head, torso and limbs drop as separate pieces and are cleared after 5 s.

---

## 2018 interface
- **Top bar:** menu, chat, backpack and emotes buttons. Your name sits above "Account: 13+", with a health bar once you've been hurt. **Leaderstats** (Level, KOs, Wipeouts) sit on the right.
- **Backpack hotbar:** only filled slots show, keeping their numbers. The equipped slot gets Roblox's blue border inside the slot. The backpack icon turns blue while your inventory is open. There's no player list.
- **2018 chat:** fills from the top like Roblox's, with `[Name]: message` in SourceSans and names coloured by Roblox's own name-colour algorithm. Messages fade after 30 seconds, under the classic hint `To chat click here or press "/" key`. The chat icon counts unread messages. `/help <command>` still explains Minecraft commands.
- **Roblox notifications:** the see-through 2018 boxes in the bottom right replace Minecraft's "Advancement Made!" and "New Recipes Unlocked!" toasts.
- **Settings menu:** opened with the **Rocraft** button next to Friends on the title screen. It looks like the 2018 Roblox settings menu, with **Avatar**, **Gameplay** and **Graphics** tabs. The Avatar tab shows a live render of your character.

![The Rocraft settings menu: Avatar tab with username, Open Cloud key, Avatar Type and a live render of the loaded avatar](screenshots/settings.png)

<details>
<summary><b>F3 stats panels</b></summary>

With the 2018 interface on, F3 shows Roblox-style stats panels under the top bar instead of Minecraft's debug screen.
- **Meters:** Physics, Render, GPU load and Memory each have a bar showing how much of their budget they use: green under 75%, yellow up to 100%, red when over. Physics' budget is 50 ms a tick. Render's is your frame limit (with VSync, your monitor's refresh rate). Memory's is 85% of the heap.
- **Frame graph:** the last 240 frames as bars, coloured the same way, with the frame budget as a white line. A tall bar is a stutter.
- **FPS, World, Kernel:** server and render rates, network packets, ping, entities, Parts, debris, chunks, position, biome, light and ragdolls.
- **Graphics, Performance, Timing:** GPU, backend and load. What each optimisation is doing this frame, the likely bottleneck, and detected optimisation mods. Frame times (median, p90, p99), recent stutters and their cause (GC, chunk building or other), memory, allocation rate and GC.
</details>

---

## Sounds and effects
- **Character sounds** from your install (RbxCharacterSounds) replace Minecraft's: footsteps, jump, landing, falling wind, climbing, swimming and splash.
- **The "oof"** when you die, and your character falls apart instead of Minecraft's fall-over, with no hurt sounds, sprint dust or death smoke.
- **Gear sounds** from Roblox, from the sword and the bomb tick to the superball boing, the balloon pop, the hoverboard and the protest sign's shouts.
- **Roblox's own Fire, Smoke and Explosion effects**, built from the install's particle textures and colour/alpha ramps. Fire and explosions glow additively.
- Burning in lava or fire shows Roblox Fire on your character instead of Minecraft's flames.

---

## Performance
**Avatar rendering:**
- Avatar meshes are drawn the way Roblox draws them, as plain triangles.
- They switch to the mesh's own lower-detail LODs past about 9 and 18 blocks. Meshes without LODs (older accessories) always draw at full detail.
- Each mesh is uploaded to the GPU once and stays there. Per frame, only its pose, colour and light are sent. With 13 Robloxians in view, this cut about 7.4 ms of frame work to 0.9 ms.

**Built-in optimisations** (settings menu → Graphics):
- **Hide Unseen Entities** (like EntityCulling): mobs, players and block entities behind solid blocks aren't drawn.
- **Unfocused FPS Limit** (like Dynamic FPS): 30 FPS while the game window isn't focused.
- **Adaptive Performance** (on by default): while frames run over budget, Roblox flames, sparks and smoke are thinned out, though explosion flashes and shockwaves always show. Avatars further than 32 blocks are animated every 2nd or 3rd frame, and half as often again while over budget. Rocraft's caches are emptied when the Java heap is over 85% full.
- **Stable Frame Pacing** (off by default): caps the frame rate at the highest common step (60, 72, 90, 120...) that your slowest 10% of frames can keep up with, so frames arrive evenly.

If a mod that does the same job is installed, Rocraft leaves that job to it and the setting shows "By <mod>". The performance patches are optional: if one can't apply because another mod changed the same code, the game still starts without it.

> [!TIP]
> Stuck at exactly 60 FPS? That's VSync matching a 60 Hz monitor (Video Settings → VSync, or your monitor's refresh rate in Windows display settings), not Rocraft.

### Works with these performance mods
Tested together with Rocraft (Minecraft 26.2, Fabric API 0.161.0): no mixin failures, and the avatar, HUD, chat, F3 panels, emote wheel and settings all render normally.

| Mod | Version tested | Works? | Notes |
|---|---|---|---|
| Sodium | 0.9.2 | ✅ | Sodium draws the chunks itself, so F3 shows chunks and chunk builds as "by Sodium" |
| Sodium Extra | 0.9.4 | ✅ | Turning particles off in its settings also hides Roblox explosion and sparkle effects |
| Lithium | 0.25.3 | ✅ | Roblox movement, gear physics and falling parts behave the same |
| FerriteCore | 9.0.0 | ✅ | Memory only |
| ImmediatelyFast | 1.16.5 | ✅ | The 2018 HUD, Roblox fonts and menus draw correctly |
| EntityCulling | 1.11.3 | ✅ | Rocraft hands its entity culling over to it |
| MoreCulling | 1.8.1 | ✅ | Needs Cloth Config |
| BadOptimizations | 2.4.1 | ✅ | |
| Cloth Config | 26.2.155 | ✅ | Library for MoreCulling |

Rocraft also detects and works alongside C2ME, ModernFix, Dynamic FPS (which takes over the unfocused FPS limit), Iris, Krypton, Noisium and VMP. Those haven't been tested in game yet.

---

## Settings
Everything can be changed in the in-game **Settings** menu. It's saved in `config/rocraft.json`:

| Setting | Default | Meaning |
|---|---|---|
| `username` | | Your Roblox username |
| `apiKey` | | Your Open Cloud key (private) |
| `avatarType` | R6 | Your avatar's rig: `R6` or `R15` |
| `robloxMovement` | on | Roblox walk speed, jump height and gravity, converted from studs to blocks |
| `fallDamage` | off | Minecraft fall damage (off, like Roblox) |
| `hunger` | off | Minecraft hunger (off, like Roblox) |
| `hud2018` | on | The 2018 top bar, hotbar and chat |
| `robloxFont` | on | SourceSans text |
| `robloxCamera` | on | Roblox camera and mouse |

---

## Where the Roblox assets come from
**Nothing from Roblox is in this repo or the mod jar.** At runtime Rocraft gets everything from two places:

1. **Your local Roblox install** (`%LOCALAPPDATA%\Roblox\Versions\...`): R6 meshes, fonts, UI textures, particle textures, stud textures and character sounds.
2. **The Roblox Open Cloud API**, with your own key: avatar accessories, faces, clothing, animations, gear and hat models, textures, icons and sounds.

Everything is saved privately in your Minecraft folder: the **"Rocraft Roblox Assets"** resource pack, `config/rocraft/cache` and `config/rocraft/legacy`.

Rocraft **never launches or hooks the Roblox client**. It only reads files and calls the web APIs.

<details>
<summary><b>The oldest tools</b></summary>

The Rocket Launcher, Superball, Slingshot, Bomb, Trowel, the foods and Teddy point at old built-in `rbxasset://` files such as `rbxasset://fonts/timebomb.mesh`. Roblox no longer ships those with the client, but it keeps website copies with normal asset IDs. Rocraft carries only that list of IDs plus each tool's grip and size numbers (`classic_tools.json`), and downloads the meshes, textures and sounds with your key.

An optional, older method is also included: `tools/export_classic_tools.luau` and `tools/receive_export.ps1` export the tools from Roblox Studio into `config/rocraft/legacy`, which is used first if it exists. You don't need it.
</details>

---

## Known issues
- Command suggestions (the popup when you type `/`) don't show while the 2018 chat is on.
- Particle sizes and lifetimes are estimates.
- The hoverboard can climb one-block steps (Roblox's can't); otherwise it would get stuck on Minecraft terrain everywhere. Its ride sounds are only heard by the rider.
- The Paintball Gun only recolours Roblox Parts (like Trowel walls), not Minecraft blocks.
- The balloon's lift is close to Roblox's but not exact, because Minecraft adds air drag.
- Fallen body parts and explosion debris don't collide with each other, and body parts treat the ground under the character as flat.
- R15 characters fall apart as six R6-sized pieces (their limbs stay straight). The avatar picture in Settings and the spawn eggs are still drawn as R6.
- Emotes only show for you; other players don't see them.

---

## Build from source
```bash
./gradlew build
```
The jar lands in `build/libs/rocraft-1.0.0.jar`.

Two extra Gradle tasks check things without starting Minecraft:
- `./gradlew bench` checks the movement physics against Roblox: jump height, the fit to Minecraft's physics, and the R6 rest pose.
- `./gradlew rbxDump` dumps the contents of a Roblox model file.

---

## Credits
- **Inspired by [@coah80](https://x.com/coah80) on X.**
- **Vibecoded** with Claude Code (Anthropic).
- Roblox, its assets and the classic gear belong to **Roblox Corporation**. This is an unofficial fan project, not affiliated with or endorsed by Roblox or Mojang.

## License
MIT, see [LICENSE](LICENSE). It covers the code only, not any Roblox assets.
