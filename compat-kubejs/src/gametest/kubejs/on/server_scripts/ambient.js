// A pack's script that gives a dimension its open air (COMPAT-053). The provider answers in each
// of the ways a script can, as a test asks.
const AmbientProbe = Java.loadClass('io.github.west3436.zas.compat.kubejs.gametest.Probe')

// The End's air, with a priority.
AmbientProviders.register('zas_kubejs:end_air', 'minecraft:the_end', 5, level => {
  AmbientProbe.asked('end_air', level, 'minecraft:the_end')
  const mode = String(AmbientProbe.mode('ambient'))
  if (mode == 'object') {
    return { partialPressures: { 'zas:oxygen': 30, 'zas:nitrogen': 20 }, temperature: 250 }
  }
  if (mode == 'vacuum') {
    return AmbientDefinition.vacuum(120)
  }
  if (mode == 'nothing') {
    return null
  }
  return new AmbientDefinition({ 'zas:oxygen': 18, 'zas:nitrogen': 60 }, 280)
})

// One with the default priority, which is not asked while the other is there.
AmbientProviders.register('zas_kubejs:end_fallback', 'minecraft:the_end', level => {
  AmbientProbe.asked('end_fallback', level, 'minecraft:the_end')
  return AmbientDefinition.standardAir(300)
})

AmbientProbe.loaded('ambient.js')
