package com.zeropointsix.eraser.suxia;

/** Common-side animation contract: dedicated servers must not load client model classes. */
public interface SuxiaPoseSource {
    float suxiaCrawlAmount(float partialTick);

    default float suxiaPlayDeadAmount(float partialTick) { return 0F; }
}
