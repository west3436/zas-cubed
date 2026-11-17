package io.github.west3436.zas.api.capability;

/**
 * A gas handler that is a view of another kind of storage, translated: another mod's chemical tank
 * or fluid tank as ZAS³ gas (MEK-013, COMPAT-141). It holds nothing of its own; each call reads or
 * changes the storage it is a view of.
 *
 * <p><b>Never a view of a view.</b> An adapter that offers a block's gas handler to other mods as
 * some other capability, as the Mekanism adapter offers it as a chemical handler and the content
 * mod as a fluid handler, offers nothing for a handler that implements this. Otherwise a fluid
 * pipe could reach a chemical tank through two translations, turning one into the other with
 * nothing in between. A handler that isn't a view of something else shouldn't implement it.
 *
 * <p>It names nothing, so an adapter can implement it whatever it translates.
 *
 * @since 1.2.0
 */
public interface GasHandlerView extends IGasHandler {}
