package br.com.eventhorizon.mywallet.common.validation;

import lombok.NonNull;

import java.util.List;

public interface Validator<T> {

    List<ValidationError> validate(@NonNull T target);
}
