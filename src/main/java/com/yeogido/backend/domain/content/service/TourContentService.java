package com.yeogido.backend.domain.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.content.dto.TourContentDTO;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TourContentService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final DateTimeFormatter TOUR_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern HREF_PATTERN =
            Pattern.compile("href=[\\\"']([^\\\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final TourApiClient tourApiClient;
    private final S3Service s3Service;
    private final UserRepository userRepository;

    public TourContentDTO.SearchResponse search(
            TourContentDTO.SearchRequest request,
            Long userId
    ) {
        validateAdmin(userId);

        int page = request.page() == null ? DEFAULT_PAGE : request.page();
        int size = request.size() == null ? DEFAULT_SIZE : request.size();

        if (size > MAX_SIZE) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }

        JsonNode response = tourApiClient.searchKeyword(
                request.keyword().trim(),
                request.areaCode(),
                page,
                size
        );

        List<TourContentDTO.SearchItem> items = tourApiClient.items(response)
                .stream()
                .map(this::toSearchItem)
                .toList();

        return new TourContentDTO.SearchResponse(
                page,
                size,
                tourApiClient.totalCount(response),
                items
        );
    }

    public TourContentDTO.DetailResponse getDetail(
            String contentId,
            String contentTypeId,
            Long userId
    ) {
        validateAdmin(userId);

        JsonNode common = firstItem(tourApiClient.detailCommon(contentId));
        JsonNode intro = firstItem(tourApiClient.detailIntro(contentId, contentTypeId));
        List<JsonNode> imageItems = tourApiClient.items(
                tourApiClient.detailImages(contentId)
        );

        LinkedHashSet<String> imageUrls = new LinkedHashSet<>();
        addIfPresent(imageUrls, text(common, "firstimage"));
        imageItems.forEach(item -> addIfPresent(imageUrls, text(item, "originimgurl")));

        String title = text(common, "title");
        String firstImage = text(common, "firstimage");
        if (!StringUtils.hasText(firstImage)) {
            firstImage = imageUrls.stream().findFirst().orElse(null);
        }

        return new TourContentDTO.DetailResponse(
                contentId,
                contentTypeId,
                title,
                plainText(text(common, "overview")),
                ContentCategory.FESTIVAL,
                date(text(intro, "eventstartdate")),
                date(text(intro, "eventenddate")),
                firstText(text(intro, "sponsor1tel"), text(common, "tel")),
                homepage(text(common, "homepage")),
                firstImage,
                List.copyOf(imageUrls),
                new TourContentDTO.Place(
                        contentId,
                        PlaceSource.TOUR_API,
                        title,
                        text(common, "addr1"),
                        text(common, "addr2"),
                        decimal(text(common, "mapy")),
                        decimal(text(common, "mapx"))
                )
        );
    }

    public TourContentDTO.ImageImportResponse importImage(
            TourContentDTO.ImageImportRequest request,
            Long userId
    ) {
        validateAdmin(userId);

        String imageKey = s3Service.uploadImageFromUrl(
                request.imageUrl(),
                ImageDirectory.CONTENT
        );

        if (!StringUtils.hasText(imageKey)) {
            throw new GeneralException(ContentErrorCode.TOUR_IMAGE_IMPORT_FAILED);
        }

        return new TourContentDTO.ImageImportResponse(
                imageKey,
                s3Service.getImageUrl(imageKey)
        );
    }

    private TourContentDTO.SearchItem toSearchItem(JsonNode item) {
        return new TourContentDTO.SearchItem(
                text(item, "contentid"),
                text(item, "contenttypeid"),
                text(item, "title"),
                firstText(text(item, "addr1"), text(item, "addr2")),
                decimal(text(item, "mapy")),
                decimal(text(item, "mapx")),
                firstText(text(item, "firstimage2"), text(item, "firstimage"))
        );
    }

    private JsonNode firstItem(JsonNode response) {
        return tourApiClient.items(response)
                .stream()
                .findFirst()
                .orElseThrow(() -> new GeneralException(ContentErrorCode.TOUR_CONTENT_NOT_FOUND));
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate date(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return LocalDate.parse(value, TOUR_DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String homepage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        Matcher matcher = HREF_PATTERN.matcher(value);
        return matcher.find() ? HtmlUtils.htmlUnescape(matcher.group(1)) : plainText(value);
    }

    private String plainText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return HtmlUtils.htmlUnescape(
                HTML_TAG_PATTERN.matcher(value).replaceAll(" ")
        ).replaceAll("\\s+", " ").trim();
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private void addIfPresent(LinkedHashSet<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value);
        }
    }

    private void validateAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        if (user.getRole() != UserRole.ADMIN) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }
}
