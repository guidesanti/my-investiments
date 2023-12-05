package br.com.eventhorizon.mywallet.common.validation;

import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public class ValidationError {

    String field;

    ValidationErrorCode code;

    String message;
}
