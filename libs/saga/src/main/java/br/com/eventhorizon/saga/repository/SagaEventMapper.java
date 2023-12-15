package br.com.eventhorizon.saga.repository;

import br.com.eventhorizon.common.common.DefaultErrors;
import br.com.eventhorizon.common.exception.ServerErrorException;
import br.com.eventhorizon.common.util.DateTimeUtils;
import br.com.eventhorizon.saga.SagaEvent;
import br.com.eventhorizon.saga.SagaHeaders;
import br.com.eventhorizon.saga.SagaIdempotenceId;
import br.com.eventhorizon.saga.content.serialization.SagaContentSerializer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SagaEventMapper {

    public static SagaEventEntity modelToEntity(SagaEvent event, SagaContentSerializer serializer) {
        var builder = SagaEventEntity.builder()
                .id(event.id())
                .originalIdempotenceId(event.originalIdempotenceId().toString())
                .idempotenceId(event.idempotenceId().toString())
                .traceId(event.traceId())
                .destination(event.destination())
                .createdAt(DateTimeUtils.offsetDateTimeToString(event.createdAt()))
                .replyOkTo(event.replyOkTo())
                .replyNotOkTo(event.replyNotOkTo())
                .content(serializer.serialize(event.content()))
                .contentType(event.content().getContent().getClass().getName())
                .publishCount(event.publishCount());
        event.headers().forEach(header -> builder.header(header.getKey(), header.getValue()));
        return builder.build();
    }

    public static SagaEvent entityToModel(SagaEventEntity event, SagaContentSerializer serializer) {
        try {
            return SagaEvent.builder()
                    .id(event.getId())
                    .originalIdempotenceId(SagaIdempotenceId.of(event.getOriginalIdempotenceId()))
                    .idempotenceId(SagaIdempotenceId.of(event.getIdempotenceId()))
                    .traceId(event.getTraceId())
                    .destination(event.getDestination())
                    .createdAt(DateTimeUtils.stringToOffsetDateTime(event.getCreatedAt()))
                    .replyOkTo(event.getReplyOkTo())
                    .replyNotOkTo(event.getReplyNotOkTo())
                    .headers(SagaHeaders.builder().headers(event.getHeaders()).build())
                    .content(serializer.deserialize(event.getContent(), Class.forName(event.getContentType())))
                    .publishCount(event.getPublishCount())
                    .build();
        } catch (ClassNotFoundException e) {
            var message = "Cannot map SAGA event from repository model to business model, invalid content type " + event.getContentType();
            log.error(message, e);
            throw new ServerErrorException(DefaultErrors.UNEXPECTED_SERVER_ERROR.getCode(), message);
        }
    }
}
