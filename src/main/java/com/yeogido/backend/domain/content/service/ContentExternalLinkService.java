package com.yeogido.backend.domain.content.service;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentLinkSource;
import com.yeogido.backend.domain.content.enums.ContentLinkType;
import java.util.List;

public interface ContentExternalLinkService {

    void replace(
            Content content,
            ContentLinkSource source,
            List<LinkCommand> links
    );

    record LinkCommand(
            ContentLinkType type,
            String label,
            String url
    ) {
    }
}
