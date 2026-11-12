// A pack's script that calls each of ZAS³'s entry points (COMPAT-051) when a test asks, and notes
// what came back. Everything is written as a script would write it: ids as text, a point as a
// block position or [x, y, z], a parcel of gas as an object.
const CallProbe = Java.loadClass('io.github.west3436.zas.compat.kubejs.gametest.Probe')

// The readings: answered at once.
CallProbe.offer('read', (level, inside, open, entity) => {
  const note = (key, value) => CallProbe.note('read.' + key, value)

  const sample = Atmospheres.at(level, inside)
  note('state', sample.state())
  note('pressure', sample.pressure())
  note('temperature', sample.temperature())
  note('oxygen', sample.mixture().partialPressure('zas:oxygen'))
  note('zoned', sample.zone().isPresent())
  note('open.state', Atmospheres.at(level, open).state())
  const middle = new Vec3d(inside.x + 0.5, inside.y + 0.5, inside.z + 0.5)
  note('point.state', Atmospheres.at(level, middle).state())
  note('array.state', Atmospheres.at(level, [inside.x + 0.5, inside.y + 0.5, inside.z + 0.5]).state())
  note('array.open.state', Atmospheres.at(level, [open.x, open.y, open.z]).state())
  note('entity.pressure', Atmospheres.of(entity).pressure())
  try {
    Atmospheres.at(level, 'here')
  } catch (error) {
    note('text', String(error))
  }

  const zone = Atmospheres.zoneAt(level, inside).get()
  note('zone.volume', zone.volume())
  note('zone.enclosed', zone.enclosed())
  note('zone.same', Atmospheres.zone(level, zone.id()).get().id().equals(sample.zone().get()))
  note('zone.point', Atmospheres.zoneAt(level, middle).get().id().equals(zone.id()))
  note('zone.open', Atmospheres.zoneAt(level, open).isPresent())
  note('zoneTouches', Atmospheres.zoneTouches(level, inside))
  note('periodTicks', Atmospheres.periodTicks())
  note('preset', Atmospheres.preset())
  note('nearVacuumDensity', Atmospheres.nearVacuumDensity())
  note('containerExplosionsBreakBlocks', Atmospheres.containerExplosionsBreakBlocks())

  const verdict = Breathing.of(entity)
  note('breathing.canBreathe', verdict.canBreathe())
  note('breathing.reason', verdict.reason())
  note('breathing.source', verdict.source())
  note('breathing.vanilla', Breathing.vanilla(entity).canBreathe())
  note('breathing.oxygenMinimum', Breathing.oxygenMinimum())

  note('fires.canBurnIn', Fires.canBurnIn(sample.mixture()))
  note('fires.isBurning', Fires.isBurning(level, zone.id()))
  note('fires.hotspots', Fires.hotspots(level, zone.id()).size())
  note('fires.isHotspot', Fires.isHotspot(level, inside))
  note('fires.extinguish', Fires.extinguish(level, inside, inside))
})

// The changes: each answers at the atmos tick that makes it.
CallProbe.offer('change', (level, inside, open, entity) => {
  const note = (key, value) => CallProbe.note('change.' + key, value)
  const source = 'zas_kubejs:calls'

  GasMutations.add(level, inside, new GasParcel({ 'zas:carbon_dioxide': 3 }, 293.15), source).thenAccept(result => {
    note('add.outcome', result.outcome())
    note('add.moved', result.moved().moles('zas:carbon_dioxide'))
  })
  GasMutations.add(level, [inside.x + 0.5, inside.y + 0.5, inside.z + 0.5], { moles: { 'zas:nitrogen': 2 }, temperature: 293.15 }, source)
    .thenAccept(result => note('add.object.moved', result.moved().moles('zas:nitrogen')))
  GasMutations.remove(level, inside, { 'zas:carbon_dioxide': 1 }, source)
    .thenAccept(result => note('remove.moved', result.moved().moles('zas:carbon_dioxide')))

  HeatMutations.add(level, inside, 5000, source).thenAccept(result => {
    note('heat.add.outcome', result.outcome())
    note('heat.add.joules', result.joules())
  })
  HeatMutations.remove(level, inside, 2000, source).thenAccept(result => note('heat.remove.joules', result.joules()))

  ZonePins.pin(level, inside, source).thenAccept(result => {
    note('pin.outcome', result.outcome())
    note('pin.zoned', result.zone().isPresent())
  })
  ZonePins.pin(level, open, 'zas_kubejs:calls_open').thenAccept(result => note('pin.open.outcome', result.outcome()))

  Fires.ignite(level, inside, source).thenAccept(result => {
    note('ignite.outcome', result.outcome())
    note('ignite.burning', result.burning())
  })
})

// The end of the changes.
CallProbe.offer('unpin', (level, inside, open, entity) => {
  ZonePins.unpin(level, 'zas_kubejs:calls').thenAccept(() => CallProbe.note('unpin.done', true))
  ZonePins.unpin(level, 'zas_kubejs:calls_open')
})

// A change asked for from another thread.
CallProbe.offer('offThread', (level, inside, open, entity) => {
  GasMutations.add(level, inside, new GasParcel({ 'zas:oxygen': 1 }, 293.15), 'zas_kubejs:calls')
})

CallProbe.loaded('calls.js')
