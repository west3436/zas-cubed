# NeoForge 1.21.1 platform research (zas-cubed)

_Researched 2026-10-30. Target: Minecraft 1.21.1, NeoForge 21.1.x, Java 21._

**Sources read directly:** NeoForge branch `1.21.1` at `624c929` (= 21.1.265), FancyModLoader branch `1.21.1` (FML 4.0.45), the official MDK `NeoForgeMDKs/MDK-1.21.1-ModDevGradle` (updated 2026-10-28), ModDevGradle `main` README and test project, `neoforged/Documentation` `versioned_docs/version-1.21.1` (served at `docs.neoforged.net/docs/1.21.1/...`), the NeoForge primers (`neoforged/.github/primers/1.21.2`, `/1.21.5`), and Mojang's official 1.21.1 mappings, diffed against 1.21.3 and 1.21.5, for vanilla names.

`NF:` below means `https://github.com/neoforged/NeoForge/blob/1.21.1/`. Each NeoForge version is `21.1.<commits since tag 21.1>`; I verified this against the branch head, which is 21.1.265, so a backport's version can be read from its commit.

## Implications for zas-cubed

- **Require `neoforge [21.1.264,)`; build against 21.1.265.** Syncable data attachments were backported in **21.1.197** (#2544); the `sendToPlayer` filter applies to *initial* sync only from **21.1.264** (#3670); network hardening landed in 21.1.260–262. The 1.21.1 docs still say "sync attachments yourself", so trust the source over the docs.
- **No event sees every block change.** Use one tiny mixin at `LevelChunk#setBlockState(BlockPos, BlockState, boolean)` (server levels only) that just enqueues dirty positions; process them at a tick boundary. Rebuild on `ChunkEvent.Load` for worldgen and offline edits. Events are batching hints only.
- **Airtightness = collision shapes, precomputed per BlockState** as per-face coverage masks. A face is sealed when the two touching faces together cover it (same idea as `Shapes.mergedFaceOccludes`). Never use render-occlusion APIs: glass and leaves are `noOcclusion()`.
- **Storage:** gas contents and zone fragments in a serializable **chunk attachment**; the live zone graph **in memory**, rebuilt from loaded chunks; breathing/suit state in **entity attachments** with `.sync(...)`. No level-wide SavedData for zone geometry.
- **Threading:** a pure-Java simulation core runs on snapshots (`PalettedContainer#copy()`) in a dedicated executor; results are applied on the server thread. Never touch `Level`, chunks, capabilities or attachments off-thread.
- **Public API = capabilities + registries:** `BlockCapability<IGasHandler, @Nullable Direction>` and `ItemCapability<ILifeSupport, Void>` in the API jar; a static `GasType` registry; data-driven reactions and per-dimension atmospheres, plus an `AtmosphereProvider` hook for code-defined ones.
- **Breathing uses events; fire needs mixins.** `LivingBreatheEvent`/`LivingDrownEvent` also fire client-side, so sync breathability for the local player. Fire, furnaces and campfires have no events, but O₂ *consumption* can be counted through the block-change hook (fire blocks and LIT states change via `setBlock`).
- **Gradle layout:** `:core` (plain Java + JUnit), `:api` (MDG, published), `:mod` (MDG, bundles both). Never ship one package in two jars: FML loads JPMS modules and a split package crashes the loader ([structuring docs](https://docs.neoforged.net/docs/1.21.1/gettingstarted/structuring/)).
- **Don't copy 1.21.2+ snippets** (table at the end).

## 1. Toolchain

- **NeoForge:** latest 1.21.1 release is **21.1.265** (Maven, 2026-10-25). The branch still gets backports and security fixes, though docs.neoforged.net marks the 1.21.1 docs "no longer actively maintained."
- **Gradle plugin:** **ModDevGradle 2.0.148** (`net.neoforged.moddev`, 2026-09-29), as used by the 1.21.1 MDK; NeoGradle 7.1.39 is the alternative. NeoForged ([NeoForgeMDKs](https://github.com/neoforgemdks)): "functionally equivalent"; MDG is "aimed for simpler and more streamlined buildscripts", NeoGradle supports several MC versions per project, "both alternatives are fully endorsed." MDG fits a single-version mod.
- **Gradle/Java:** the MDK pins **Gradle 9.2.1** (NeoForge's own 1.21.1 build uses 9.8.1; newer 9.x untested with MDG). Java 21 toolchain plus the foojay resolver.
- **Parchment:** `minecraftVersion=1.21.1`, `mappingsVersion=2024.11.17` (latest). MDG 2 errors if only one is set.
- **Useful MDG features:** `validateAccessTransformers = true`, a `gameTestServer` run type, `unitTest { enable(); testedMod = mods.zas }`, `addModdingDependenciesTo(sourceSet)`. Since 2.0.136, decompile/recompile is skipped when `CI=true`.
- **Multi-module:** MDG's [test project](https://github.com/neoforged/ModDevGradle/tree/main/testproject) shows an `api` source set and a cross-project source set in one mod (`mods { x { sourceSet project(':sub').sourceSets.main } }` + `evaluationDependsOn`). For zas-cubed:
  - `:core`: plain `java-library`, no Minecraft classes, fast tests.
  - `:api`: MDG, same NeoForge version, `maven-publish` + `withSourcesJar()`/`withJavadocJar()`.
  - `:mod`: bundles the core and api classes and lists their source sets in `mods {}`.
  - Untested: listing a non-MDG `:core` source set in `mods {}`. Fallback is `jarJar project(':core')`, which makes core a separate module and, on MC ≤1.21.8, needs `additionalRuntimeClasspath` in dev.
- **Publishing:** MDG wires NeoForge in with `extendsFrom` on the classpath configurations, not `implementation`, so published POMs don't depend on NeoForge. Use the MDK's `localRuntime` for runtime-only mods. Give the API its own semver (the docs cite Mekanism's `major.api.minor.patch`) and mark internals `@ApiStatus.Internal`. ATs can be published (`accessTransformers { publish file(...) }`).
- **Jar-in-jar:** `jarJar(implementation("g:a")) { version { strictly '[1.0,2.0)'; prefer '1.2.0' } }`. FML picks one copy across mods, so module names must be unique ([docs](https://docs.neoforged.net/toolchain/docs/dependencies/jarinjar/)).
- **Mekanism (optional):** `compileOnly "mekanism:Mekanism:1.21.1-<ver>:api"` from `https://modmaven.dev/`. The `1.21.x` branch is 10.7.19 (needs NeoForge ≥21.1.194). Capabilities: `mekanism:chemical_handler` (`IChemicalHandler`; gases are now chemicals) and `mekanism:heat_handler`. Declare `type="optional"` in `neoforge.mods.toml` (not `mods.toml`).

## 2. Data storage

- **Data attachments** (`NF:src/main/java/net/neoforged/neoforge/attachment/`):
  - Register with `DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID)`.
  - Build with `AttachmentType.builder(Supplier | Function<IAttachmentHolder,T>)`, adding `.serialize(Codec)` or `.serialize(IAttachmentSerializer)`. Alternatively use `AttachmentType.serializable(...)` for `INBTSerializable` values.
  - Options: `.copyOnDeath()`, `.copyHandler()`, and `.sync(StreamCodec)` / `.sync(BiPredicate<IAttachmentHolder,ServerPlayer>, StreamCodec)` / `.sync(AttachmentSyncHandler)`.
- **Holders in 1.21.1:** `Entity`, `BlockEntity`, `ChunkAccess` (serializable data is copied from ProtoChunk to LevelChunk on promotion), and `Level` (only `ServerLevel` persists it, via the internal `LevelAttachmentsSavedData`, rewritten on every save). API: `getData`, `hasData`, `getExistingData`, `getExistingDataOrNull` (21.1.166+), `setData`, `removeData`, `syncData`.
- **Dirtiness:** `setData` marks the chunk (`setUnsaved(true)`) and syncs. If you mutate a value in place, you must call `chunk.setUnsaved(true)` or `be.setChanged()` yourself, then `syncData(type)`.
- **Sync (21.1.197+):**
  - Happens automatically on initial send (`ChunkWatchEvent.Sent` for chunks and block entities, entity pairing, login and level change), on default creation via `getData`, and on `setData`/`removeData`.
  - Chunk updates go to `chunkMap.getPlayers(pos, false)`.
  - `AttachmentSyncHandler#write(buf, value, initialSync)` allows sending deltas instead of the full value.
  - Supported holder types: BlockEntity, LevelChunk, Entity, Level (`AttachmentSync.java`).
- **SavedData** in its 1.21.1 shape ([docs](https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/)):
  - Implement `save(CompoundTag, HolderLookup.Provider)` and call `setDirty()` when data changes.
  - Get it with `level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ctor, loader), "name")`. NeoForge adds the 2-argument `Factory`.
  - Since 21.1.118 only the compressed file write is offloaded to the IO worker; **`save()` itself still runs on the server thread**.
- **Which store fits which data:**
  - Per-chunk zone membership, fragments and gas contents → chunk attachment (its lifecycle matches when the chunk can be simulated).
  - Level-wide zone graph → in memory only (an unserialized level attachment or a map cleared on `LevelEvent.Unload`). Persist only what can't be recomputed (moles, temperature), keyed per chunk, so partially loaded levels stay consistent.
  - SavedData → small global state. Keep codecs and constructors free of world access (NeoForge documents `ChunkDataEvent.Load` as possibly async).

## 3. Block-change detection

**NeoForge events** (`NF:.../event/level/`):

| Event | When it fires |
|---|---|
| `BlockEvent.EntityPlaceEvent` / `EntityMultiPlaceEvent` | An entity places a block |
| `BlockEvent.BreakEvent` | A player breaks a block (fires before the break) |
| `BlockEvent.NeighborNotifyEvent` | Fired from `Level#updateNeighborsAt`, so only for writes with neighbor updates |
| `BlockEvent.FluidPlaceBlockEvent` | Lava/water creates blocks or fire |
| `CreateFluidSourceEvent` | A new fluid source forms |
| `PistonEvent.Pre` / `Post` | Piston moves |
| `ExplosionEvent.Start` / `Detonate` | Explosions; `Detonate#getAffectedBlocks()` is available before blocks are removed |
| `BlockToolModificationEvent` | A tool modifies a block |
| `ChunkEvent.Load` / `Unload` | Chunk lifecycle |

**Gaps.** Nothing covers generic `setBlock` from mods, `/fill`/`/clone`/`/place`, normal fluid spread, doors toggling, furnace `LIT` changes, fire spread, or WorldEdit. NeoForge's `IBlockExtension#onBlockStateChange(LevelReader, BlockPos, old, new)` reaches only the *new* state's block, so it only helps for our own blocks (firelocks).

**Mixin targets** (1.21.1 signatures, verified in the official mappings):

| Target | Sees | Caveats |
|---|---|---|
| `LevelChunk#setBlockState(BlockPos, BlockState, boolean)` at RETURN, non-null result (= old state) | Every full-chunk write: all `Level#setBlock` callers (players, pistons, explosions, fluids, commands, in-world structure placement, mods) **plus direct chunk writers** | Misses ProtoChunks (handle worldgen on load). Runs mid-update (`onRemove`/`onPlace` can nest writes), so only enqueue. Also runs for `ClientLevel`. Reverted snapshot placements pass through here too. Becomes `(…, int)` in 1.21.5. Commonly targeted (Carpet, Flashback, Arclight, WorldEdit). |
| `Level#onBlockStateChange(BlockPos, BlockState, BlockState)` (`ServerLevel` override does POI) | Settled changes via `markAndNotifyBlock` (after `EntityPlaceEvent` when snapshots are captured) | Misses direct chunk writers; renamed `updatePOIOnBlockStateChange` in 1.21.5 |
| `Level#setBlock(BlockPos, BlockState, int, int)` | Update flags | Misses direct chunk writers; sees no-op writes |
| `LevelChunkSection#setBlockState(int,int,int,BlockState,boolean)` | Everything incl. worldgen | No level/pos context; hottest path; worldgen worker threads; Lithium's `chunk.no_locking` patches it too |

**WorldEdit** (checked on its current `version/7.3.x` branch, not a 1.21.1 release): the NeoForge module writes through `LevelChunk#setBlockState` and runs POI and neighbor updates only when those side effects are on, so an `onBlockStateChange`-only hook misses its edits.

**Threading.** Server-side `setBlock` is expected on the server thread but nothing asserts it; `PalettedContainer`'s `ThreadingDetector` only catches concurrent *writes*, and Lithium removes that lock. Keep the injection allocation-free, check `server.isSameThread()`, and fall back to a concurrent queue. Shapes driven by block-entity data and offline edits are invisible to the hook; expose `markDirty(level, pos)` in the API and rebuild on `ChunkEvent.Load`.

## 4. Block geometry

- **Use collision shapes:** `BlockStateBase#getCollisionShape(BlockGetter, BlockPos[, CollisionContext])`. When `!block.hasDynamicShape()`, vanilla precomputes `BlockStateBase.Cache` (`collisionShape`, `isCollisionShapeFullBlock`, `faceSturdy[]`, …) with `EmptyBlockGetter.INSTANCE`/`BlockPos.ZERO`, so shapes are state-only by contract. Blocks whose shape depends on position, block entity or entity must declare `Properties#dynamicShape()` (likely moving pistons, shulker boxes, offset plants; unverified).
- **Helpers available in 1.21.1:**
  - `Block.isFaceFull(VoxelShape, Direction)` and `Block.isShapeFullBlock(VoxelShape)`, backed by the Guava `SHAPE_FULL_BLOCK_CACHE`.
  - `VoxelShape#getFaceShape(Direction)`: cached in `faces[]`, and still present in 1.21.5, so prefer it.
  - `VoxelShape#toAabbs()`.
  - `Shapes.joinIsNotEmpty(a, b, BooleanOp)`.
  - `Shapes.blockOccudes(a, b, dir)`: render culling, "is my face hidden by my neighbor"; renamed `blockOccludes` in 1.21.5.
  - `Shapes.mergedFaceOccludes(a, b, dir)` and `Shapes.faceShapeOccludes(faceA, faceB)`: true when the union of both faces covers the boundary. The light engine uses the same idea (`LightEngine#shapeOccludes`).
  - The static `Shapes.getFaceShape` is removed in 1.21.2.
- **Wrong APIs for airtightness:**
  - `isFaceSturdy(...)` (support for things like torches).
  - `getFaceOcclusionShape`, `canOcclude`, `isSolidRender` (rendering and light).
- **Recommendation:**
  - Lazily compute six face masks per state (8×8 or 16×16 bits from `getFaceShape(d).toAabbs()`), indexed by `Block.getId(state)` (dense `BLOCK_STATE_REGISTRY` ids). Most faces are empty or full, so deduplicate via a small palette.
  - Seal test: `full(mask[a][d] | mask[b][d.opposite()])`, which handles half-slab meeting half-slab. Center walls (panes, bars, walls) need a cross-section test or overrides.
  - Overrides via block tags or a block data map (e.g. permeable leaves), rebuilt on `TagsUpdatedEvent`. Dynamic-shape states: compute per position, main thread only.
- **Scale:** vanilla 1.21.1 has 1,060 blocks and **26,684 states** (PrismarineJS minecraft-data). Big packs plausibly reach 10⁵+ (unverified estimate): precompute lazily, keep per-state data compact.
- **Fluids** have an empty collision shape, so they need an explicit policy: `getFluidState()` → `isEmpty()`, `isSource()`, `getAmount()`, `getOwnHeight()`, `getHeight(level, pos)` (neighbor-dependent). Waterlogged states have both a shape and a fluid.

## 5. Ticking & threading

- **Tick events** (`NF:.../event/tick/`):
  - `ServerTickEvent.Pre/Post` (head and tail of `MinecraftServer#tickServer`, with `hasTime()`).
  - `LevelTickEvent.Pre/Post`. These also fire for `ClientLevel`, so check the side.
  - `EntityTickEvent.Pre` (cancellable) and `EntityTickEvent.Post`.
  - `PlayerTickEvent.Pre/Post` and `ClientTickEvent.Pre/Post`.
- **Chunk lifecycle:**
  - `ChunkEvent.Load(chunk, isNewChunk)` fires on the main thread during FULL promotion. NeoForge warns that touching the level there can deadlock, so defer the work.
  - `ChunkEvent.Unload` fires before the chunk is saved.
  - Also available: `ChunkWatchEvent.Watch/Sent/UnWatch`, `ChunkTicketLevelUpdatedEvent`, `LevelEvent.Load/Unload/Save`.
- **Off-thread work:** on the server thread, snapshot dirty sections (`LevelChunkSection#getStates().copy()`) or convert them straight to face masks. Run them on your own small daemon executor, not `Util.backgroundExecutor()` (shared with worldgen). Drain results in `ServerTickEvent.Post` (or `server.execute`), dropping stale ones via a per-chunk generation counter. Off-thread `Level#getBlockState` is unsafe: vanilla `ServerChunkCache#getChunk` hops to the main thread and blocks.
- **Forced loading:**
  - NeoForge `TicketController(ResourceLocation, @Nullable LoadingValidationCallback)`, registered in `RegisterTicketControllersEvent` (mod bus), with `forceChunk(ServerLevel, BlockPos|Entity|UUID owner, x, z, add, ticking)`. Tickets persist and are validated on load.
  - Vanilla temporary tickets: `ServerChunkCache#addRegionTicket(TicketType.create(name, cmp, lifespan), …)`.
  - Recommendation: don't force-load chunks for atmospherics. Freeze zones that touch non-ticking chunks, using `ServerLevel#shouldTickBlocksAt(long)` and `getChunkNow`.
- **How performance mods do it** (general knowledge, not checked per mod): own executors working on immutable snapshots, flags precomputed per state, and results applied on the main thread within a time budget.

## 6. Capabilities

- **Defining a capability:** `BlockCapability.create / createSided / createVoid(ResourceLocation, Class[, Class])` creates the capability or returns the existing one with that name. The definition must therefore live in the API jar. `EntityCapability` and `ItemCapability` work the same way.
- **Registering providers:** in `RegisterCapabilitiesEvent` (mod bus) via `registerBlockEntity`, `registerBlock`, `registerEntity` and `registerItem`.
- **Querying:** `level.getCapability(cap, pos, side)`, `stack.getCapability(cap)` or `entity.getCapability(cap, ctx)` ([docs](https://docs.neoforged.net/docs/1.21.1/inventories/capabilities/)).
- **Caching:** `BlockCapabilityCache.create(cap, ServerLevel, pos, ctx[, isValid, listener])` suits vents and pipes. Providers must call `level.invalidateCapabilities(pos)` whenever a capability appears, changes or disappears. Item and entity capabilities cannot be cached, so throttle those checks.
- **Proxyable flag (21.1.169+):** the owning mod calls `setProxyable` or `setNonProxyable` at HIGH priority. A gas handler that represents "the zone at this position" should be **non-proxyable**.
- **Examples:**
  - `GAS = BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("zas","gas_handler"), IGasHandler.class)`
  - `LIFE_SUPPORT = ItemCapability.createVoid(..., ILifeSupport.class)`
- **Life-support lookup:**
  - Armor: `getItemBySlot(EquipmentSlot)`.
  - Curios 9.5.1+1.21.1: `CuriosApi.getCuriosInventory(LivingEntity)` returns `Optional<ICuriosItemHandler>`; the API artifact is `top.theillusivec4.curios:curios-neoforge:<ver>:api` from `maven.theillusivec4.top`.
  - Recompute on `LivingEquipmentChangeEvent` and every N ticks.
  - Keep suit tanks in **item data components**.

## 7. Registries

([docs](https://docs.neoforged.net/docs/1.21.1/concepts/registries/))

- **Static registry:** `new RegistryBuilder<>(KEY).sync(true).defaultKey(..).maxId(..).create()`, added in `NewRegistryEvent#register`; entries via `DeferredRegister.create(KEY, MODID)`. Frozen at startup; `sync(true)` syncs IDs to clients. Tags work, with NeoForge prefixing the registry namespace: `data/<ns>/tags/zas/gas/…`.
- **Datapack registry:**
  - Register with `DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec[, networkCodec[, Consumer<RegistryBuilder>]])`. The 4-argument form exists since 21.1.58.
  - Entries load at world start. Clients receive them only if `networkCodec` is non-null.
  - `/reload` does not reload the entries, only their tags.
- **Data maps** ([docs](https://docs.neoforged.net/docs/1.21.1/resources/server/datamaps/)):
  - Build with `DataMapType.builder(id, registryKey, codec).synced(netCodec, mandatory).build()` and register in `RegisterDataMapTypesEvent`.
  - They work on static and datapack registries, reload with `/reload`, and support merge/remove. Read them with `holder.getData(type)`.
  - Before 21.1.155, data maps on synced datapack registries were not sent to in-memory (singleplayer) connections.
- **Recommended mapping:**
  - `GasType` → static registry (behavior in code, stable network IDs).
  - Reactions → datapack registry, or `AddReloadListenerEvent` + `OnDatapackSyncEvent` if `/reload` support matters.
  - Ambient atmosphere → keyed by the `ResourceKey<Level>` **dimension id, not the dimension type**, plus the API hook.
  - Per-block gas properties → block tags or a block data map.

## 8. Networking

([docs](https://docs.neoforged.net/docs/1.21.1/networking/payload/))

- **Registration:**
  - In `RegisterPayloadHandlersEvent` (mod bus): `event.registrar("1")` then `.playToClient(TYPE, STREAM_CODEC, handler)`. Also available: `playToServer`, `playBidirectional`, `configuration*`, `common*`.
  - Modifiers: `.versioned()`, `.optional()`, `.executesOn(HandlerThread.NETWORK)`. The default is MAIN, which wraps the handler so it runs on the main thread.
- **Payload shape:** `record X(...) implements CustomPacketPayload` with `static final Type<X> TYPE` and `StreamCodec<RegistryFriendlyByteBuf, X>` (`StreamCodec.composite`, `ByteBufCodecs`).
- **Sending:** `PacketDistributor.sendToPlayer`, `sendToPlayersTrackingChunk(ServerLevel, ChunkPos, …)`, `sendToPlayersNear`, `sendToPlayersTrackingEntity[AndSelf]`, `sendToPlayersInDimension`, `sendToAllPlayers`.
- **Pattern:**
  - Chunk summaries (zone id and pressure bucket for overlays) as a synced chunk attachment, or sent on `ChunkWatchEvent.Sent`, then deltas via `sendToPlayersTrackingChunk`.
  - HUD state as a synced player attachment sent only to that player, at 4 Hz or less.
  - Flow vectors only to nearby players.
- **Bandwidth:**
  - Quantize values (varints, byte fractions), coalesce dirty zones per tick, and avoid per-block packets.
  - Vanilla limits are `ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE` (about 1 MiB) and about 32 KiB serverbound.
  - NeoForge's `GenericPacketSplitter` exists, but 21.1.260–262 tightened size and decoding limits, so stay well under them.

## 9. Entities

- **`LivingBreatheEvent`** is not cancellable. It offers `setCanBreathe`, `setConsumeAirAmount` and `setRefillAirAmount`, and fires on **every** `LivingEntity#baseTick` for living entities, through `CommonHooks.onLivingBreathe`, **on both logical sides**.
- **`LivingDrownEvent`** is cancellable. It offers `setDrowning`, `setDamageAmount` and `setBubbleCount`, and fires when air is ≤0; vanilla damage is `damageSources().drown()`.
- **Plan:** the server decides from zone gas, cancels the drown event and applies our own damage type. Mirror the decision client-side from synced player state, or the client keeps refilling air and the bar jitters.
- **Damage types** are a datapack registry: `ResourceKey.create(Registries.DAMAGE_TYPE, rl)` plus `data/<ns>/damage_type/<id>.json`, generated with `DatapackBuiltinEntriesProvider`. Build the source with `new DamageSource(registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KEY))` and apply it with `entity.hurt(src, amt)`. Use tags such as `minecraft:bypasses_armor` ([docs](https://docs.neoforged.net/docs/1.21.1/resources/server/damagetypes/)).
- **Also useful:** `LivingIncomingDamageEvent`, `LivingDamageEvent.Pre/Post`, and `MobEffectEvent.Applicable/Added/Expired`. Effects are `Holder<MobEffect>`, registered with `DeferredRegister<MobEffect>`.
- **Motion:** `push(x,y,z)`, `addDeltaMovement(Vec3)`, `setDeltaMovement`. Mobs and items are server-simulated, so this just works. Players are client-authoritative: setting `hurtMarked = true` makes vanilla `ServerEntity` send `ClientboundSetEntityMotionPacket`, fine for one-off decompression impulses. For continuous flow, sync a flow field and also apply it client-side (like water currents) to avoid rubber-banding.
- **Armor:** in 1.21.1 armor materials are a registry (`Registries.ARMOR_MATERIAL`, `Holder<ArmorMaterial>`).

## 10. Fire & furnaces

- **Available events:** none for fire spread, ignition or burning. `FurnaceFuelBurnTimeEvent` has no level or position, and `BlockEvent.FluidPlaceBlockEvent` only covers lava-lit fire. The `IBlockExtension` flammability hooks only affect our own blocks.
- **Mixin targets** (NeoForge-patched code):
  - `FireBlock#tick(BlockState, ServerLevel, BlockPos, RandomSource)` at HEAD, to extinguish fire.
  - `BaseFireBlock#canBePlacedAt(Level, BlockPos, Direction)` (flint and steel, fire charges).
  - NeoForge replaces vanilla's spread logic with `checkBurnOut(Level, BlockPos, int, RandomSource, int, Direction)` and `canCatchFire(BlockGetter, BlockPos, Direction)`. Target those, not vanilla's 5-argument overload.
  - `SoulFireBlock` doesn't tick, so handle it by scanning zones.
  - `AbstractFurnaceBlockEntity#serverTick(Level, BlockPos, BlockState, AbstractFurnaceBlockEntity)` (static); `litTime` needs an accessor.
  - `CampfireBlockEntity#cookTick(...)`, or set `CampfireBlock.LIT` to false (as `CampfireBlock#dowse` does) and gate relighting in `CampfireBlock#canLight`. Candles use `LIT` as well.
  - Torches have no lit state, so use a custom extinguished torch or ignore them.
- **Consumption accounting** needs no mixins: count fire blocks and `LIT=true` states per zone through the block-change hook.

## 11. Client

- **HUD:** in `RegisterGuiLayersEvent` (mod bus), call `registerAbove(VanillaGuiLayers.AIR_LEVEL, id, LayeredDraw.Layer)`. Also available: `registerBelow`, `registerAboveAll`, `replaceLayer`, and `wrapLayer` (21.1.190+). `RenderGuiLayerEvent.Pre` can hide vanilla layers.
- **World overlays:** in `RenderLevelStageEvent`, compare `getStage()` with `Stage.AFTER_TRANSLUCENT_BLOCKS` (or `AFTER_PARTICLES` / `AFTER_LEVEL`). Draw with `getPoseStack()` offset by the camera, `RenderType.lines()` and `LevelRenderer.renderLineBox(...)`. By 1.21.8 this became one subclass per stage, and the box helpers moved to `ShapeRenderer` from 1.21.2.
- **Particles:** register the type with `Registries.PARTICLE_TYPE`, providers in `RegisterParticleProvidersEvent`, and spawn with `ServerLevel#sendParticles`.
- **Client entrypoint:** a class annotated `@Mod(value=MODID, dist=Dist.CLIENT)`. `@EventBusSubscriber` no longer needs `bus` since 21.1.181.
- **Client config:** `ModConfig.Type.CLIENT`, with the screen registered via `registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)`.

## 12. Config

([docs](https://docs.neoforged.net/docs/1.21.1/misc/config/))

- **API:** `ModConfigSpec.Builder` with `define`, `defineInRange`, `defineEnum`, `defineListAllowEmpty`, `comment`, `translation`, `worldRestart()`/`gameRestart()`. Register with `modContainer.registerConfig(Type, SPEC)`. React to changes in `ModConfigEvent.Loading/Reloading`.
- **Types:**
  - **STARTUP:** read at registration (mod construction) and never synced. It is *not* usable for skipping mixins. Vanilla `Bootstrap.bootStrap()` (called from `Main`) loads block classes such as `FireBlock` before mods are constructed, so check toggles at runtime inside the injected code instead.
  - **CLIENT:** physical client only.
  - **COMMON:** both sides, not synced.
  - **SERVER:** stored per world in `serverconfig/`, loaded before `ServerAboutToStartEvent`, synced to clients. Modpack defaults come from `defaultconfigs/`.
- **Placement for zas-cubed:** simulation tuning → SERVER; HUD and overlays → CLIENT; thread counts → COMMON.

## 13. Testing & CI

- **GameTest** (1.21.1 still uses the annotation API; it was rewritten in 1.21.5) ([docs](https://docs.neoforged.net/docs/1.21.1/misc/gametest/)):
  - Write `@GameTest` methods taking a `GameTestHelper`, registered via `@GameTestHolder(MODID)` or `RegisterGameTestsEvent`.
  - Templates live at `data/<ns>/structure/<class>.<method>.nbt`; use `@PrefixGameTestTemplate(false)` or `template=` to change the name.
  - Run headless with `./gradlew runGameTestServer` (MDG run `type = "gameTestServer"`). The exit code is the number of failed required tests.
  - Set the `neoforge.enabledGameTestNamespaces` property.
  - `net.neoforged:testframework:21.1.x` adds `@EmptyTemplate`, but only within its own framework.
- **JUnit:**
  - `:core` uses plain JUnit 5.
  - Tests that need Minecraft use MDG `unitTest`, `testImplementation "net.neoforged:testframework:${neo_version}"`, and `@ExtendWith(EphemeralTestServerProvider.class)`, which injects a lazily started singleton `MinecraftServer`.
- **CI:** the MDK workflow is `actions/checkout@v4` (`fetch-depth: 0`), `actions/setup-java@v4` (temurin 21), `gradle/actions/setup-gradle@v4`, `./gradlew build`. Add `./gradlew runGameTestServer` (NeoForge's own CI runs gametests headless on Ubuntu; client tests need `xvfb-run`), upload reports, and add an API binary-compatibility check (e.g. japicmp).

## 14. Mixins & ATs

- **Bundled versions:** NeoForge 21.1.265 bundles Mixin `0.16.4+mixin.0.8.7` and **MixinExtras 0.5.5**; don't bundle your own copy.
- **Declaring mixins:** declare them only in `neoforge.mods.toml` with `[[mixins]] config="zas.mixins.json"`.
  - FML 1.21.1 supports `requiredMods=["mekanism"]` for conditional mixins (backported in 21.1.97). `behaviorVersion` is not available on 1.21.1.
  - The manifest `MixinConfigs` attribute is ignored.
  - No refmap is needed, because Mojang names are used at runtime.
- **Mixin config JSON:** `"required": true, "minVersion": "0.8", "compatibilityLevel": "JAVA_21", "injectors": {"defaultRequire": 1}`, with client-only targets listed under `"client"`.
- **Pitfalls:** target NeoForge-patched code (the `FireBlock` overloads; `Level#setBlock` is split into `markAndNotifyBlock`). Prefer `@Inject` or MixinExtras (`@WrapOperation`, `@ModifyExpressionValue`, `@WrapWithCondition`) over `@Redirect`/`@Overwrite`. Mark added members `@Unique` with a `zas$` prefix, keep hot-path injections trivial, and never load client classes from common mixins.
- **Access transformers:** `META-INF/accesstransformer.cfg` is picked up automatically; enable `validateAccessTransformers` ([docs](https://docs.neoforged.net/docs/1.21.1/advanced/accesstransformers/)). Since we ship mixins anyway, prefer `@Accessor`/`@Invoker`.
- **Etiquette:** an event or extension hook first, a mixin only when none exists. Scope behavior changes to zones and make them configurable, document mixin targets for pack makers, and render through NeoForge events rather than `LevelRenderer` mixins (Sodium/Iris compatibility).

## 1.21.2+ changes we must not rely on

| 1.21.1 (use) | Later |
|---|---|
| `registryAccess().registryOrThrow(K).getHolderOrThrow(k)` | `lookupOrThrow` / `getOrThrow` (1.21.2) |
| `Entity#hurt(DamageSource, float)` | `hurtServer(ServerLevel, …)` (1.21.2) |
| `getOcclusionShape(level,pos)`, `getLightBlock(level,pos)`, `getFaceOcclusionShape(level,pos,dir)` | lose level/pos parameters; `Shapes.getFaceShape` removed (1.21.2) |
| `neighborChanged(…, BlockPos fromPos, …)` | takes `Orientation` (1.21.2) |
| `Registries.ARMOR_MATERIAL`, `Holder<ArmorMaterial>` | `ArmorMaterial` record + `DataComponents.EQUIPPABLE` (1.21.2) |
| `LevelRenderer.renderLineBox` | `ShapeRenderer` (1.21.2) |
| `Shapes.blockOccudes` | `blockOccludes` (1.21.5) |
| `LevelChunk#setBlockState(…, boolean)`, `Level#onBlockStateChange` | `(…, int)`, `updatePOIOnBlockStateChange` (1.21.5) |
| `BlockBehaviour#onRemove` | `affectNeighborsAfterRemoval` + `BlockEntity#preRemoveSideEffects` (1.21.5) |
| `SavedData.Factory` + `save(CompoundTag, …)` | `SavedDataType` (1.21.5) |
| Annotation GameTests | test instances / `TestEnvironmentDefinition` (1.21.5) |
| `RenderLevelStageEvent` + `Stage` | subclass per stage (by 1.21.8) |
| `IItemHandler` / `IFluidHandler` | `ResourceHandler` transfer API (later; present on the 1.21.11 branch) |

## Open questions / uncertainty

- Whether MDG accepts a non-MDG `:core` source set in `mods {}` (verified only for MDG subprojects). Prototype early.
- The exact vanilla `dynamicShape()` list, and powder-snow/scaffolding collision under `CollisionContext.empty()`. Dump at runtime.
- WorldEdit on its 1.21.1 releases (I checked the current branch), plus other bulk editors (Axiom, Create schematics).
- Client air-bar jitter without breathability sync is reasoned, not tested.
- Modded block-state counts are an estimate; measure in a large pack.
- Which thread runs `ChunkDataEvent.Load` and chunk deserialization in 1.21.1: NeoForge's javadoc says "async"; vanilla not traced.
- Gradle newer than 9.2.1 with MDG 2.0.148: likely fine, untested.
