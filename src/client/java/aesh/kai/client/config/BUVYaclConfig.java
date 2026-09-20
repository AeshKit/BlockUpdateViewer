package aesh.kai.client.config;

import aesh.kai.client.BlockUpdateViewerClient;
import aesh.kai.client.log.BULogger;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;

public class BUVYaclConfig {

    public static Screen buildScreen(Screen parent) {
        BUVConfig config = BUVConfig.INSTANCE;
        boolean wasLoggingToFile = config.logToFile;

        return YetAnotherConfigLib.createBuilder()
                .title(getText("title"))
                .save(() -> {
                    BUVConfig.save();

                    if(!wasLoggingToFile && config.logToFile) {
                        if(Minecraft.getInstance().level != null)
                            BULogger.open(BlockUpdateViewerClient.resolveLogPath(Minecraft.getInstance()));
                        BULogger.startWriterThread();
                    } else if(wasLoggingToFile && !config.logToFile)
                        BULogger.stopWriterThread();
                })
                .category(ConfigCategory.createBuilder()
                        .name(getText("render"))

                        .option(Option.<Boolean>createBuilder()
                                .name(getText("option.renderingEnabled"))
                                .binding(true, () -> config.isRenderingEnabled, newVal -> config.isRenderingEnabled = newVal)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
//                        .option(Option.<BUVConfig.RenderMode>createBuilder()
//                                .name(getText("option.renderMode"))
//                                .description(OptionDescription.of(getText("option.renderMode.description")))
//                                .binding(BUVConfig.RenderMode.SOLID, () -> config.renderMode, newVal -> config.renderMode = newVal)
//                                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(BUVConfig.RenderMode.class).formatValue(BUVConfig.RenderMode::getName))
//                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(getText("option.lingerTicks"))
                                .description(OptionDescription.of(getText("option.lingerTicks.description")))
                                .binding(0, () -> config.lingerTicks, newVal -> config.lingerTicks = newVal)
                                .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(-1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(getText("option.blockSizePercent"))
                                .description(OptionDescription.of(getText("option.blockSizePercent.description")))
                                .binding(90, () -> config.boxSizePercent, newVal -> config.boxSizePercent = newVal)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 100).step(1).formatValue(val -> Component.literal(val + "%")))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(getText("option.culling"))
                                .description(OptionDescription.of(getText("option.culling.description")))
                                .binding(true, () -> config.culling, newVal -> config.culling = newVal)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(getText("option.occlusionCulling"))
                                .description(OptionDescription.of(getText("option.occlusionCulling.description")))
                                .binding(true, () -> config.occlusionCulling, newVal -> config.occlusionCulling = newVal)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(getText("option.occlusionCullingStart"))
                                .description(OptionDescription.of(getText("option.occlusionCullingStart.description")))
                                .binding(3, () -> config.occlusionCullStart, newVal -> config.occlusionCullStart = newVal)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 32).step(1))
                                .build())

                        .group(OptionGroup.createBuilder().name(getText("colors"))
                                .option(Option.<Color>createBuilder()
                                        .name(getText("option.colorPP"))
                                        .description(OptionDescription.of(getText("option.pp.description")))
                                        .binding(new Color(0x88FF77), () -> new Color(config.colorPP), val -> config.colorPP = val.getRGB() & 0xFFFFFF)
                                        .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(false))
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(getText("option.ppopacityPercent"))
                                        .description(OptionDescription.of(getText("option.pp.description")))
                                        .binding(25, () -> config.PPOpacityPercent, val -> config.PPOpacityPercent = val)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100).step(1).formatValue(val -> Component.literal(val + "%")))
                                        .build())
                                .option(Option.<Color>createBuilder()
                                        .name(getText("option.colorNC"))
                                        .description(OptionDescription.of(getText("option.nc.description")))
                                        .binding(new Color(0x7788FF), () -> new Color(config.colorNC), val -> config.colorNC = val.getRGB() & 0xFFFFFF)
                                        .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(false))
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(getText("option.ncopacityPercent"))
                                        .description(OptionDescription.of(getText("option.nc.description")))
                                        .binding(25, () -> config.NCOpacityPercent, val -> config.NCOpacityPercent = val)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100).step(1).formatValue(val -> Component.literal(val + "%")))
                                        .build())
                                .option(Option.<Color>createBuilder()
                                        .name(getText("option.colorComparator"))
                                        .description(OptionDescription.of(getText("option.comparator.description")))
                                        .binding(new Color(0xFF7788), () -> new Color(config.colorComparator), val -> config.colorComparator = val.getRGB() & 0xFFFFFF)
                                        .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(false))
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(getText("option.comparatoropacityPercent"))
                                        .description(OptionDescription.of(getText("option.comparator.description")))
                                        .binding(50, () -> config.ComparatorOpacityPercent, val -> config.ComparatorOpacityPercent = val)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100).step(1).formatValue(val -> Component.literal(val + "%")))
                                        .build())
                                .build())

                        .group(OptionGroup.createBuilder().name(getText("advancedOptions"))
                                .option(Option.<Integer>createBuilder()
                                        .name(getText("option.maxUniquePerTick"))
                                        .description(OptionDescription.of(getText("option.maxUniquePerTick.description")))
                                        .binding(1_000, () -> config.maxUpdates, newVal -> config.maxUpdates = newVal)
                                        .controller(IntegerFieldControllerBuilder::create)
                                        .flag(OptionFlag.GAME_RESTART)
                                        .build())
                                .build())
                        .build())

                .category(ConfigCategory.createBuilder()
                        .name(getText("logging"))
                        .option(Option.<Boolean>createBuilder()
                                .name(getText("option.logtofile"))
                                .description(OptionDescription.of(getText("option.logtofile.description")))
                                .binding(true, () -> config.logToFile, newVal -> config.logToFile = newVal)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(getText("option.logDuplicates"))
                                .description(OptionDescription.of(getText("option.logDuplicates.description")))
                                .binding(false, () -> config.logDuplicates, newVal -> config.logDuplicates = newVal)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(getText("option.logQueueMax"))
                                .description(OptionDescription.of(getText("option.logQueueMax.description")))
                                .binding(2_000_000, () -> config.logQueueMax, newVal -> config.logQueueMax = newVal)
                                .controller(IntegerFieldControllerBuilder::create)
                                .flag(OptionFlag.GAME_RESTART)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(getText("option.persistentLog"))
                                .description(OptionDescription.of(getText("option.persistentLog.description")))
                                .binding(false, () -> config.persistentLog, newVal -> config.persistentLog = newVal)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .group(OptionGroup.createBuilder().name(getText("debugLogging"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(getText("option.verboselogging"))
                                        .description(OptionDescription.of(getText("option.verboselogging.description")))
                                        .binding(false, () -> config.verboseLogging, newVal -> config.verboseLogging = newVal)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(getText("option.networklogging"))
                                        .description(OptionDescription.of(getText("option.networklogging.description")))
                                        .binding(false, () -> config.networkLogging, newVal -> config.networkLogging = newVal)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .build())
                        .build())
                .build()
                .generateScreen(parent);
    }

    private static Component getText(String key) { return Component.translatable("config.blockupdateviewer." + key); }
}