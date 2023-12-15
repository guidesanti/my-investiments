package br.com.eventhorizon.saga.chain.filter;

import br.com.eventhorizon.common.exception.ClientErrorException;
import br.com.eventhorizon.saga.SagaMessage;
import br.com.eventhorizon.saga.SagaOutput;
import br.com.eventhorizon.saga.chain.SagaChain;
import br.com.eventhorizon.saga.chain.SagaPhase;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SagaIdempotenceFilter implements SagaFilter {

    @Override
    public int order() {
        return SagaPhase.REPOSITORY_TRANSACTION.after();
    }

    @Override
    public SagaOutput filter(List<SagaMessage> messages, SagaChain chain) throws Exception {
        try {
            log.info("SAGA IDEMPOTENCE FILTER START");

            var repository = chain.repository();
            var checker = chain.checker();
            var serializer = chain.serializer();
            var notProcessedMessages = new ArrayList<SagaMessage>();
            var outputBuilder = SagaOutput.builder();

            for (var message : messages) {
                var checksum = checker.checksum(message);
                var transaction = repository.findTransaction(message.idempotenceId().toString());
                if (transaction == null) {
                    if (repository.createTransaction(message.idempotenceId().toString(), checksum)) {
                        notProcessedMessages.add(message);
                    } else {
                        throw new RuntimeException("Failed to create SAGA transaction for idempotence ID " + message.idempotenceId().toString());
                    }
                } else if (transaction.getChecksum().equalsIgnoreCase(checksum)) {
                    log.warn("Idempotence ID already processed, will return previous output");
                    outputBuilder.response(repository.findResponse(message.idempotenceId().toString(), serializer));
                    outputBuilder.events(repository.findEvents(message.idempotenceId().toString(), serializer));
                } else {
                    throw new ClientErrorException("IDEMPOTENCE_ID_CONFLICT",
                            "Idempotence ID conflict, idempotence ID '" + message.idempotenceId()
                                    + "'already used for a different request");
                }
            }

            if (!notProcessedMessages.isEmpty()) {
                var output = chain.next(notProcessedMessages);
                outputBuilder.response(output.response());
                outputBuilder.events(output.events());
                if (output.response() != null) {
                    repository.createResponse(output.response(), serializer);
                }
                repository.createEvents(output.events(), serializer);
            }

            return outputBuilder.build();
        }  finally {
            log.info("SAGA IDEMPOTENCE FILTER END");
        }
    }
}
