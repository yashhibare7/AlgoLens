package com.algolens.trace;

import java.util.List;

/**
 * One drawable data structure at one moment in time.
 *
 * <p>Only the fields relevant to {@link #type} are populated; the rest serialise away because
 * Jackson is configured to omit nulls. {@code elements} carries ARRAY / STACK / QUEUE contents,
 * {@code rows} carries MATRIX contents, and {@code nodes}/{@code edges} carry every
 * reference-linked structure -- linked lists, trees and general object graphs are the same
 * payload with a different {@code type} telling the frontend how to lay it out.
 */
public record VisualizationState(
        VisualizationType type,
        String name,
        String elementType,
        List<ArrayElement> elements,
        List<List<ArrayElement>> rows,
        List<MapEntry> entries,
        List<Pointer> pointers,
        List<GraphNode> nodes,
        List<GraphEdge> edges) {

    public static VisualizationState array(String name, String elementType,
            List<ArrayElement> elements, List<Pointer> pointers) {
        return new VisualizationState(VisualizationType.ARRAY, name, elementType, elements, null,
                null, pointers, null, null);
    }

    /** An ordered collection drawn as a row: a list, stack, queue or set. */
    public static VisualizationState sequence(VisualizationType type, String name,
            String elementType, List<ArrayElement> elements, List<Pointer> pointers) {
        return new VisualizationState(type, name, elementType, elements, null, null, pointers,
                null, null);
    }

    public static VisualizationState matrix(String name, String elementType,
            List<List<ArrayElement>> rows) {
        return new VisualizationState(VisualizationType.MATRIX, name, elementType, null, rows,
                null, null, null, null);
    }

    /** A key/value store. */
    public static VisualizationState map(String name, String elementType,
            List<MapEntry> entries) {
        return new VisualizationState(VisualizationType.MAP, name, elementType, null, null,
                entries, null, null, null);
    }

    /** A linked list, tree or object graph. {@code type} decides the layout. */
    public static VisualizationState objectGraph(VisualizationType type, String name,
            String className, List<GraphNode> nodes, List<GraphEdge> edges,
            List<Pointer> pointers) {
        return new VisualizationState(type, name, className, null, null, null, pointers, nodes,
                edges);
    }

    /**
     * One key/value pair of a map.
     *
     * <p>{@code index} is the pair's position in iteration order, which is what lets a touch
     * address an entry and highlight it the same way an array cell is highlighted.
     */
    public record MapEntry(int index, Object key, Object value, ElementState state) {

        public MapEntry withState(ElementState newState) {
            return new MapEntry(index, key, value, newState);
        }
    }

    /**
     * One object in a reference-linked structure.
     *
     * @param id        stable identity, so the frontend can keep a node in place across steps
     * @param className the declaring class, shown small above the payload
     * @param value     the payload to display large: the first primitive field, or null when the
     *                  object has none
     * @param fields    every field, for the detail readout
     */
    public record GraphNode(
            String id,
            String className,
            Object value,
            List<VariableValue> fields,
            ElementState state) {
    }

    /**
     * A reference from one object to another.
     *
     * @param label the field name the reference lives in ({@code next}, {@code left}, ...)
     * @param to    null when the field is null, so the frontend can draw the list's terminator
     *              rather than silently omitting the tail
     */
    public record GraphEdge(String from, String to, String label) {
    }
}
