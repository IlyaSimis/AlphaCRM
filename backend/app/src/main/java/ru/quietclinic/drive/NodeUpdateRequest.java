package ru.quietclinic.drive;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Переименование и (или) перенос в другую папку; moveToRoot — перенести в «Мой диск». */
record NodeUpdateRequest(
    @Size(min = 1, max = 255) String name, UUID parentId, Boolean moveToRoot) {}
