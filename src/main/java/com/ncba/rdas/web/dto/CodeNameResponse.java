package com.ncba.rdas.web.dto;

/** A reference dimension entry, e.g. {@code {"code":"AF","name":"Africa"}}. */
public record CodeNameResponse(String code, String name) {
}
