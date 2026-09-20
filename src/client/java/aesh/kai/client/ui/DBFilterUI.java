package aesh.kai.client.ui;

import aesh.kai.client.log.BULogger;
import aesh.kai.client.log.ThisTick;
import aesh.kai.network.UpdateCollector;
import io.github.cottonmc.cotton.gui.client.CottonClientScreen;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.impl.client.LibGuiClient;
import io.github.cottonmc.cotton.gui.widget.*;
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static aesh.kai.BlockUpdateViewer.LOGGER;
import static aesh.kai.client.BlockUpdateViewerClient.config;

public class DBFilterUI extends LightweightGuiDescription {

    private final String MAX_ENTRIES_DEFAULT = "10000";

    private String sDimension = null;
    private String sMaxTick = "";
    private String sMinTick = "";
    private String sX = "", sY = "", sZ = "";
    private String sType = null;
    private String maxTickSuggestion = "";
    private String sMaxEntries = MAX_ENTRIES_DEFAULT;

    private final WTextField maxTickField;
    private final WTextField minTickField;
    private final WButton plusOne;
    private final WButton plusTwo;
    private final WButton minusOne;
    private final WButton minusTwo;
    private final WText noResultsText;
    private final WButton search;

    Long knownTick = ThisTick.get().orElse(null);

    private final CompletableFuture<Void> flushReady;
    private boolean isSearching = false;

    public static void open(Screen parent) {
        CompletableFuture<Void> flushReady = BULogger.forceFlushAsync();
        Minecraft.getInstance().setScreenAndShow(new CottonClientScreen(new DBFilterUI(parent, flushReady)));
    }

