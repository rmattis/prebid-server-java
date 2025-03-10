package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum ChannelType {

    WEB, AMP, APP, VIDEO

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
