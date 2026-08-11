package com.yeogido.backend.domain.content.service;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentExternalLink;
import com.yeogido.backend.domain.content.enums.ContentLinkSource;
import com.yeogido.backend.domain.content.repository.ContentExternalLinkRepository;
import java.util.List;
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

        contentExternalLinkRepository.deleteAllByContentIdAndSource(
                content.getId(),
                source
        );

        List<ContentExternalLink> entities = IntStream.range(0, links.size())
                .mapToObj(index -> {
                    LinkCommand link = links.get(index);
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
