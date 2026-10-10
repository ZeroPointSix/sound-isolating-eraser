package com.zeropointsix.eraser.wings;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class WingsConfig {
    private WingsConfig() {}

    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;
    static {
        Pair<Server, ForgeConfigSpec> p = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = p.getLeft();
        SERVER_SPEC = p.getRight();
    }

    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;
    static {
        Pair<Client, ForgeConfigSpec> p = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = p.getLeft();
        CLIENT_SPEC = p.getRight();
    }

    public static class Server {
        public final ForgeConfigSpec.DoubleValue hoverMaxSpeed;
        public final ForgeConfigSpec.DoubleValue cruiseSpeed;
        public final ForgeConfigSpec.DoubleValue boostSpeed;
        public final ForgeConfigSpec.DoubleValue stormSpeed;
        public final ForgeConfigSpec.DoubleValue accelLerp;

        // Exhaustion per second of flight by tier (4 exhaustion ≈ 1 hunger point).
        public final ForgeConfigSpec.DoubleValue hoverExhaustPerSec;
        public final ForgeConfigSpec.DoubleValue cruiseExhaustPerSec;
        public final ForgeConfigSpec.DoubleValue boostExhaustPerSec;
        public final ForgeConfigSpec.DoubleValue stormExhaustPerSec;
        public final ForgeConfigSpec.IntValue minFoodToDeploy;
        public final ForgeConfigSpec.IntValue minFoodForFastTiers;

        public final ForgeConfigSpec.DoubleValue blinkDistance;
        public final ForgeConfigSpec.DoubleValue blinkExhaust;
        public final ForgeConfigSpec.IntValue blinkChainWindowTicks;
        public final ForgeConfigSpec.IntValue blinkChainMax;
        public final ForgeConfigSpec.IntValue blinkCooldownTicks;
        public final ForgeConfigSpec.IntValue blinkChainCooldownTicks;
        public final ForgeConfigSpec.BooleanValue blinkDamageEnabled;
        public final ForgeConfigSpec.DoubleValue blinkDamage;

        public final ForgeConfigSpec.BooleanValue wallDamageEnabled;
        public final ForgeConfigSpec.DoubleValue wallDamageThresholdSpeed;
        public final ForgeConfigSpec.DoubleValue wallDamageCap;
        public final ForgeConfigSpec.DoubleValue unloadedChunkSpeedCap;
        public final ForgeConfigSpec.IntValue voidPushMargin;
        public final ForgeConfigSpec.IntValue chargedDurationTicks;

        Server(ForgeConfigSpec.Builder b) {
            b.comment("风雷翅服务端数值（全部可配置）").push("flight");
            hoverMaxSpeed = b.comment("悬停档 WASD 平移上限 m/s")
                    .defineInRange("hoverMaxSpeed", 5.0, 0.5, 50.0);
            cruiseSpeed = b.comment("御风档巡航速度 m/s")
                    .defineInRange("cruiseSpeed", 30.0, 1.0, 500.0);
            boostSpeed = b.comment("疾风档速度 m/s")
                    .defineInRange("boostSpeed", 60.0, 1.0, 500.0);
            stormSpeed = b.comment("风雷档速度 m/s（服务器可调小防爆图）")
                    .defineInRange("stormSpeed", 120.0, 1.0, 500.0);
            accelLerp = b.comment("每秒向目标速度趋近的比例（lerp）")
                    .defineInRange("accelLerp", 2.5, 0.05, 20.0);
            unloadedChunkSpeedCap = b.comment("前方区块未加载时自动限速 m/s")
                    .defineInRange("unloadedChunkSpeedCap", 30.0, 1.0, 200.0);
            voidPushMargin = b.comment("低于世界底+此值时强制向上飞")
                    .defineInRange("voidPushMargin", 5, 0, 64);
            b.pop();

            b.push("hunger");
            hoverExhaustPerSec = b.defineInRange("hoverExhaustPerSec", 0.4, 0.0, 40.0);
            cruiseExhaustPerSec = b.defineInRange("cruiseExhaustPerSec", 0.8, 0.0, 40.0);
            boostExhaustPerSec = b.defineInRange("boostExhaustPerSec", 2.0, 0.0, 40.0);
            stormExhaustPerSec = b.defineInRange("stormExhaustPerSec", 4.0, 0.0, 40.0);
            minFoodToDeploy = b.comment("饥饿低于此值无法展开风雷翅")
                    .defineInRange("minFoodToDeploy", 4, 0, 20);
            minFoodForFastTiers = b.comment("饥饿低于此值只允许悬停/御风档")
                    .defineInRange("minFoodForFastTiers", 6, 0, 20);
            b.pop();

            b.push("blink");
            blinkDistance = b.comment("雷遁瞬移距离（格）")
                    .defineInRange("blinkDistance", 24.0, 4.0, 64.0);
            blinkExhaust = b.comment("每次雷遁消耗的 exhaustion（4≈1 饥饿）")
                    .defineInRange("blinkExhaust", 6.0, 0.0, 40.0);
            blinkChainWindowTicks = b.comment("连闪窗口 tick（默认 0.6s）")
                    .defineInRange("blinkChainWindowTicks", 12, 1, 100);
            blinkChainMax = b.defineInRange("blinkChainMax", 3, 1, 10);
            blinkCooldownTicks = b.comment("单次雷遁冷却 tick（默认 2s）")
                    .defineInRange("blinkCooldownTicks", 40, 1, 200);
            blinkChainCooldownTicks = b.comment("连闪打满后的长冷却 tick（默认 8s）")
                    .defineInRange("blinkChainCooldownTicks", 160, 1, 1200);
            blinkDamageEnabled = b.comment("雷遁落点伤害（默认关闭：遁术非武器）")
                    .define("blinkDamageEnabled", false);
            blinkDamage = b.defineInRange("blinkDamage", 4.0, 0.0, 40.0);
            b.pop();

            b.push("safety");
            wallDamageEnabled = b.define("wallDamageEnabled", true);
            wallDamageThresholdSpeed = b.comment("撞墙伤害的速度阈值 m/s")
                    .defineInRange("wallDamageThresholdSpeed", 40.0, 5.0, 500.0);
            wallDamageCap = b.defineInRange("wallDamageCap", 10.0, 0.0, 100.0);
            chargedDurationTicks = b.comment("被雷劈中后「雷力充盈」时长 tick（默认 60s）")
                    .defineInRange("chargedDurationTicks", 1200, 0, 12000);
            b.pop();
        }

        public double tierSpeed(int tier) {
            return switch (tier) {
                case 0 -> hoverMaxSpeed.get();
                case 1 -> cruiseSpeed.get();
                case 2 -> boostSpeed.get();
                default -> stormSpeed.get();
            };
        }

        public double tierExhaustPerSec(int tier) {
            return switch (tier) {
                case 0 -> hoverExhaustPerSec.get();
                case 1 -> cruiseExhaustPerSec.get();
                case 2 -> boostExhaustPerSec.get();
                default -> stormExhaustPerSec.get();
            };
        }
    }

    public static class Client {
        public final ForgeConfigSpec.BooleanValue fovBoost;
        public final ForgeConfigSpec.DoubleValue fovBoostMax;
        public final ForgeConfigSpec.BooleanValue blinkFlash;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("客户端表现").push("render");
            fovBoost = b.comment("高速时 FOV 增宽").define("fovBoost", true);
            fovBoostMax = b.defineInRange("fovBoostMax", 15.0, 0.0, 30.0);
            blinkFlash = b.comment("雷遁白闪+轻震屏（光敏玩家可关）").define("blinkFlash", true);
            b.pop();
        }
    }
}
