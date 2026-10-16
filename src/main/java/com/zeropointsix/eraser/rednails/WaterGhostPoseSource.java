package com.zeropointsix.eraser.rednails;

public interface WaterGhostPoseSource {
    float waterGhostSwimAmount(float partialTick);
    float waterGhostGrabAmount(float partialTick);
    WaterGhostBody ghostBody();
}
