package io.github.west3436.zas.api.life;

/**
 * What an item worn in an extra equipment slot counts as ({@link ExtraSlots}).
 *
 * @since 1.1.0
 */
public enum ExtraSlotKind {
    /**
     * A slot on the head, such as one for a hat or a mask. An item with life support in it counts
     * as the head piece for {@link io.github.west3436.zas.api.capability.ILifeSupport#sealedBreathing()
     * sealed breathing} and {@link io.github.west3436.zas.api.capability.ILifeSupport#gasFiltering()
     * gas filtering}, beside the one in the armor head slot. It covers none of the body, so it adds
     * no pressure or thermal protection.
     */
    HEAD,

    /** Any other slot, such as one on the back or the belt. Only an internal supply counts in it. */
    OTHER
}
