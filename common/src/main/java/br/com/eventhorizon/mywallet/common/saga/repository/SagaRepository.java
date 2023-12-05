package br.com.eventhorizon.mywallet.common.saga.repository;

import br.com.eventhorizon.mywallet.common.saga.SagaEvent;
import br.com.eventhorizon.mywallet.common.saga.SagaResponse;
import br.com.eventhorizon.mywallet.common.saga.content.serializer.SagaContentSerializer;
import br.com.eventhorizon.mywallet.common.transaction.TransactionManager;

import java.util.List;
import java.util.Map;

public interface SagaRepository extends TransactionManager {

    boolean createTransaction(String idempotenceId, String checksum);

    SagaTransactionEntity findTransaction(String idempotenceId);

    void createResponse(SagaResponse response, Map<Class<?>, SagaContentSerializer> serializers);

    SagaResponse findResponse(String idempotenceId, Map<Class<?>, SagaContentSerializer> serializers);

    void createEvents(List<SagaEvent> events, Map<Class<?>, SagaContentSerializer> serializers);

    List<SagaEvent> findEvents(String idempotenceId, Map<Class<?>, SagaContentSerializer> serializers);
}
