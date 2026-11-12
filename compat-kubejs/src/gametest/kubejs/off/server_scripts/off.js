// A pack's script on a server whose KubeJS is older than the adapter supports (COMPAT-002).
// KubeJS still runs it, and nothing of ZAS³'s is there for it to name.
const OffProbe = Java.loadClass('io.github.west3436.zas.compat.kubejs.gametest.Probe')

OffProbe.note('off.ZasEvents', typeof ZasEvents)
OffProbe.note('off.Atmospheres', typeof Atmospheres)
OffProbe.note('off.GasMutations', typeof GasMutations)
OffProbe.note('off.HazardHandlers', typeof HazardHandlers)
OffProbe.note('off.AmbientProviders', typeof AmbientProviders)

OffProbe.loaded('off.js')
