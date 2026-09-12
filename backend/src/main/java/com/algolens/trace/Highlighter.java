package com.algolens.trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns raw {@link Touch}es into per-cell and per-node {@link ElementState}s.
 *
 * <p>Executors emit uncoloured visualizations plus a list of touches; colouring happens here,
 * once. That separation is what lets {@link SwapDetector} rewrite a step's touches after the
 * fact and simply re-run highlighting, instead of every producer having to know about colours.
 *
 * <p>A touch addresses an array cell by {@code target} = array name plus {@code index}, and an
 * object by {@code target} = node id. Both are strings, so the two cases are distinguished by
 * which kind of visualization is being coloured rather than by parsing the target.
 */
public final class Highlighter {

    private Highlighter() {
    }

    public static List<VisualizationState> apply(List<VisualizationState> visualizations,
            List<Touch> touches) {
        if (visualizations == null || visualizations.isEmpty()
                || touches == null || touches.isEmpty()) {
            return visualizations;
        }

        // array name -> index -> strongest state
        Map<String, Map<Integer, ElementState>> cellStates = new HashMap<>();
        // node id -> strongest state
        Map<String, ElementState> nodeStates = new HashMap<>();

        for (Touch touch : touches) {
            ElementState candidate = stateFor(touch.kind());
            cellStates.computeIfAbsent(touch.target(), key -> new HashMap<>())
                    .merge(touch.index(), candidate, Highlighter::strongest);
            nodeStates.merge(touch.target(), candidate, Highlighter::strongest);
        }

        List<VisualizationState> result = new ArrayList<>(visualizations.size());
        for (VisualizationState visualization : visualizations) {
            if (visualization.nodes() != null) {
                result.add(colourNodes(visualization, nodeStates));
            } else if (visualization.entries() != null) {
                result.add(colourEntries(visualization, cellStates.get(visualization.name())));
            } else if (visualization.elements() != null) {
                result.add(colourCells(visualization, cellStates.get(visualization.name())));
            } else {
                result.add(visualization);
            }
        }
        return result;
    }

    private static VisualizationState colourCells(VisualizationState visualization,
            Map<Integer, ElementState> states) {
        if (states == null) {
            return visualization;
        }
        List<ArrayElement> elements = new ArrayList<>(visualization.elements().size());
        for (ArrayElement element : visualization.elements()) {
            ElementState state = states.get(element.index());
            elements.add(state == null ? element : element.withState(state));
        }
        return new VisualizationState(visualization.type(), visualization.name(),
                visualization.elementType(), elements, visualization.rows(),
                visualization.entries(), visualization.pointers(), visualization.nodes(),
                visualization.edges());
    }

    private static VisualizationState colourEntries(VisualizationState visualization,
            Map<Integer, ElementState> states) {
        if (states == null) {
            return visualization;
        }
        List<VisualizationState.MapEntry> entries = new ArrayList<>(
                visualization.entries().size());
        for (VisualizationState.MapEntry entry : visualization.entries()) {
            ElementState state = states.get(entry.index());
            entries.add(state == null ? entry : entry.withState(state));
        }
        return new VisualizationState(visualization.type(), visualization.name(),
                visualization.elementType(), visualization.elements(), visualization.rows(),
                entries, visualization.pointers(), visualization.nodes(),
                visualization.edges());
    }

    private static VisualizationState colourNodes(VisualizationState visualization,
            Map<String, ElementState> states) {
        List<VisualizationState.GraphNode> nodes = new ArrayList<>(visualization.nodes().size());
        boolean changed = false;
        for (VisualizationState.GraphNode node : visualization.nodes()) {
            ElementState state = states.get(node.id());
            if (state == null) {
                nodes.add(node);
                continue;
            }
            changed = true;
            nodes.add(new VisualizationState.GraphNode(node.id(), node.className(), node.value(),
                    node.fields(), state));
        }
        if (!changed) {
            return visualization;
        }
        return new VisualizationState(visualization.type(), visualization.name(),
                visualization.elementType(), visualization.elements(), visualization.rows(),
                visualization.entries(), visualization.pointers(), nodes,
                visualization.edges());
    }

    private static ElementState stateFor(TouchKind kind) {
        return switch (kind) {
            case READ -> ElementState.ACTIVE;
            case WRITE -> ElementState.WRITTEN;
            case COMPARE -> ElementState.COMPARING;
            case SWAP -> ElementState.SWAPPING;
        };
    }

    private static ElementState strongest(ElementState a, ElementState b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(ElementState state) {
        return switch (state) {
            case SWAPPING -> 4;
            case COMPARING -> 3;
            case WRITTEN -> 2;
            case ACTIVE -> 1;
            default -> 0;
        };
    }
}
