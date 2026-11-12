// A pack's script that gives gases hazards of its own (COMPAT-052): one for a gas, one for a gas
// tag. Each tells the tests what it was called with.
const HazardProbe = Java.loadClass('io.github.west3436.zas.compat.kubejs.gametest.Probe')

// For one gas: hurts whoever breathes it, by the seconds the check charges for.
HazardHandlers.register('zas_kubejs:choking', 'zas_kubejs:script_gas', exposure => {
  HazardProbe.exposed('choking', exposure)
  if (exposure.breathedPressure() > 0) {
    exposure.entity().attack(1.5 * exposure.seconds())
  }
})

// For each gas in a tag.
HazardHandlers.register('zas_kubejs:tagged', '#zas_kubejs:scripted', exposure => HazardProbe.exposed('tagged', exposure))

// One the pack has only for as long as a test says, as a script that is edited between reloads.
if (String(HazardProbe.mode('extra')) != 'off') {
  HazardHandlers.register('zas_kubejs:extra', 'zas_kubejs:script_gas', exposure => HazardProbe.exposed('extra', exposure))
}

HazardProbe.loaded('hazard.js')
