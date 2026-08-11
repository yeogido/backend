package com.yeogido.backend.domain.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.content.dto.TourContentSyncDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentExternalLink;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentLinkSource;
import com.yeogido.backend.domain.content.enums.ContentLinkType;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.content.repository.ContentExternalLinkRepository;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TourContentSyncServiceImpl implements TourContentSyncService {

    private static final DateTimeFormatter TOUR_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
            "<a\\b[^>]*href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s<\\\"']+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern KOREAN_PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(0\\d{1,2})[-.\\s]?(\\d{3,4})(?:[-.\\s]?(\\d{4}))?(?!\\d)"
    );
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final TourApiClient tourApiClient;
    private final ContentRepository contentRepository;
    private final ContentExternalLinkRepository contentExternalLinkRepository;
    private final PlaceRepository placeRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;

    @Value("${app.tour-api.sync-months:12}")
    private int syncMonths;

    @Value("${app.tour-api.sync-page-size:100}")
    private int pageSize;

    @Value("${app.tour-api.sync-max-pages:50}")
    private int maxPages;

    @Value("${app.tour-api.sync-max-detail-calls:800}")
    private int maxDetailCalls;

    @Override
    @Transactional
    public TourContentSyncDTO.Result synchronize() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(syncMonths);
        int receivedCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        AtomicInteger remainingDetailCalls = new AtomicInteger(maxDetailCalls);

        for (int page = 1; page <= maxPages; page++) {
            JsonNode response = tourApiClient.searchFestivals(
                    startDate,
                    endDate,
                    page,
                    pageSize
            );
            List<JsonNode> items = tourApiClient.items(response);

            if (items.isEmpty()) {
                break;
            }

            for (JsonNode item : items) {
                receivedCount++;

                try {
                    SyncOutcome outcome = synchronizeItem(item, remainingDetailCalls);
                    if (outcome == SyncOutcome.CREATED) {
                        createdCount++;
                    } else if (outcome == SyncOutcome.UPDATED) {
                        updatedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (RuntimeException exception) {
                    skippedCount++;
                    log.warn(
                            "Tour content synchronization skipped. contentId={}",
                            text(item, "contentid"),
                            exception
                    );
                }
            }

            int totalCount = tourApiClient.totalCount(response);
            if ((long) page * pageSize >= totalCount) {
                break;
            }
        }

        if (receivedCount > 0 && createdCount == 0 && updatedCount == 0) {
            log.error(
                    "Tour content synchronization failed. All received items were skipped. "
                            + "receivedCount={}, skippedCount={}",
                    receivedCount,
                    skippedCount
            );
            throw new GeneralException(
                    ContentErrorCode.TOUR_CONTENT_SYNC_FAILED
            );
        }

        TourContentSyncDTO.Result result = new TourContentSyncDTO.Result(
                receivedCount,
                createdCount,
                updatedCount,
                skippedCount,
                LocalDateTime.now()
        );
        log.info("Tour content synchronization completed. result={}", result);
        return result;
    }

    @Override
    @Transactional
    public TourContentSyncDTO.Result synchronize(Long userId) {
        validateAdmin(userId);
        return synchronize();
    }

    private SyncOutcome synchronizeItem(
            JsonNode item,
            AtomicInteger remainingDetailCalls
    ) {
        String contentId = text(item, "contentid");
        String title = text(item, "title");
        String address = text(item, "addr1");
        BigDecimal latitude = decimal(text(item, "mapy"));
        BigDecimal longitude = decimal(text(item, "mapx"));

        if (
                !StringUtils.hasText(contentId)
                        || !StringUtils.hasText(title)
                        || !StringUtils.hasText(address)
                        || latitude == null
                        || longitude == null
        ) {
            return SyncOutcome.SKIPPED;
        }

        Region region = findRegionByAddress(address);
        Place place = getOrCreatePlace(
                contentId,
                title,
                address,
                text(item, "addr2"),
                latitude,
                longitude,
                region
        );

        Optional<Content> existingContent =
                contentRepository.findBySourceAndExternalContentId(
                        ContentSource.TOUR_API,
                        contentId
                );

        String thumbnailImage = firstText(
                text(item, "firstimage"),
                text(item, "firstimage2")
        );
        LocalDate startDate = date(text(item, "eventstartdate"));
        LocalDate endDate = date(text(item, "eventenddate"));
        String contactPhone = firstPhoneNumber(text(item, "tel"));

        DetailData detailData = null;
        boolean needsDetail = existingContent
                .map(content -> content.getExternalDetailsSyncedAt() == null)
                .orElse(true);

        if (needsDetail && reserveDetailCall(remainingDetailCalls)) {
            detailData = fetchDetail(contentId);
        }

        if (existingContent.isPresent()) {
            Content content = existingContent.get();
            content.update(
                    place,
                    contentId,
                    truncate(title, 100),
                    null,
                    thumbnailImage,
                    startDate,
                    endDate,
                    contactPhone,
                    ContentCategory.FESTIVAL,
                    ContentSource.TOUR_API
            );

            if (detailData != null) {
                content.updateExternalDetails(
                        detailData.description(),
                        LocalDateTime.now()
                );
                replaceExternalLinks(content, detailData.links());
            }
            return SyncOutcome.UPDATED;
        }

        Content newContent = Content.builder()
                        .place(place)
                        .externalContentId(contentId)
                        .source(ContentSource.TOUR_API)
                        .title(truncate(title, 100))
                        .thumbnailImage(thumbnailImage)
                        .startDate(startDate)
                        .endDate(endDate)
                        .contactPhone(contactPhone)
                        .category(ContentCategory.FESTIVAL)
                        .build();

        if (detailData != null) {
            newContent.updateExternalDetails(
                    detailData.description(),
                    LocalDateTime.now()
            );
        }

        contentRepository.save(newContent);
        if (detailData != null) {
            replaceExternalLinks(newContent, detailData.links());
        }
        return SyncOutcome.CREATED;
    }

    private DetailData fetchDetail(String contentId) {
        try {
            JsonNode common = tourApiClient.items(
                            tourApiClient.detailCommon(contentId)
                    )
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (common == null) {
                return null;
            }

            List<LinkData> links = extractLinks(text(common, "homepage"));

            return new DetailData(
                    plainText(text(common, "overview")),
                    links
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Tour content detail synchronization failed. contentId={}",
                    contentId,
                    exception
            );
            return null;
        }
    }

    private void replaceExternalLinks(Content content, List<LinkData> links) {
        contentExternalLinkRepository.deleteAllByContentIdAndSource(
                content.getId(),
                ContentLinkSource.TOUR_API
        );

        if (links.isEmpty()) {
            return;
        }

        List<ContentExternalLink> entities = java.util.stream.IntStream
                .range(0, links.size())
                .mapToObj(index -> {
                    LinkData link = links.get(index);
                    return ContentExternalLink.builder()
                            .content(content)
                            .type(link.type())
                            .source(ContentLinkSource.TOUR_API)
                            .label(truncate(link.label(), 100))
                            .url(truncate(link.url(), 1000))
                            .displayOrder(index)
                            .build();
                })
                .toList();

        contentExternalLinkRepository.saveAll(entities);
    }

    private List<LinkData> extractLinks(String homepageValue) {
        if (!StringUtils.hasText(homepageValue)) {
            return List.of();
        }

        String unescaped = HtmlUtils.htmlUnescape(homepageValue);
        Map<String, LinkData> links = new LinkedHashMap<>();
        Matcher anchorMatcher = ANCHOR_PATTERN.matcher(unescaped);

        while (anchorMatcher.find()) {
            addLink(
                    links,
                    anchorMatcher.group(1),
                    plainText(anchorMatcher.group(2))
            );
        }

        Matcher urlMatcher = URL_PATTERN.matcher(unescaped);
        while (urlMatcher.find()) {
            addLink(links, urlMatcher.group(), null);
        }

        return List.copyOf(links.values());
    }

    private void addLink(
            Map<String, LinkData> links,
            String rawUrl,
            String rawLabel
    ) {
        String url = normalizeUrl(rawUrl);
        if (!StringUtils.hasText(url)) {
            return;
        }

        ContentLinkType type = classifyLink(url);
        String label = StringUtils.hasText(rawLabel)
                ? rawLabel
                : defaultLabel(type);
        links.putIfAbsent(url, new LinkData(type, label, url));
    }

    private String normalizeUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }

        String url = HtmlUtils.htmlUnescape(rawUrl).trim();
        while (url.endsWith(".") || url.endsWith(",") || url.endsWith(";") || url.endsWith(")")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private ContentLinkType classifyLink(String url) {
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains("instagram.com")) {
            return ContentLinkType.INSTAGRAM;
        }
        if (lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.com")) {
            return ContentLinkType.FACEBOOK;
        }
        if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
            return ContentLinkType.YOUTUBE;
        }
        if (lowerUrl.contains("blog.naver.com") || lowerUrl.contains("blog.daum.net")) {
            return ContentLinkType.BLOG;
        }
        return ContentLinkType.OFFICIAL_WEBSITE;
    }

    private String defaultLabel(ContentLinkType type) {
        return switch (type) {
            case OFFICIAL_WEBSITE -> "공식 홈페이지";
            case INSTAGRAM -> "공식 인스타그램";
            case FACEBOOK -> "공식 페이스북";
            case YOUTUBE -> "공식 유튜브";
            case BLOG -> "공식 블로그";
            case ETC -> "관련 링크";
        };
    }

    private boolean reserveDetailCall(AtomicInteger remainingDetailCalls) {
        while (true) {
            int remaining = remainingDetailCalls.get();
            if (remaining <= 0) {
                return false;
            }
            if (remainingDetailCalls.compareAndSet(remaining, remaining - 1)) {
                return true;
            }
        }
    }

    private Place getOrCreatePlace(
            String contentId,
            String title,
            String roadAddress,
            String lotAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            Region region
    ) {
        Optional<Place> existingPlace = placeRepository
                .findBySourceAndExternalPlaceId(PlaceSource.TOUR_API, contentId);

        if (existingPlace.isPresent()) {
            Place place = existingPlace.get();
            place.updateTourData(
                    region,
                    truncate(title, 100),
                    roadAddress,
                    lotAddress,
                    latitude,
                    longitude
            );
            return place;
        }

        return placeRepository.save(
                Place.builder()
                        .region(region)
                        .externalPlaceId(contentId)
                        .source(PlaceSource.TOUR_API)
                        .name(truncate(title, 100))
                        .roadAddress(roadAddress)
                        .lotAddress(lotAddress)
                        .latitude(latitude)
                        .longitude(longitude)
                        .build()
        );
    }

    private Region findRegionByAddress(String address) {
        String[] addressParts = address.trim().split("\\s+");
        if (addressParts.length < 2) {
            throw new IllegalArgumentException("Invalid tour content address");
        }

        Region region = regionRepository.findByFullName(addressParts[0])
                .or(() -> regionRepository.findByName(addressParts[0]))
                .orElseThrow(() -> new IllegalArgumentException("Unknown region"));

        return regionRepository.findByParentAndName(region, addressParts[1])
                .orElseThrow(() -> new IllegalArgumentException("Unknown sub-region"));
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private BigDecimal decimal(String value) {
        try {
            return StringUtils.hasText(value) ? new BigDecimal(value) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate date(String value) {
        try {
            return StringUtils.hasText(value)
                    ? LocalDate.parse(value, TOUR_DATE_FORMATTER)
                    : null;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    static String firstPhoneNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        Matcher matcher = KOREAN_PHONE_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }

        StringBuilder phoneNumber = new StringBuilder()
                .append(matcher.group(1))
                .append("-")
                .append(matcher.group(2));

        if (matcher.group(3) != null) {
            phoneNumber.append("-").append(matcher.group(3));
        }

        return phoneNumber.toString();
    }

    private String plainText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return HtmlUtils.htmlUnescape(
                HTML_TAG_PATTERN.matcher(value).replaceAll(" ")
        ).replaceAll("\\s+", " ").trim();
    }

    private void validateAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        if (user.getRole() != UserRole.ADMIN) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private enum SyncOutcome {
        CREATED,
        UPDATED,
        SKIPPED
    }

    private record DetailData(
            String description,
            List<LinkData> links
    ) {
    }

    private record LinkData(
            ContentLinkType type,
            String label,
            String url
    ) {
    }
}
