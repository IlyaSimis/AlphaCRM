package ru.quietclinic.drive;

import java.util.List;
import java.util.UUID;

/** Узел, путь до него (только видимые сотруднику папки) и содержимое папки. */
record NodeDetails(NodeView node, List<Crumb> path, List<NodeView> children) {
  record Crumb(UUID id, String name) {}
}
