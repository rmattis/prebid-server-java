package org.prebid.server.proto.openrtb.ext.request.compass;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.compass
 */

@Value(staticConstructor = "of")
public class ExtImpCompass {

    @JsonProperty(value = "placementId")
    String placementId;

    @JsonProperty(value = "endpointId")
    String endpointId;
}
