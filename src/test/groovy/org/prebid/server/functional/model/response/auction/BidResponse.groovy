package org.prebid.server.functional.model.response.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.auction.BidRequest

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidResponse implements ResponseModel {

    String id
    List<SeatBid> seatbid
    String bidid
    String cur
    String customdata
    Integer nbr
    BidResponseExt ext

    static BidResponse getDefaultBidResponse(BidRequest bidRequest) {
        getDefaultBidResponse(bidRequest.id, bidRequest.imp*.id)
    }

    static BidResponse getDefaultBidResponse(String id, List<String> impIds) {
        def bidResponse = new BidResponse(id: id)
        def bids = Bid.getDefaultBids(impIds)
        def seatBid = new SeatBid(bid: bids, seat: GENERIC)
        bidResponse.seatbid = [seatBid]
        bidResponse
    }
}
