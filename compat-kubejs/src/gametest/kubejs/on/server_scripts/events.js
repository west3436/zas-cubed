// A pack's script that listens for each of ZAS³'s events (COMPAT-050), and tells the tests what
// each listener was given. The breach is also written to the server's log, as a pack would.
const EventProbe = Java.loadClass('io.github.west3436.zas.compat.kubejs.gametest.Probe')

ZasEvents.zoneCreated(event => EventProbe.zone('zoneCreated', event.level, event.zone, event.pos, event.server))

ZasEvents.zoneBreached(event => {
  console.info('Zone ' + event.zone.value() + ' was breached at ' + event.pos.toShortString())
  EventProbe.zone('zoneBreached', event.level, event.zone, event.pos, event.server)
})

ZasEvents.zoneSealed(event => EventProbe.zone('zoneSealed', event.level, event.zone, event.pos, event.server))
ZasEvents.zoneDissolved(event => EventProbe.zone('zoneDissolved', event.level, event.zone, event.pos, event.server))
ZasEvents.zonePaused(event => EventProbe.zone('zonePaused', event.level, event.zone, event.pos, event.server))
ZasEvents.zoneResumed(event => EventProbe.zone('zoneResumed', event.level, event.zone, event.pos, event.server))
ZasEvents.zoneMerged(event => EventProbe.merged(event.level, event.zone, event.pos, event.absorbed))
ZasEvents.zoneSplit(event => EventProbe.split(event.level, event.zone, event.pos, event.remnant))

ZasEvents.fireIgnited(event => EventProbe.fire('fireIgnited', event.level, event.zone, event.hotspot))
ZasEvents.fireExtinguished(event => EventProbe.fire('fireExtinguished', event.level, event.zone, event.lastHotspot))

ZasEvents.openAirGas(event => EventProbe.openAir(event.level, event.pos, event.added, event.gas, event.source))

EventProbe.loaded('events.js')