    private DBFilterUI(Screen parent, CompletableFuture<Void> flushReady) {
        this.flushReady = flushReady;
        List<String> dimensionsList = new ArrayList<>();
        dimensionsList.add(null);
        if(Minecraft.getInstance().getConnection() != null) {
            for(ResourceKey<Level> levelKey : Minecraft.getInstance().getConnection().levels()) {
                dimensionsList.add(levelKey.identifier().toString());
            }
        }

        List<String> typesList = new ArrayList<>();
        typesList.add(null);
        typesList.add("pp");
        typesList.add("nc");
        typesList.add("comp");

        LocalPlayer player = Minecraft.getInstance().player;
        HitResult hitRes = Minecraft.getInstance().hitResult;

        WGridPanel root = new WGridPanel();
        setRootPanel(root);
        root.setSize(256, 160);
        root.setInsets(Insets.ROOT_PANEL);

        WGridPanel content = new WGridPanel();
        content.setSize(13, 90);
        content.setGaps(0, 3);

        WLabel title = new WLabel(getText("dbFilter.title"));
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        title.setHorizontalAlignment(HorizontalAlignment.LEFT);
        content.add(title, 0, 0, 6, 1);

        WToggleButton darkModeToggle = new WToggleButton(getText("darkMode")) {
            @Override
            public void onToggle(boolean on) {
                LibGuiClient.config.darkMode = on;
                LibGuiClient.saveConfig(LibGuiClient.config); // that's right I done stol'd the code strate from tha library istelf
            }
        };
        darkModeToggle.setToggle(LibGuiClient.config.darkMode);
        content.add(darkModeToggle, 9, 0, 4, 1);

        WText dimensionText = new WText(getText("dimension"));
        dimensionText.setHorizontalAlignment(HorizontalAlignment.LEFT);
        dimensionText.setVerticalAlignment(VerticalAlignment.CENTER);
        content.add(dimensionText, 0, 1, 6, 1);

        WButton dimensionButton = new WButton(getText("any"));
        dimensionButton.setOnClick(() -> {
            int i = (dimensionsList.indexOf(sDimension) + 1) % dimensionsList.size();
            sDimension = dimensionsList.get(i);
            dimensionButton.setLabel(Component.literal(sDimension == null ? getText("any").getString() : sDimension));
        });
        content.add(dimensionButton, 0, 2, 13, 1);

        WText positionText = new WText(getText("position"));
        positionText.setHorizontalAlignment(HorizontalAlignment.LEFT);
        positionText.setVerticalAlignment(VerticalAlignment.CENTER);
        content.add(positionText, 0, 3, 13, 1);

        WText xText = new WText(Component.literal("X:"));
        WText yText = new WText(Component.literal("Y:"));
        WText zText = new WText(Component.literal("Z:"));
        xText.setHorizontalAlignment(HorizontalAlignment.CENTER);
        xText.setVerticalAlignment(VerticalAlignment.CENTER);
        yText.setHorizontalAlignment(HorizontalAlignment.CENTER);
        yText.setVerticalAlignment(VerticalAlignment.CENTER);
        zText.setHorizontalAlignment(HorizontalAlignment.CENTER);
        zText.setVerticalAlignment(VerticalAlignment.CENTER);

        // NOTE: Alpha channel at the beginning, not end. Written as lowercase always
        xText.setDarkmodeColor(0xff_EEBCBC);
        yText.setDarkmodeColor(0xff_BCEEBC);
        zText.setDarkmodeColor(0xff_BCBCEE);
        xText.setColor(0xff_663030);
        yText.setColor(0xff_306630);
        zText.setColor(0xff_303066);

        String xSuggestion;
        String ySuggestion;
        String zSuggestion;
        if(player != null) {
            BlockPos playerPos = player.blockPosition();
            xSuggestion = Long.toString(playerPos.getX());
            ySuggestion = Long.toString(playerPos.getY());
            zSuggestion = Long.toString(playerPos.getZ());
        } else {
            xSuggestion = "";
            ySuggestion = "";
            zSuggestion = "";
        }

        WTextField xInp = new WTextField(Component.literal(xSuggestion));
        xInp.setTextPredicate(text -> text.matches("-?[0-9]*"));
        xInp.setChangedListener(s -> this.sX = s);
        xInp.setSuggestionColor(0xff_AA8080);

        WTextField yInp = new WTextField(Component.literal(ySuggestion));
        yInp.setTextPredicate(text -> text.matches("-?[0-9]*"));
        yInp.setChangedListener(s -> this.sY = s);
        yInp.setSuggestionColor(0xff_80AA80);

        WTextField zInp = new WTextField(Component.literal(zSuggestion));
        zInp.setTextPredicate(text -> text.matches("-?[0-9]*"));
        zInp.setChangedListener(s -> this.sZ = s);
        zInp.setSuggestionColor(0xff_8080AA);

        content.add(xText, 0, 4, 1, 1);
        content.add(xInp, 1, 4, 5, 1);
        content.add(yText, 0, 5, 1, 1);
        content.add(yInp, 1, 5, 5, 1);
        content.add(zText, 0, 6, 1, 1);
        content.add(zInp, 1, 6, 5, 1);

        WButton here = new WButton(getText("here"));
        here.setOnClick(() -> {
            xInp.setText(xSuggestion);
            yInp.setText(ySuggestion);
            zInp.setText(zSuggestion);
        });

        WButton looking = new WButton(getText("looking"));
        looking.setOnClick(() -> {
            if(hitRes instanceof BlockHitResult blockHit) {
                BlockPos targetPos = blockHit.getBlockPos();
                xInp.setText(Long.toString(targetPos.getX()));
                yInp.setText(Long.toString(targetPos.getY()));
                zInp.setText(Long.toString(targetPos.getZ()));
            }
        });

        WButton clear = new WButton(getText("clear"));
        clear.setOnClick(() -> {
            xInp.setText("");
            yInp.setText("");
            zInp.setText("");
        });

        content.add(here, 7, 4, 6, 1);
        content.add(looking, 7, 5, 6, 1);
        content.add(clear, 7, 6, 6, 1);

        WText typeText = new WText(getText("type"));
        typeText.setVerticalAlignment(VerticalAlignment.CENTER);
        typeText.setHorizontalAlignment(HorizontalAlignment.CENTER);
        content.add(typeText, 0, 8, 2, 1);

        WButton typeButton = new WButton(getText("any"));
        typeButton.setOnClick(() -> {
            int i = (typesList.indexOf(sType) + 1) % typesList.size();
            sType = typesList.get(i);
            typeButton.setLabel(sType == null ? getText("any") : getText(sType));
        });
        content.add(typeButton, 2, 8, 11, 1);

        // Tick range

        WText maxTickText = new WText(getText("maxTick"));
        maxTickText.setVerticalAlignment(VerticalAlignment.CENTER);
        maxTickText.setHorizontalAlignment(HorizontalAlignment.CENTER);
        content.add(maxTickText, 0, 10, 3, 1);

        WText minTickText = new WText(getText("minTick"));
        minTickText.setVerticalAlignment(VerticalAlignment.CENTER);
        minTickText.setHorizontalAlignment(HorizontalAlignment.CENTER);
        content.add(minTickText, 0, 11, 3, 1);

        maxTickSuggestion = knownTick != null ? Long.toString(knownTick) : "";
        sMaxTick = maxTickSuggestion;

        maxTickField = new WTextField(Component.literal(maxTickSuggestion));
        maxTickField.setText(maxTickSuggestion);
        maxTickField.setTextPredicate(text -> text.matches("[0-9]*"));
        maxTickField.setChangedListener(s -> {
            sMaxTick = s;
            syncTickButtons();
        });

        minTickField = new WTextField(Component.literal(maxTickSuggestion));
        minTickField.setTextPredicate(text -> text.matches("[0-9]*"));
        minTickField.setChangedListener(s -> {
            sMinTick = s;
            syncTickButtons();
        });

        content.add(maxTickField, 3, 10, 10, 1);
        content.add(minTickField, 3, 11, 10, 1);

        minusTwo = new WButton(Component.literal("-2"));
        minusTwo.setOnClick(() -> adjustMinTick(-2));

        minusOne = new WButton(Component.literal("-1"));
        minusOne.setOnClick(() -> adjustMinTick(-1));

        WButton copy = new WButton(getText("copy"));
        copy.setOnClick(() -> {
            Long maxL = resolveMaxTick();
            if(maxL != null) {
                sMinTick = sMaxTick;
                minTickField.setText(sMinTick);
            }
            syncTickButtons();
        });

        plusOne = new WButton(Component.literal("+1"));
        plusOne.setOnClick(() -> adjustMinTick(1));

        plusTwo = new WButton(Component.literal("+2"));
        plusTwo.setOnClick(() -> adjustMinTick(2));

        content.add(minusTwo, 3, 12, 1, 1);
        content.add(minusOne, 4, 12, 1, 1);
        content.add(copy, 5, 12, 2, 1);
        content.add(plusOne, 7, 12, 1, 1);
        content.add(plusTwo, 8, 12, 1, 1);

        WText entriesText = new WText(getText("maxEntries"));
        entriesText.setVerticalAlignment(VerticalAlignment.CENTER);
        entriesText.setHorizontalAlignment(HorizontalAlignment.LEFT);
        content.add(entriesText, 7, 13, 6, 1);

        WTextField entriesField = new WTextField(Component.literal(MAX_ENTRIES_DEFAULT));
        entriesField.setText(MAX_ENTRIES_DEFAULT);
        entriesField.setTextPredicate(text -> text.matches("[0-9]*"));
        entriesField.setChangedListener(s -> {
            if(s.isEmpty())
                sMaxEntries = MAX_ENTRIES_DEFAULT;
            else
                sMaxEntries = s;
        });

        content.add(entriesField, 7, 14, 6, 1);

        noResultsText = new WText(Component.empty()); // populate when no results, prevent user confusion
        noResultsText.setVerticalAlignment(VerticalAlignment.CENTER);
        noResultsText.setHorizontalAlignment(HorizontalAlignment.CENTER);
        noResultsText.setColor(0xffCC3322);
        noResultsText.setDarkmodeColor(0xffFF6666);

        content.add(noResultsText, 0, 13, 6, 1);

        search = new WButton(getText("search")) {
            private int tickCounter = 0;
            private int frameIndex = 0;
            private final String[] frames = {"|", "/", "-", "\\"};

            @Override
            public void tick() {
                super.tick();
                if(isSearching && tickCounter++ % 2 == 0) {
                    frameIndex = (frameIndex + 1) % frames.length;
                    this.setLabel(Component.literal(frames[frameIndex]));
                }
            }
        };
        search.setOnClick(() -> runSearch(parent));

        content.add(search, 0, 14, 6, 1);

        if(isDarkMode().get()) {
            dimensionText.setDrawShadows(true);
            entriesText.setDrawShadows(true);
            maxTickText.setDrawShadows(true);
            minTickText.setDrawShadows(true);
            positionText.setDrawShadows(true);
            title.setDrawShadows(true);
            xText.setDrawShadows(true);
            yText.setDrawShadows(true);
            zText.setDrawShadows(true);
        }

        syncTickButtons();

        WScrollPanel scrollPanel = new WScrollPanel(content);
        root.add(scrollPanel, 0, 0, 14, 10);

        root.validate(this);
    }

