package org.prebid.server.bidder.appnexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.appnexus.model.ImpWithExtProperties;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtCreative;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtVideo;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;
import org.prebid.server.bidder.appnexus.proto.AppnexusReqExtAppnexus;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidPbs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AppnexusBidder implements Bidder<BidRequest> {

    private static final int AD_POSITION_ABOVE_THE_FOLD = 1; // openrtb.AdPosition.AdPositionAboveTheFold
    private static final int AD_POSITION_BELOW_THE_FOLD = 3; // openrtb.AdPosition.AdPositionBelowTheFold
    private static final int MAX_IMP_PER_REQUEST = 10;
    private static final int DEFAULT_PLATFORM_ID = 5;
    private static final String POD_SEPARATOR = "_";
    private static final Map<Integer, String> IAB_CATEGORIES = new HashMap<>();

    private static final TypeReference<ExtPrebid<?, ExtImpAppnexus>> APPNEXUS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    static {
        IAB_CATEGORIES.put(1, "IAB20-3");
        IAB_CATEGORIES.put(2, "IAB18-5");
        IAB_CATEGORIES.put(3, "IAB10-1");
        IAB_CATEGORIES.put(4, "IAB2-3");
        IAB_CATEGORIES.put(5, "IAB19-8");
        IAB_CATEGORIES.put(6, "IAB22-1");
        IAB_CATEGORIES.put(7, "IAB18-1");
        IAB_CATEGORIES.put(8, "IAB12-3");
        IAB_CATEGORIES.put(9, "IAB5-1");
        IAB_CATEGORIES.put(10, "IAB4-5");
        IAB_CATEGORIES.put(11, "IAB13-4");
        IAB_CATEGORIES.put(12, "IAB8-7");
        IAB_CATEGORIES.put(13, "IAB9-7");
        IAB_CATEGORIES.put(14, "IAB7-1");
        IAB_CATEGORIES.put(15, "IAB20-18");
        IAB_CATEGORIES.put(16, "IAB10-7");
        IAB_CATEGORIES.put(17, "IAB19-18");
        IAB_CATEGORIES.put(18, "IAB13-6");
        IAB_CATEGORIES.put(19, "IAB18-4");
        IAB_CATEGORIES.put(20, "IAB1-5");
        IAB_CATEGORIES.put(21, "IAB1-6");
        IAB_CATEGORIES.put(22, "IAB3-4");
        IAB_CATEGORIES.put(23, "IAB19-13");
        IAB_CATEGORIES.put(24, "IAB22-2");
        IAB_CATEGORIES.put(25, "IAB3-9");
        IAB_CATEGORIES.put(26, "IAB17-18");
        IAB_CATEGORIES.put(27, "IAB19-6");
        IAB_CATEGORIES.put(28, "IAB1-7");
        IAB_CATEGORIES.put(29, "IAB9-30");
        IAB_CATEGORIES.put(30, "IAB20-7");
        IAB_CATEGORIES.put(31, "IAB20-17");
        IAB_CATEGORIES.put(32, "IAB7-32");
        IAB_CATEGORIES.put(33, "IAB16-5");
        IAB_CATEGORIES.put(34, "IAB19-34");
        IAB_CATEGORIES.put(35, "IAB11-5");
        IAB_CATEGORIES.put(36, "IAB12-3");
        IAB_CATEGORIES.put(37, "IAB11-4");
        IAB_CATEGORIES.put(38, "IAB12-3");
        IAB_CATEGORIES.put(39, "IAB9-30");
        IAB_CATEGORIES.put(41, "IAB7-44");
        IAB_CATEGORIES.put(42, "IAB7-1");
        IAB_CATEGORIES.put(43, "IAB7-30");
        IAB_CATEGORIES.put(50, "IAB19-30");
        IAB_CATEGORIES.put(51, "IAB17-12");
        IAB_CATEGORIES.put(52, "IAB19-30");
        IAB_CATEGORIES.put(53, "IAB3-1");
        IAB_CATEGORIES.put(55, "IAB13-2");
        IAB_CATEGORIES.put(56, "IAB19-30");
        IAB_CATEGORIES.put(57, "IAB19-30");
        IAB_CATEGORIES.put(58, "IAB7-39");
        IAB_CATEGORIES.put(59, "IAB22-1");
        IAB_CATEGORIES.put(60, "IAB7-39");
        IAB_CATEGORIES.put(61, "IAB21-3");
        IAB_CATEGORIES.put(62, "IAB5-1");
        IAB_CATEGORIES.put(63, "IAB12-3");
        IAB_CATEGORIES.put(64, "IAB20-18");
        IAB_CATEGORIES.put(65, "IAB11-2");
        IAB_CATEGORIES.put(66, "IAB17-18");
        IAB_CATEGORIES.put(67, "IAB9-9");
        IAB_CATEGORIES.put(68, "IAB9-5");
        IAB_CATEGORIES.put(69, "IAB7-44");
        IAB_CATEGORIES.put(71, "IAB22-3");
        IAB_CATEGORIES.put(73, "IAB19-30");
        IAB_CATEGORIES.put(74, "IAB8-5");
        IAB_CATEGORIES.put(78, "IAB22-1");
        IAB_CATEGORIES.put(85, "IAB12-2");
        IAB_CATEGORIES.put(86, "IAB22-3");
        IAB_CATEGORIES.put(87, "IAB11-3");
        IAB_CATEGORIES.put(112, "IAB7-32");
        IAB_CATEGORIES.put(113, "IAB7-32");
        IAB_CATEGORIES.put(114, "IAB7-32");
        IAB_CATEGORIES.put(115, "IAB7-32");
        IAB_CATEGORIES.put(118, "IAB9-5");
        IAB_CATEGORIES.put(119, "IAB9-5");
        IAB_CATEGORIES.put(120, "IAB9-5");
        IAB_CATEGORIES.put(121, "IAB9-5");
        IAB_CATEGORIES.put(122, "IAB9-5");
        IAB_CATEGORIES.put(123, "IAB9-5");
        IAB_CATEGORIES.put(124, "IAB9-5");
        IAB_CATEGORIES.put(125, "IAB9-5");
        IAB_CATEGORIES.put(126, "IAB9-5");
        IAB_CATEGORIES.put(127, "IAB22-1");
        IAB_CATEGORIES.put(132, "IAB1-2");
        IAB_CATEGORIES.put(133, "IAB19-30");
        IAB_CATEGORIES.put(137, "IAB3-9");
        IAB_CATEGORIES.put(138, "IAB19-3");
        IAB_CATEGORIES.put(140, "IAB2-3");
        IAB_CATEGORIES.put(141, "IAB2-1");
        IAB_CATEGORIES.put(142, "IAB2-3");
        IAB_CATEGORIES.put(143, "IAB17-13");
        IAB_CATEGORIES.put(166, "IAB11-4");
        IAB_CATEGORIES.put(175, "IAB3-1");
        IAB_CATEGORIES.put(176, "IAB13-4");
        IAB_CATEGORIES.put(182, "IAB8-9");
        IAB_CATEGORIES.put(183, "IAB3-5");
    }

    private final String endpointUrl;
    private final Integer headerBiddingSource;
    private final JacksonMapper mapper;

    private final Random rand = new Random();

    public AppnexusBidder(String endpointUrl, Integer platformId, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.headerBiddingSource = ObjectUtils.defaultIfNull(platformId, DEFAULT_PLATFORM_ID);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final String defaultDisplayManagerVer = makeDefaultDisplayManagerVer(bidRequest);
        final List<Imp> processedImps = new ArrayList<>();
        final Set<String> uniqueIds = new HashSet<>();
        Boolean generateAdPodId = null;

        for (final Imp imp : bidRequest.getImp()) {
            try {
                final ImpWithExtProperties impWithExtProperties = processImp(imp, defaultDisplayManagerVer);
                final Boolean impGenerateAdPodId = impWithExtProperties.getGenerateAdPodId();

                generateAdPodId = ObjectUtils.defaultIfNull(generateAdPodId, impGenerateAdPodId);
                if (!Objects.equals(generateAdPodId, impGenerateAdPodId)) {
                    errors.add(BidderError.badInput(
                            "Generate ad pod option should be same for all pods in request"));
                    return Result.withErrors(errors);
                }

                processedImps.add(impWithExtProperties.getImp());
                final String memberId = impWithExtProperties.getMemberId();
                if (memberId != null) {
                    uniqueIds.add(memberId);
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (processedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final String url = constructUrl(uniqueIds, errors);
        return Result.of(constructRequests(bidRequest, processedImps, url, generateAdPodId), errors);
    }

    private String makeDefaultDisplayManagerVer(BidRequest bidRequest) {
        final ExtApp extApp = ObjectUtil.getIfNotNull(bidRequest.getApp(), App::getExt);
        final ExtAppPrebid prebid = ObjectUtil.getIfNotNull(extApp, ExtApp::getPrebid);

        final String source = ObjectUtil.getIfNotNull(prebid, ExtAppPrebid::getSource);
        final String version = ObjectUtil.getIfNotNull(prebid, ExtAppPrebid::getVersion);

        return ObjectUtils.allNotNull(source, version)
                ? String.format("%s-%s", source, version)
                : null;
    }

    private ImpWithExtProperties processImp(Imp imp, String defaultDisplayManagerVer) {
        final ExtImpAppnexus appnexusExt = validateAndResolveImpExt(imp);

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .banner(makeBanner(imp.getBanner(), appnexusExt))
                .ext(makeImpExt(appnexusExt));

        final String invCode = appnexusExt.getInvCode();
        if (StringUtils.isNotBlank(invCode)) {
            impBuilder.tagid(invCode);
        }

        final BigDecimal reserve = appnexusExt.getReserve();
        if (!BidderUtil.isValidPrice(imp.getBidfloor()) && BidderUtil.isValidPrice(reserve)) {
            impBuilder.bidfloor(reserve);
        }

        if (StringUtils.isBlank(imp.getDisplaymanagerver()) && StringUtils.isNotBlank(defaultDisplayManagerVer)) {
            impBuilder.displaymanagerver(defaultDisplayManagerVer);
        }

        return ImpWithExtProperties.of(impBuilder.build(), appnexusExt.getMember(), appnexusExt.getGenerateAdPodId());
    }

    private ExtImpAppnexus validateAndResolveImpExt(Imp imp) {
        try {
            final ExtImpAppnexus ext = mapper.mapper()
                    .convertValue(imp.getExt(), APPNEXUS_EXT_TYPE_REFERENCE)
                    .getBidder();

            final ExtImpAppnexus resolvedExt = resolveLegacyParameters(ext);
            validateExtImpAppnexus(resolvedExt);

            return resolvedExt;
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static ExtImpAppnexus resolveLegacyParameters(ExtImpAppnexus extImpAppnexus) {
        if (!shouldReplaceWithLegacyParameters(extImpAppnexus)) {
            return extImpAppnexus;
        }

        final Integer resolvedPlacementId = ObjectUtils.defaultIfNull(
                extImpAppnexus.getLegacyPlacementId(), extImpAppnexus.getPlacementId());
        final String resolvedInvCode = ObjectUtils.defaultIfNull(
                extImpAppnexus.getInvCode(), extImpAppnexus.getLegacyInvCode());
        final String resolvedTrafficSourceCode = ObjectUtils.defaultIfNull(
                extImpAppnexus.getTrafficSourceCode(), extImpAppnexus.getLegacyTrafficSourceCode());

        return extImpAppnexus.toBuilder()
                .placementId(resolvedPlacementId)
                .invCode(resolvedInvCode)
                .trafficSourceCode(resolvedTrafficSourceCode)
                .build();
    }

    private static boolean shouldReplaceWithLegacyParameters(ExtImpAppnexus extImpAppnexus) {
        final boolean setPlacementId = extImpAppnexus.getPlacementId() == null
                && extImpAppnexus.getLegacyPlacementId() != null;
        final boolean setInvCode = extImpAppnexus.getInvCode() == null
                && extImpAppnexus.getLegacyInvCode() != null;
        final boolean setTrafficSourceCode = extImpAppnexus.getTrafficSourceCode() == null
                && extImpAppnexus.getLegacyTrafficSourceCode() != null;

        return setPlacementId || setInvCode || setTrafficSourceCode;
    }

    private static void validateExtImpAppnexus(ExtImpAppnexus extImpAppnexus) {
        final int placementId = ObjectUtils.defaultIfNull(extImpAppnexus.getPlacementId(), 0);
        if (placementId == 0 && StringUtils.isAnyBlank(extImpAppnexus.getInvCode(), extImpAppnexus.getMember())) {
            throw new PreBidException("No placement or member+invcode provided");
        }
    }

    private ObjectNode makeImpExt(ExtImpAppnexus appnexusExt) {
        final AppnexusImpExtAppnexus appnexusImpExt = AppnexusImpExtAppnexus.builder()
                .placementId(appnexusExt.getPlacementId())
                .keywords(makeKeywords(appnexusExt.getKeywords()))
                .trafficSourceCode(appnexusExt.getTrafficSourceCode())
                .usePmtRule(appnexusExt.getUsePmtRule())
                .privateSizes(appnexusExt.getPrivateSizes())
                .build();

        return mapper.mapper().valueToTree(AppnexusImpExt.of(appnexusImpExt));
    }

    private static String makeKeywords(List<AppnexusKeyVal> keywords) {
        final String resolvedKeywords = CollectionUtils.emptyIfNull(keywords).stream()
                .filter(entry -> entry.getKey() != null)
                .flatMap(AppnexusBidder::extractKeywords)
                .collect(Collectors.joining(","));

        return StringUtils.stripToNull(resolvedKeywords);
    }

    private static Stream<String> extractKeywords(AppnexusKeyVal appnexusKeyVal) {
        final String key = appnexusKeyVal.getKey();
        final List<String> values = appnexusKeyVal.getValue();
        return CollectionUtils.isNotEmpty(values)
                ? values.stream().map(value -> String.format("%s=%s", key, value))
                : Stream.of(key);
    }

    private static Banner makeBanner(Banner banner, ExtImpAppnexus appnexusExt) {
        if (banner == null) {
            return null;
        }
        final Integer width = banner.getW();
        final Integer height = banner.getH();

        final List<Format> formats = banner.getFormat();
        final Format firstFormat = CollectionUtils.isNotEmpty(formats) ? formats.get(0) : null;

        final boolean replaceWithFirstFormat = firstFormat != null && width == null && height == null;

        final Integer resolvedWidth = replaceWithFirstFormat ? firstFormat.getW() : width;
        final Integer resolvedHeight = replaceWithFirstFormat ? firstFormat.getH() : height;

        final Integer position = resolvePosition(appnexusExt.getPosition());

        return position != null || replaceWithFirstFormat
                ? banner.toBuilder().pos(position).w(resolvedWidth).h(resolvedHeight).build()
                : banner;
    }

    private static Integer resolvePosition(String position) {
        final Integer posAbove = Objects.equals(position, "above") ? AD_POSITION_ABOVE_THE_FOLD : null;
        final Integer posBelow = Objects.equals(position, "below") ? AD_POSITION_BELOW_THE_FOLD : null;
        return posAbove != null ? posAbove : posBelow;
    }

    private String constructUrl(Set<String> ids, List<BidderError> errors) {
        validateMemberIds(ids, errors);
        return CollectionUtils.isNotEmpty(ids)
                ? String.format("%s?member_id=%s", endpointUrl, ids.iterator().next())
                : endpointUrl;
    }

    private static void validateMemberIds(Set<String> uniqueIds, List<BidderError> errors) {
        if (uniqueIds.size() > 1) {
            errors.add(BidderError.badInput(
                    "All request.imp[i].ext.appnexus.member params must match. Request contained: "
                            + String.join(", ", uniqueIds)));
        }
    }

    private List<HttpRequest<BidRequest>> constructRequests(BidRequest bidRequest,
                                                            List<Imp> imps,
                                                            String url,
                                                            Boolean generateAdPodId) {

        final String requestEndpointName = extractEndpointName(bidRequest);
        final boolean isVideoRequest = StringUtils.equals(requestEndpointName, Endpoint.openrtb2_video.value());
        final boolean isAmpRequest = StringUtils.equals(requestEndpointName, Endpoint.openrtb2_amp.value());

        return isVideoRequest && BooleanUtils.isTrue(generateAdPodId)
                ? constructPodRequests(bidRequest, imps, url)
                : constructPartitionedRequests(bidRequest, imps, url, isVideoRequest, isAmpRequest);
    }

    private static String extractEndpointName(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final ExtRequestPrebidPbs pbs = prebid != null ? prebid.getPbs() : null;
        return pbs != null ? pbs.getEndpoint() : null;
    }

    private List<HttpRequest<BidRequest>> constructPodRequests(BidRequest bidRequest,
                                                               List<Imp> imps,
                                                               String url) {

        return groupImpsByPod(imps)
                .values().stream()
                .map(podImps -> splitHttpRequests(
                        bidRequest, updateRequestExtForVideo(bidRequest.getExt()), podImps, url))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private ExtRequest updateRequestExtForVideo(ExtRequest extRequest) {
        return updateRequestExt(extRequest, true, false, Long.toUnsignedString(rand.nextLong()));
    }

    private Map<String, List<Imp>> groupImpsByPod(List<Imp> processedImps) {
        return processedImps.stream()
                .collect(Collectors.groupingBy(imp -> StringUtils.substringBefore(imp.getId(), POD_SEPARATOR)));
    }

    private List<HttpRequest<BidRequest>> constructPartitionedRequests(BidRequest bidRequest,
                                                                       List<Imp> imps,
                                                                       String url,
                                                                       boolean isVideoRequest,
                                                                       boolean isAmpRequest) {

        final ExtRequest updatedExtRequest = updateRequestExt(
                bidRequest.getExt(), isVideoRequest, isAmpRequest, null);

        return splitHttpRequests(bidRequest, updatedExtRequest, imps, url);
    }

    private ExtRequest updateRequestExt(ExtRequest extRequest,
                                        boolean isVideoRequest,
                                        boolean isAmpRequest,
                                        String adPodId) {

        final Boolean includeBrandCategory = isIncludeBrandCategory(extRequest);
        final AppnexusReqExtAppnexus appnexus = AppnexusReqExtAppnexus.builder()
                .includeBrandCategory(includeBrandCategory)
                .brandCategoryUniqueness(includeBrandCategory)
                .isAmp(BooleanUtils.toInteger(isAmpRequest))
                .adpodId(adPodId)
                .headerBiddingSource(headerBiddingSource + BooleanUtils.toInteger(isVideoRequest))
                .build();

        final ExtRequestPrebid extRequestPrebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);
        final ObjectNode appnexusNode = mapper.mapper().createObjectNode()
                .set("appnexus", mapper.mapper().valueToTree(appnexus));

        return mapper.fillExtension(ExtRequest.of(extRequestPrebid), appnexusNode);
    }

    private static Boolean isIncludeBrandCategory(ExtRequest extRequest) {
        final ExtRequestPrebid prebid = extRequest != null ? extRequest.getPrebid() : null;
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;
        return targeting != null && targeting.getIncludebrandcategory() != null ? true : null;
    }

    private List<HttpRequest<BidRequest>> splitHttpRequests(BidRequest bidRequest,
                                                            ExtRequest requestExt,
                                                            List<Imp> imps,
                                                            String url) {

        return ListUtils.partition(imps, MAX_IMP_PER_REQUEST)
                .stream()
                .map(impsChunk -> createHttpRequest(bidRequest, requestExt, impsChunk, url))
                .collect(Collectors.toList());
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest,
                                                      ExtRequest requestExt,
                                                      List<Imp> imps,
                                                      String url) {

        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(imps)
                .ext(requestExt)
                .build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .body(mapper.encodeToBytes(outgoingRequest))
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> toBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid toBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final AppnexusBidExtAppnexus appnexus;
        try {
            appnexus = parseAppnexusBidExt(bid.getExt()).getAppnexus();
        } catch (IllegalArgumentException | JsonProcessingException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        if (appnexus == null) {
            errors.add(BidderError.badServerResponse("bidResponse.bid.ext.appnexus should be defined"));
            return null;
        }

        final String iabCategory = iabCategory(appnexus.getBrandCategoryId());

        List<String> cat = bid.getCat();
        if (iabCategory != null) {
            cat = List.of(iabCategory);
        } else if (CollectionUtils.isNotEmpty(bid.getCat())) {
            // create empty categories array to force bid to be rejected
            cat = Collections.emptyList();
        }

        return BidderBid.of(
                bid.toBuilder().cat(cat).build(),
                bidType(appnexus.getBidAdType()),
                currency,
                appnexus.getDealPriority(),
                makeExtBidVideo(appnexus));
    }

    private static ExtBidPrebidVideo makeExtBidVideo(AppnexusBidExtAppnexus extAppnexus) {
        final AppnexusBidExtCreative appnexusBidExtCreative = extAppnexus.getCreativeInfo();
        final AppnexusBidExtVideo appnexusBidExtVideo =
                ObjectUtil.getIfNotNull(appnexusBidExtCreative, AppnexusBidExtCreative::getVideo);
        final Integer duration = appnexusBidExtVideo != null ? appnexusBidExtVideo.getDuration() : null;
        return duration != null ? ExtBidPrebidVideo.of(duration, null) : null;
    }

    private static String iabCategory(Integer brandId) {
        return brandId != null ? IAB_CATEGORIES.get(brandId) : null;
    }

    private static BidType bidType(Integer bidAdType) {
        if (bidAdType == null) {
            throw new PreBidException("bidResponse.bid.ext.appnexus.bid_ad_type should be defined");
        }

        switch (bidAdType) {
            case 0:
                return BidType.banner;
            case 1:
                return BidType.video;
            case 2:
                return BidType.audio;
            case 3:
                return BidType.xNative;
            default:
                throw new PreBidException(
                        String.format("Unrecognized bid_ad_type in response from appnexus: %s", bidAdType));
        }
    }

    private AppnexusBidExt parseAppnexusBidExt(ObjectNode bidExt) throws JsonProcessingException {
        if (bidExt == null) {
            throw new PreBidException("bidResponse.bid.ext should be defined for appnexus");
        }

        return mapper.mapper().treeToValue(bidExt, AppnexusBidExt.class);
    }
}
