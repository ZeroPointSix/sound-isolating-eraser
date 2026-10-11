package com.zeropointsix.eraser.yuji;

import java.util.Locale;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

public enum YujiKind {
    YUCHI(30, 0, .18, 2, .6F, 1.95F, 0x96AAB6, 0xB0B6A8),
    SHASHENG(20, 3, .30, 2, .6F, 1.95F, 0x806656, 0x8E2020),
    TOUDAO(12, 1, .42, 0, .5F, .7F, 0xD6D0C4, 0x101012),
    EKOU(24, 0, .25, 2, .6F, 1.95F, 0x888A88, 0x6E141A),
    WANGYU(20, 3, .23, 0, .6F, 2.1F, 0xC6BAA8, 0x96505A),
    LIANGSHE(16, 2, .23, 0, .6F, 1.6F, 0xB4A696, 0xB04654),
    XIEYIN(20, 0, .20, 0, .6F, 2.1F, 0xE2DCD6, 0x141216),
    QIYU(20, 4, .25, 0, .6F, 2.3F, 0xCEC8C4, 0x141216),
    TANYU(38, 5, .25, 4, .9F, 2.7F, 0x84827E, 0x3A3634),
    CHENNU(500, 20, .38, 15, 4.5F, 6, 0x18161A, 0xD22818),
    SHIFOTOU(2000, 50, .30, 30, 6, 8, 0x5C5C60, 0x3A1830);

    public final double health, damage, speed, armor;
    public final float width, height;
    public final int eggBase, eggSpot;
    YujiKind(double health, double damage, double speed, double armor, float width, float height,
             int eggBase, int eggSpot) {
        this.health = health; this.damage = damage; this.speed = speed; this.armor = armor;
        this.width = width; this.height = height; this.eggBase = eggBase; this.eggSpot = eggSpot;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public boolean giant() { return this == CHENNU || this == SHIFOTOU; }
    public AttributeSupplier attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ATTACK_DAMAGE, damage).add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ARMOR, armor).add(Attributes.FOLLOW_RANGE, this == CHENNU ? 96 : 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, giant() ? 1 : this == SHASHENG ? .3 : 0).build();
    }
}
