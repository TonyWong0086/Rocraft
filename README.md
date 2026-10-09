# Rocraft

Roblox (2018 era) inside Minecraft 26.2 Fabric: your real R6 avatar, Roblox animations, the classic StarterPack gear, the Roblox camera and mouse, the 2018 top bar, chat and settings menu, and Roblox movement physics.

## Requirements
- Minecraft 26.2, Fabric Loader 0.19+, Fabric API, Java 25
- Roblox installed on the same PC. Meshes, textures, fonts, sounds and particles are read from your own install at runtime and never shipped in this repo or the jar.
- Optional: a Roblox Open Cloud API key (`legacy-assets:manage`, `users:read`) to load your avatar. Without one you play as a Guest.

## Build
```
./gradlew build
```
The jar lands in `build/libs/`. Your API key is stored only in `config/rocraft.json`.

## License
See [LICENSE](LICENSE).
