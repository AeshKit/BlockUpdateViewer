package aesh.kai.client.ui;

import aesh.kai.client.log.BULogger;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.WButton;
import io.github.cottonmc.cotton.gui.widget.WGridPanel;
import io.github.cottonmc.cotton.gui.widget.WListPanel;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import io.github.cottonmc.cotton.gui.widget.icon.Icon;
import io.github.cottonmc.cotton.gui.widget.icon.TextureIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static aesh.kai.BlockUpdateViewer.LOGGER;
import static aesh.kai.BlockUpdateViewer.MOD_ID;

public class DBViewerUI extends LightweightGuiDescription {
    private final List<BULogger.LogEntry> originalEntries;

    private final Set<Long> expandedTicks = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();

    public sealed interface Row permits TickHeader, EntryRow {}
    public record TickHeader(long tick) implements Row {}
    public record EntryRow(BULogger.LogEntry entry, int index) implements Row {}

    private final RowListPanel list;

    public DBViewerUI(List<BULogger.LogEntry> entries, Long thisTick) {
        this.originalEntries = entries;

        if(thisTick != null)
            expandedTicks.add(thisTick);

        rebuildRows();

        WGridPanel root = new WGridPanel();
        setRootPanel(root);
        root.setSize(420, 230);
        root.setInsets(Insets.ROOT_PANEL);
        WButton expand = new WButton(new TextureIcon(Identifier.fromNamespaceAndPath(MOD_ID, "textures/expand_icon.png")));
        expand.setOnClick(() -> expandAll(entries));

        WButton retract = new WButton(new TextureIcon(Identifier.fromNamespaceAndPath(MOD_ID, "textures/retract_icon.png")));
        retract.setOnClick(this::retractAll);

        list = new RowListPanel(
                rows,
                DBRow::new,
                (row, widget) -> {
                    try {
                        switch(row) {
                            case TickHeader header -> widget.configureHeader(header.tick(), this::rowToggle, thisTick, expandedTicks);
                            case EntryRow eRow -> widget.configureEntry(eRow.entry());
                        }
                    } catch(Exception e) {
                        LOGGER.error("Failed to bind row {}", row, e);
                    }
                }
        );
        list.setListItemHeight(11);
        list.getScrollBar().setScrollingSpeed(1);
        list.getScrollBar().setSize(1, 10);

        root.add(expand, 0, 0, 1, 1);
        root.add(retract, 1, 0, 1, 1);
        root.add(list, 0, 1, 23, 11);
        root.validate(this);
    }

    private void rebuildRows() {
        rows.clear();
        Map<Long, List<Integer>> groupedIdx = new LinkedHashMap<>();
        for(int i = 0; i < originalEntries.size(); ++i) {
            BULogger.LogEntry e = originalEntries.get(i);
            groupedIdx.computeIfAbsent(e.tick(), _ -> new ArrayList<>()).add(i);
        }
        for(var tick : groupedIdx.keySet()) {
            rows.add(new TickHeader(tick));
            if(expandedTicks.contains(tick)) {
                for(int i : groupedIdx.get(tick))
                    rows.add(new EntryRow(originalEntries.get(i), i));
            }
        }
    }

    private void expandAll(List<BULogger.LogEntry> entries) {
        for(var entry : entries) expandedTicks.add(entry.tick());
        rebuildRows();
        list.clear();
        // Try this without the clear method I added. The button label isn't updated.
        // Couldn't tell you why though lmaoo
        list.layout();
    }

    private void retractAll() {
        expandedTicks.clear();
        rebuildRows();
        list.clear();
        list.layout();
    }

    private void rowToggle(long tick) {
        if(!expandedTicks.add(tick)) expandedTicks.remove(tick);
        rebuildRows();
        list.layout();
    }

    private static final class RowListPanel extends WListPanel<Row, DBRow> {
        RowListPanel(List<Row> data, Supplier<DBRow> supplier, BiConsumer<Row, DBRow> configurator) {
            super(data, supplier, configurator);
        }

        void clear() {
            unconfigured.addAll(configured.values());
            configured.clear();
        }
    }
}