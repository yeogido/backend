package com.yeogido.backend.domain.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.content.dto.TourContentSyncDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentSource;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TourContentSyncService {

    private static final DateTimeFormatter TOUR_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern HREF_PATTERN =
            Pattern.compile("href=[\\\"']([^\\\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final TourApiClient tourApiClient;
    private final ContentRepository contentRepository;
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
        String contactPhone = truncate(text(item, "tel"), 20);

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
                    null,
                    ContentCategory.FESTIVAL,
                    ContentSource.TOUR_API
            );

            if (detailData != null) {
                content.updateExternalDetails(
                        detailData.description(),
                        detailData.officialUrl(),
                        LocalDateTime.now()
                );
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
                    detailData.officialUrl(),
                    LocalDateTime.now()
            );
        }

        contentRepository.save(newContent);
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

            return new DetailData(
                    plainText(text(common, "overview")),
                    truncate(homepage(text(common, "homepage")), 500)
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

    private String homepage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        Matcher matcher = HREF_PATTERN.matcher(value);
        return matcher.find()
                ? HtmlUtils.htmlUnescape(matcher.group(1))
                : plainText(value);
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
            String officialUrl
    ) {
    }
}
