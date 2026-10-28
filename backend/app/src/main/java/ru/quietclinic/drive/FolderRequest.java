package ru.quietclinic.drive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record FolderRequest(UUID parentId, @NotBlank @Size(max = 255) String name) {}
