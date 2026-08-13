package com.yeogido.backend.domain.content.service;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentExternalLink;
import com.yeogido.backend.domain.content.enums.ContentLinkSource;
import com.yeogido.backend.domain.content.repository.ContentExternalLinkRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContentExternalLinkServiceImpl implements ContentExternalLinkService {

    private final ContentExternalLinkRepository contentExternalLinkRepository;

    @Override
    public void replace(
            Content content,
            ContentLinkSource source,
            List<LinkCommand> links
    ) {
        if (links == null) {
            return;
        }

        List<LinkCommand> distinctLinks = links.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                LinkCommand::url,
                                Function.identity(),
                                (first, ignored) -> first,
                                LinkedHashMap::new
                        ),
                        linksByUrl -> List.copyOf(linksByUrl.values())
                ));

        List<LinkCommand> linksToSave;
        if (source == ContentLinkSource.ADMIN) {
            contentExternalLinkRepository.deleteAllByContentId(content.getId());
            linksToSave = distinctLinks;
        } else {
            Set<String> adminUrls = contentExternalLinkRepository
                    .findAllByContentIdAndSource(
                            content.getId(),
                            ContentLinkSource.ADMIN
                    )
                    .stream()
                    .map(ContentExternalLink::getUrl)
                    .collect(Collectors.toSet());

            contentExternalLinkRepository.deleteAllByContentIdAndSource(
                    content.getId(),
                    source
            );

            linksToSave = distinctLinks.stream()
                    .filter(link -> !adminUrls.contains(link.url()))
                    .toList();
        }

        List<ContentExternalLink> entities = IntStream.range(0, linksToSave.size())
                .mapToObj(index -> {
                    LinkCommand link = linksToSave.get(index);
                    return ContentExternalLink.builder()
                            .content(content)
                            .type(link.type())
                            .source(source)
                            .label(link.label())
                            .url(link.url())
                            .displayOrder(index)
                            .build();
                })
                .toList();

        contentExternalLinkRepository.saveAll(entities);
    }
}
