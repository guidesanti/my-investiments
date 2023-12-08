package br.com.eventhorizon.mywallet.common.saga.repository.impl;

import br.com.eventhorizon.mywallet.common.saga.SagaEvent;
import br.com.eventhorizon.mywallet.common.saga.SagaOption;
import br.com.eventhorizon.mywallet.common.saga.SagaResponse;
import br.com.eventhorizon.mywallet.common.saga.chain.SagaOptions;
import br.com.eventhorizon.mywallet.common.saga.content.serializer.SagaContentSerializer;
import br.com.eventhorizon.mywallet.common.saga.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static br.com.eventhorizon.mywallet.common.saga.Conventions.DEFAULT_DATE_TIME_FORMATTER;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Slf4j
@Repository
public class MongoDBSagaRepository implements SagaRepository {

    private static final String ORIGINAL_IDEMPOTENCE_ID_FIELD_NAME = "originalIdempotenceId";

    private static final String IDEMPOTENCE_ID_FIELD_NAME = "idempotenceId";

    private static final String EXPIRE_AT_FIELD_NAME = "expireAt";

    private static final String PUBLISH_COUNT_FIELD_NAME = "publishCount";

    private static final String ORIGINAL_IDEMPOTENCE_ID_INDEX_NAME = "originalIdempotenceIdIndex";

    private static final String IDEMPOTENCE_ID_INDEX_NAME = "idempotenceIdIndex";

    private static final String EXPIRE_AT_INDEX_NAME = "expireAtIndex";

    private final MongoTemplate mongoTemplate;

    private SagaOptions options;