    private void runSearch(Screen parent) {
        Long maxL = parseLongOrNull(sMaxTick);
        Long minL = parseLongOrNull(sMinTick);
        Integer x = parseIntOrNull(sX);
        Integer y = parseIntOrNull(sY);
        Integer z = parseIntOrNull(sZ);

        Long packedPos = (x != null && y != null && z != null) ? BlockPos.asLong(x, y, z) : null;

        noResultsText.setText(Component.empty());

        BULogger.QueryFilter filter = getFilter(minL, maxL, packedPos);

        search.setEnabled(false);
        isSearching = true;

        flushReady.thenCompose(_ -> BULogger.queryAsync(filter))
                .whenComplete((results, e) -> Minecraft.getInstance().execute(() -> {
                    isSearching = false;

                    if(e != null) {
                        LOGGER.error("Failed to search database", e);
                        return;
                    }
                    if(config.verboseLogging) {
                        LOGGER.info("Res: {}", sMaxEntries);
                        LOGGER.info("Filter: {}", filter);
                    }

                    if(results.isEmpty() && !isSearching) {
                        search.setEnabled(true);
                        search.setLabel(getText("search"));
                        noResultsText.setText(getText("noResults"));
                    } else Minecraft.getInstance().setScreenAndShow(new CottonClientScreen(new DBViewerUI(results, knownTick)));
                }));
    }

