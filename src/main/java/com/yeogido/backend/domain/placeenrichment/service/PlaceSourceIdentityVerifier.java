package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.dto.response.DaumSearchResponse;
import com.yeogido.backend.domain.placeenrichment.util.AddressNormalizer;
import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class PlaceSourceIdentityVerifier {
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])0[0-9]{1,2}[- .]?[0-9]{3,4}[- .]?[0-9]{4}(?![0-9])");
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+");
    private static final Pattern ROAD = Pattern.compile("[가-힣0-9·]+(?:대로|로|길)\\s*[0-9]+(?:-[0-9]+)?(?![0-9-]|번?길)");

    private PlaceSourceIdentityVerifier() { }

    static boolean matches(TourPlaceRequest.Input input, DaumSearchResponse.SearchSource source) {
        String text = source.title() + " " + source.snippet();
        String name = Pattern.quote(input.name().strip()).replace(" ", "\\E\\s*\\Q");
        if (!Pattern.compile("(?<![\\p{L}\\p{N}])" + name + "(?![\\p{L}\\p{N}])").matcher(text).find()) {
            return false;
        }
        if (containsAddress(text, input.roadAddress()) || containsAddress(text, input.lotAddress())) {
            var expectedRoad = ROAD.matcher(StringUtils.hasText(input.roadAddress()) ? input.roadAddress() : "");
            if (!expectedRoad.find()) {
                // 지번 주소만으로 식별된 자료에 별도 도로명 주소가 있으면 보수적으로 제외한다.
                return !ROAD.matcher(text).find();
            }
            String expected = expectedRoad.group().replaceAll("\\s+", "");
            String compactAddress = AddressNormalizer.normalize(input.roadAddress()).replaceAll("\\s+", "");
            return ROAD.matcher(AddressNormalizer.normalize(text)).results().allMatch(match -> {
                String road = match.group().replaceAll("\\s+", "");
                return road.equals(expected) || road.equals(compactAddress);
            });
        }
        // 다른 주소가 명시된 자료를 전화번호나 공용 홈페이지로 우회 승인하지 않는다.
        if (ROAD.matcher(text).find()) {
            return false;
        }
        if (StringUtils.hasText(input.phone())) {
            String phone = input.phone().replaceAll("[- .]", "");
            if (phone.matches("0[0-9]{8,10}") && PHONE.matcher(text).results()
                    .anyMatch(match -> match.group().replaceAll("[- .]", "").equals(phone))) {
                return true;
            }
        }
        String official = canonicalUrl(input.officialUrl());
        return official != null && (official.equals(canonicalUrl(source.url()))
                || URL.matcher(text).results().anyMatch(match -> official.equals(canonicalUrl(match.group()))));
    }

    static boolean containsAddress(String text, String address) {
        if (!StringUtils.hasText(address)) {
            return false;
        }
        String pattern = Pattern.quote(AddressNormalizer.normalize(address)).replace(" ", "\\E\\s*\\Q");
        return Pattern.compile(pattern + "(?![0-9-]|번?길)")
                .matcher(AddressNormalizer.normalize(text)).find();
    }

    private static String canonicalUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                return null;
            }
            // 경로와 쿼리는 장소 식별자의 일부이므로 호스트만으로 비교하지 않는다.
            return new URI(uri.getScheme(), null, uri.getHost().toLowerCase(java.util.Locale.ROOT),
                    uri.getPort(), uri.getPath().isEmpty() ? "/" : uri.getPath(), uri.getQuery(), null).toString();
        } catch (IllegalArgumentException | java.net.URISyntaxException exception) {
            return null;
        }
    }
}
