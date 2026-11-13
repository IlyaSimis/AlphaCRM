package ru.quietclinic.workspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Именованное представление личного пространства. */
public record SavedViewRequest(
    @NotBlank @Size(max = 100) String name, @NotNull @Valid WorkspaceFilter filter) {}
