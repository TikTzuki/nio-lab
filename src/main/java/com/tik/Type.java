package com.tik;

import lombok.Generated;
import lombok.Getter;

@Getter
enum Type {
    TEXT("text"),
    IMAGE("image"),
    IMAGE_URL("image_url"),
    VIDEO("video"),
    AUDIO("audio"),
    CUSTOM("custom");

    final String type;

    Type(String type) {
        this.type = type;
    }
}