    private BULogger.@NonNull QueryFilter getFilter(Long minL, Long maxL, Long packedPos) {
        Integer typeBit = switch(sType == null ? "" : sType) {
            case "pp" -> UpdateCollector.UpdateType.PP.bit;
            case "nc" -> UpdateCollector.UpdateType.NC.bit;
            case "comp" -> UpdateCollector.UpdateType.COMPARATOR.bit;
            default -> null;
        };

        List<BULogger.SortSpec> sort = List.of(
                new BULogger.SortSpec(BULogger.SortKey.TICK, false),
                new BULogger.SortSpec(BULogger.SortKey.ROWID, false)
        );

        return new BULogger.QueryFilter(
                sDimension, minL, maxL, packedPos, typeBit, sort, Long.parseLong(sMaxEntries)
        );
    }

    private void adjustMinTick(long delta) {
        Long maxL = resolveMaxTick();
        if(maxL == null) {
            syncTickButtons();
            return;
        }

        Long minL = parseLongOrNull(sMinTick);
        long base = (minL != null) ? minL : maxL;
        long next = Math.clamp(maxL, 0L, base + delta);

        sMinTick = String.valueOf(next);
        minTickField.setText(sMinTick);
        syncTickButtons();
    }

    private @Nullable Long resolveMaxTick() {
        Long maxL = parseLongOrNull(sMaxTick);
        if(maxL != null) return maxL;

        if(maxTickSuggestion.isBlank()) return null;

        sMaxTick = maxTickSuggestion;
        maxTickField.setText(sMaxTick);
        return parseLongOrNull(sMaxTick);
    }


    private Long previewMaxTick() {
        Long maxL = parseLongOrNull(sMaxTick);
        if(maxL != null) return maxL;
        return parseLongOrNull(maxTickSuggestion);
    }

    private void syncTickButtons() {
        Long maxL = parseLongOrNull(sMaxTick);
        Long minL = parseLongOrNull(sMinTick);

        if(maxL != null && minL != null && minL > maxL) {
            sMinTick = sMaxTick;
            minTickField.setText(sMinTick);
            minL = maxL;
        }

        Long previewMax = previewMaxTick();
        Long previewMin = (minL != null) ? minL : previewMax;

        boolean canSearch = maxL != null && !isSearching;
        boolean canAddOne = previewMax != null && previewMin <= previewMax - 1;
        boolean canAddTwo = previewMax != null && previewMin <= previewMax - 2;
        boolean canSubOne = previewMax != null && previewMin >= 1;
        boolean canSubTwo = previewMax != null && previewMin >= 2;

        plusOne.setEnabled(canAddOne);
        plusTwo.setEnabled(canAddTwo);
        minusOne.setEnabled(canSubOne);
        minusTwo.setEnabled(canSubTwo);
        search.setEnabled(canSearch);
    }

    private static Long parseLongOrNull(String s) {
        if(s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); } catch(NumberFormatException e) { return null; }
    }

    private static Integer parseIntOrNull(String s) {
        if(s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch(NumberFormatException e) { return null; }
    }

    private static Component getText(String key) { return Component.translatable("dbv.blockupdateviewer." + key); }
}