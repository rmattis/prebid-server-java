package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Native {

    @JsonSerialize(using = NativeRequest.NativeSerializer)
    @JsonDeserialize(using = NativeRequest.NativeDeserializer)
    NativeRequest request
    String ver
    List<Integer> api
    List<Integer> battr

    static Native getDefaultNative(){
        new Native(request: NativeRequest.request)
    }
}
