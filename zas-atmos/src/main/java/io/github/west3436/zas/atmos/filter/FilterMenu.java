package io.github.west3436.zas.atmos.filter;

import io.github.west3436.zas.atmos.inline.SidePortMenu;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * A filter's GUI (CONT-013, CONT-074): the base's, with what it can select. The server fixes that
 * list when the GUI opens and sends it to the client, and a data slot per entry says whether it is
 * on. The client's button sends only an entry's place in the list, which the server checks against
 * its own, so it can't select anything the list doesn't hold.
 */
public final class FilterMenu extends SidePortMenu {
    /** The first entry's button; the others follow it, in the order of {@link #options()}. */
    public static final int OPTION = LAST_BUTTON + 1;

    /** The list, over the network. */
    public static final StreamCodec<ByteBuf, List<FilterOption>> OPTIONS =
            FilterOption.STREAM_CODEC.apply(ByteBufCodecs.list());

    private final FilterBlockEntity filter;
    private final List<FilterOption> options;
    private final List<DataSlot> chosen = new ArrayList<>();

    /** The client's menu, with the list the server sent. */
    public FilterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(AtmosMenus.FILTER.get(), id, new SimpleContainerData(DATA_COUNT), DATA_COUNT, null);
        this.filter = null;
        this.options = OPTIONS.decode(buf);
        for (int i = 0; i < options.size(); i++) {
            chosen.add(addDataSlot(DataSlot.standalone()));
        }
    }

    /** The server's menu, on a filter. */
    public FilterMenu(int id, FilterBlockEntity filter) {
        super(AtmosMenus.FILTER.get(), id, filter.data(), DATA_COUNT, filter);
        this.filter = filter;
        this.options = filter.offered();
        for (FilterOption option : options) {
            chosen.add(addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return option.in(filter.selection()) ? 1 : 0;
                }

                @Override
                public void set(int value) {}
            }));
        }
    }

    /** What the GUI offers. */
    public List<FilterOption> options() {
        return options;
    }

    /** Whether an entry of {@link #options()} is on, as synced. */
    public boolean selected(int index) {
        return chosen.get(index).get() != 0;
    }

    @Override
    protected boolean deviceButton(int id) {
        int index = id - OPTION;
        if (index < 0 || index >= options.size()) {
            return false;
        }
        filter.setSelection(options.get(index).toggled(filter.selection()));
        return true;
    }
}
