package aesh.kai.client.ui;

import aesh.kai.client.log.BULogger;
import io.github.cottonmc.cotton.gui.client.LibGui;
import io.github.cottonmc.cotton.gui.widget.WButton;
import io.github.cottonmc.cotton.gui.widget.WLabel;
import io.github.cottonmc.cotton.gui.widget.WPlainPanel;
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment;
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.function.LongConsumer;

public class DBRow extends WPlainPanel {
    private static final int INDENT = 18;
    private static final int HEADER_HEIGHT = 2;

    private final WButton toggleButton = new WButton(Component.empty());
    private final WLabel headerLabel = new WLabel(Component.empty());
    private final WLabel dimLabel = new WLabel(Component.empty());
    private final WLabel coordinatesLabel = new WLabel(Component.empty());
    private final WLabel typeLabel = new WLabel(Component.empty());
    private final WButton tpButton = new WButton(Component.literal("TP"));

    private long currentTick = -1L;

    public void configureHeader(long tick, LongConsumer onToggle, Long thisTick, Set<Long> expandedTicks) {
        currentTick = tick;
        children.clear();

        toggleButton.setLabel(expandedTicks.contains(tick) ?
                Component.literal("-") :
                Component.literal("+"));
        toggleButton.setOnClick(() -> {
            onToggle.accept(tick);
            toggleButton.setLabel(expandedTicks.contains(tick) ?
                    Component.literal("-") :
                    Component.literal("+"));
        });

        String whenText = getWhenText(tick, thisTick);
        headerLabel.setText(Component.literal(tick + whenText));
        headerLabel.setVerticalAlignment(VerticalAlignment.CENTER);
        headerLabel.setHorizontalAlignment(HorizontalAlignment.LEFT);


        int backgroundColor;
        if(LibGui.isDarkMode()) {
            if(tick % 2 == 0) backgroundColor = 0xff_50503A;
            else backgroundColor = 0xff_44443A;
            headerLabel.setDrawShadows(true);
        } else {
            if(tick % 2 == 0) backgroundColor = 0xff_F0F0C6;
            else backgroundColor = 0xff_DDDDC6;
        }
        setBackgroundPainter((dc, x, y, _) -> dc.fill(x, y, x + this.width, y + this.height, backgroundColor));

        add(toggleButton, 0, HEADER_HEIGHT, 14, 8);
        add(headerLabel, INDENT, HEADER_HEIGHT, 200, 8);
    }

    private static @NonNull String getWhenText(long tick, Long thisTick) {
        String whenText = "";
        if(thisTick != null) {
            String text;
            if(tick == thisTick)
                text = Component.translatable("dbv.blockupdateviewer.oneTickAgo").getString();
            else
                text = thisTick - tick + 1 + Component.translatable("dbv.blockupdateviewer.ticksAgo").getString();

            whenText = "  [ " + text + " ]";
        }
        return whenText;
    }

    public void configureEntry(BULogger.LogEntry entry) {
        this.currentTick = -1L;
        children.clear();

        BlockPos pos = entry.pos();

        decideTypeTextColor(entry);

        typeLabel.setVerticalAlignment(VerticalAlignment.CENTER);

        dimLabel.setText(Component.literal(entry.dim()));
        dimLabel.setVerticalAlignment(VerticalAlignment.CENTER);
        dimLabel.setHorizontalAlignment(HorizontalAlignment.RIGHT);

        coordinatesLabel.setText(Component.literal(
                "@ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
        ));
        coordinatesLabel.setVerticalAlignment(VerticalAlignment.CENTER);

        tpButton.setOnClick(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if(player == null) return;
            player.connection.sendCommand("execute in " + entry.dim() + " run tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        });

        int x = INDENT;
        x = place(typeLabel, x, 16);
        x = place(coordinatesLabel, x, 141);
        x = place(dimLabel, x, 185);
        add(tpButton, x + 5, 0, 30, 12);


        int color;
        if(LibGui.isDarkMode()) {
            if(entry.type().name().equals("PP")) color = 0xff_2F352F;
            else if(entry.type().name().equals("NC")) color = 0xff_2F2F35;
            else color = 0xff_352F2F; // COMPARATOR
        } else {
            if(entry.type().name().equals("PP")) color = 0xff_C6D6C6;
            else if(entry.type().name().equals("NC")) color = 0xff_C6C6D6;
            else color = 0xff_D6C6C6;
        }
        setBackgroundPainter((dc, x2, y2, panel) -> dc.fill(x2, y2, x2 + this.width, y2 + this.height, color));
    }


    private void decideTypeTextColor(BULogger.LogEntry entry) {
        if(LibGui.isDarkMode()) {
            if(entry.type().name().equals("PP")) {
                typeLabel.setText(Component.literal("PP"));
                typeLabel.setDarkmodeColor(0xff_BCFFBC);
            } else if(entry.type().name().equals("NC")) {
                typeLabel.setText(Component.literal("NC"));
                typeLabel.setDarkmodeColor(0xff_BCBCFF);
            } else {
                typeLabel.setText(Component.literal("CO"));
                typeLabel.setDarkmodeColor(0xff_FFBCBC);
            }
        } else {
            if(entry.type().name().equals("PP")) {
                typeLabel.setText(Component.literal("PP"));
                typeLabel.setColor(0xff_3D663D);
            } else if(entry.type().name().equals("NC")) {
                typeLabel.setText(Component.literal("NC"));
                typeLabel.setColor(0xff_3D3D66);
            } else {
                typeLabel.setText(Component.literal("CO"));
                typeLabel.setColor(0xff_663D3D);
            }
        } // ^ WET code example [ Write Everything Twice ]
    }     // DRY it off if you want to. I just don't care enough to bother

    private int place(WLabel w, int x, int width) {
        add(w, x, 0, width, 12);
        return x + width;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public void setCurrentTick(long currentTick) {
        this.currentTick = currentTick;
    }
}