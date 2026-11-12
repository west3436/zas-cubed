// A pack's script whose hazard handler is broken (NFR-013): it throws whenever a test says so,
// by a throw of its own or by a mistake in its code.
const BrokenHazardProbe = Java.loadClass('io.github.west3436.zas.compat.kubejs.gametest.Probe')

HazardHandlers.register('zas_kubejs:a_broken', 'zas_kubejs:faulty_gas', exposure => {
  BrokenHazardProbe.exposed('a_broken', exposure)
  const mode = String(BrokenHazardProbe.mode('hazard'))
  if (mode == 'throw') {
    throw new Error('the hazard script is broken')
  }
  if (mode == 'mistake') {
    exposure.entity().thereIsNoSuchMethod()
  }
})

// Registered for the same gas, and called after it.
HazardHandlers.register('zas_kubejs:b_working', 'zas_kubejs:faulty_gas', exposure => BrokenHazardProbe.exposed('b_working', exposure))

BrokenHazardProbe.loaded('hazard_throws.js')
