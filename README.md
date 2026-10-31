# Immersive Portals Mod for NeoForge

It's a Minecraft mod that provides see-through portals and seamless teleportation. It also can create "Non-Euclidean" (Uneuclidean) space effect.

![immptl.png](https://i.loli.net/2021/09/30/chHMG45dsnZNqep.png)

[On CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-mod)     [On Modrinth](https://modrinth.com/mod/immersiveportals)     [Website](https://qouteall.fun/immptl/)

This mod changes a lot of underlying Minecraft mechanics. This mod allows the client to load multiple dimensions at the same time and synchronize remote world information(blocks/entities) to client. It can render portal-in-portals. The portal rendering is roughly compatible with some versions of Sodium and Iris. The portal can transform player scale and gravity direction.  [Implementation Details](https://qouteall.fun/immptl/wiki/Implementation-Details)

## API

This mod also provides some API for:

* Manage see-through portals
* Dynamically add dimensions
* Synchronize remote chunks to client
* Render the world into GUI
* Other utilities

[API description](https://qouteall.fun/immptl/wiki/API-for-Other-Mods.html).

## Minecraft 26.2 (NeoForge)

This branch targets Minecraft 26.2 with NeoForge 26.2.0.93 or newer, and needs Java 25.

Build the jar with `./gradlew build` (it ends up in `build/libs`).
Run the game with `./gradlew runClient` or `./gradlew runServer`.

The mod is required on both the server and the client.

### Changes in this port

* The portal rendering was rewritten for the Minecraft 26 renderer.
  The view through a portal is rendered into an off-screen target before the main view,
  and the portal surface shows that target.
* The config file is `config/immersive_portals.json` (there is no config screen).
  After editing it, run `/imm_ptl_client_debug reload_config`.

### Not supported yet

* Portals seen through other portals are not drawn (one layer of portals).
* At most 2 portal views are rendered per frame (the nearest ones).
* Mirrors are not drawn.
* The player is not shown in portal views, and entities passing through a portal are not drawn on both sides.
* The dimension stack screen. Configure the dimension stack with `dimStackPreset` in the config file.
* The portal wand's preview rendering.
* Sodium and Iris compatibility.

## Other

[Wiki](https://qouteall.fun/immptl/wiki/)

[Discord Server](https://discord.gg/BZxgURK)

[Support qouteall on Patreon](https://www.patreon.com/qouteall)

The forge version is currently maintained by @Nick1st
