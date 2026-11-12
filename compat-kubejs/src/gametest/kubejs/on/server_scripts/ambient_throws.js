// A pack's script whose ambient provider is broken (NFR-013): it throws whenever a test says so,
// or answers nothing at all.
const BrokenAmbientProbe = Java.loadClass('io.github.west3436.zas.compat.kubejs.gametest.Probe')

AmbientProviders.register('zas_kubejs:nether_air', 'minecraft:the_nether', level => {
  BrokenAmbientProbe.asked('nether_air', level, 'minecraft:the_nether')
  const mode = String(BrokenAmbientProbe.mode('nether'))
  if (mode == 'throw') {
    throw new Error('the ambient script is broken')
  }
  if (mode == 'forgot') {
    return
  }
  return AmbientDefinition.vacuum(mode == 'hot' ? 900 : 737)
})

BrokenAmbientProbe.loaded('ambient_throws.js')
