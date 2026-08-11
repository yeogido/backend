package com.yeogido.backend.domain.content.entity;

import com.yeogido.backend.domain.content.enums.ContentPublicationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentPublicationStatusTest {

    @Test
    void manuallyCreatedContentIsPublishedByDefault() {
        Content content = Content.builder().build();

        assertThat(content.getPublicationStatus())
                .isEqualTo(ContentPublicationStatus.PUBLISHED);
    }

    @Test
    void pendingContentCanBePublished() {
        Content content = Content.builder()
                .publicationStatus(ContentPublicationStatus.PENDING)
                .build();

        content.publish();

        assertThat(content.getPublicationStatus())
                .isEqualTo(ContentPublicationStatus.PUBLISHED);
    }
}
