package com.algolens.trace;

import java.util.List;

public record StackFrameState(String method, int line, List<VariableValue> variables) {
}
