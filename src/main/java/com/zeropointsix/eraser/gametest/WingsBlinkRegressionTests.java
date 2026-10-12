package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("wings_blink_tests")
@PrefixGameTestTemplate(false)
public final class WingsBlinkRegressionTests {
    @GameTest(templateNamespace = ModMain.MOD_ID, template = "brickrot_empty", timeoutTicks = 40)
    public static void forwardEndpoint(GameTestHelper h) {
        WingsBlinkGameTests.blinkSharedSegmentEndpointStopsAtWall(h);
    }

    @GameTest(templateNamespace = ModMain.MOD_ID, template = "brickrot_empty", timeoutTicks = 40)
    public static void reverseEndpoint(GameTestHelper h) {
        WingsBlinkGameTests.blinkReverseSharedSegmentEndpointStopsAtWall(h);
    }
}
