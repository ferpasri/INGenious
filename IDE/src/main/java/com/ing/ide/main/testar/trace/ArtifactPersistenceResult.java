package com.ing.ide.main.testar.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArtifactPersistenceResult {
    private final List<ArtifactReference> artifacts = new ArrayList<>();

    public void add(ArtifactReference artifact) {
        if (artifact != null) {
            artifacts.add(artifact);
        }
    }

    public void addAll(ArtifactPersistenceResult result) {
        if (result != null) {
            artifacts.addAll(result.artifacts);
        }
    }

    public List<ArtifactReference> getArtifacts() {
        return Collections.unmodifiableList(artifacts);
    }

    public boolean isEmpty() {
        return artifacts.isEmpty();
    }
}