    public MongoDBSagaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean createTransaction(String idempotenceId, String checksum) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var transactionEntity = SagaTransactionEntity.builder()
                .idempotenceId(idempotenceId)
                .checksum(checksum)
                .createdAt(now.format(DEFAULT_DATE_TIME_FORMATTER))
                .expireAt(getExpirationDate(now))
                .build();
        try {
            mongoTemplate.insert(transactionEntity, options.get(SagaOption.TRANSACTION_COLLECTION_NAME));
            return true;
        } catch (DuplicateKeyException ex) {
            log.warn("SAGA transaction already exists with same idempotence ID: {}", transactionEntity);
            return false;
        } catch (Exception ex) {
            log.error("Failed to create SAGA transaction on DB", ex);
            throw ex;
        }
    }

    @Override
    public SagaTransactionEntity findTransaction(String idempotenceId) {
        return mongoTemplate.findOne(Query.query(where(IDEMPOTENCE_ID_FIELD_NAME).is(idempotenceId)),
                SagaTransactionEntity.class, options.get(SagaOption.TRANSACTION_COLLECTION_NAME));
    }

    @Override
    public void createResponse(SagaResponse response, Map<Class<?>, SagaContentSerializer> serializers) {
        var responseEntity = SagaResponseMapper.modelToEntity(response, serializers.get(response.content().getContent().getClass()));
        responseEntity.setExpireAt(getExpirationDate(response.createdAt()));
        mongoTemplate.insert(responseEntity, options.get(SagaOption.RESPONSE_COLLECTION_NAME));
    }

    @Override
    public SagaResponse findResponse(String idempotenceId, Map<Class<?>, SagaContentSerializer> serializers) {
        var response = mongoTemplate.findOne(Query.query(where(IDEMPOTENCE_ID_FIELD_NAME).is(idempotenceId)),
                SagaResponseEntity.class, options.get(SagaOption.RESPONSE_COLLECTION_NAME));
        if (response != null) {
            try {
                return SagaResponseMapper.entityToModel(response, serializers.get(Class.forName(response.getContentType())));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    @Override
    public void createEvents(List<SagaEvent> events, Map<Class<?>, SagaContentSerializer> serializers) {
        mongoTemplate.insert(events.stream()
                        .map(e -> {
                            var eventDocument = SagaEventMapper.modelToEntity(e, serializers.get(e.content().getContent().getClass()));
                            eventDocument.setExpireAt(getExpirationDate(e.createdAt()));
                            return eventDocument;
                        })
                        .collect(Collectors.toList()),
                (String) options.get(SagaOption.EVENT_COLLECTION_NAME));
    }

    @Override
    public List<SagaEvent> findEvents(String originalIdempotenceId, Map<Class<?>, SagaContentSerializer> serializers) {
        var events = mongoTemplate.find(Query.query(where(ORIGINAL_IDEMPOTENCE_ID_FIELD_NAME).is(originalIdempotenceId)),
                SagaEventEntity.class, options.get(SagaOption.EVENT_COLLECTION_NAME));
        return events.stream().map(e -> {
            try {
                return SagaEventMapper.entityToModel(e, serializers.get(Class.forName(e.getContentType())));
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }).toList();
    }

    @Override
    public void incrementEventPublishCount(String eventId) {
        Query query = new Query().addCriteria(Criteria.where("_id").is(eventId));

        Update update = new Update();
        update.inc(PUBLISH_COUNT_FIELD_NAME);

        mongoTemplate.findAndModify(query, update, SagaEventEntity.class, options.get(SagaOption.EVENT_COLLECTION_NAME));
    }

    @Override
    public void configure(SagaOptions options) {
        this.options = options;
        ensureIndexes();
    }

    @Override
    @Transactional(rollbackFor = { Exception.class })
    public <T> T transact(Callable<T> task) throws Exception {
        log.info("Saga repository transaction start");
        var output = task.call();
        log.info("Saga repository transaction commit");
        return output;
    }

    private Date getExpirationDate(OffsetDateTime offsetDateTime) {
        return Date.from(offsetDateTime.plusSeconds(options.get(SagaOption.TRANSACTION_TTL)).toInstant());
    }

    private void ensureIndexes() {
        var indexDefinition = new Index(IDEMPOTENCE_ID_FIELD_NAME, Sort.Direction.ASC)
                .named(IDEMPOTENCE_ID_INDEX_NAME)
                .unique();
        mongoTemplate.indexOps((String) options.get(SagaOption.TRANSACTION_COLLECTION_NAME)).ensureIndex(indexDefinition);

        indexDefinition = new Index(EXPIRE_AT_FIELD_NAME, Sort.Direction.ASC)
                .named(EXPIRE_AT_INDEX_NAME)
                .expire(0);
        mongoTemplate.indexOps((String) options.get(SagaOption.TRANSACTION_COLLECTION_NAME)).ensureIndex(indexDefinition);

        indexDefinition = new Index(IDEMPOTENCE_ID_FIELD_NAME, Sort.Direction.ASC)
                .named(IDEMPOTENCE_ID_INDEX_NAME)
                .unique();
        mongoTemplate.indexOps((String) options.get(SagaOption.RESPONSE_COLLECTION_NAME)).ensureIndex(indexDefinition);

        indexDefinition = new Index(EXPIRE_AT_FIELD_NAME, Sort.Direction.ASC)
                .named(EXPIRE_AT_INDEX_NAME)
                .expire(0);
        mongoTemplate.indexOps((String) options.get(SagaOption.RESPONSE_COLLECTION_NAME)).ensureIndex(indexDefinition);

        indexDefinition = new Index(ORIGINAL_IDEMPOTENCE_ID_FIELD_NAME, Sort.Direction.ASC)
                .named(ORIGINAL_IDEMPOTENCE_ID_INDEX_NAME);
        mongoTemplate.indexOps((String) options.get(SagaOption.EVENT_COLLECTION_NAME)).ensureIndex(indexDefinition);

        indexDefinition = new Index(EXPIRE_AT_FIELD_NAME, Sort.Direction.ASC)
                .named(EXPIRE_AT_INDEX_NAME)
                .expire(0);
        mongoTemplate.indexOps((String) options.get(SagaOption.EVENT_COLLECTION_NAME)).ensureIndex(indexDefinition);
    }
}
