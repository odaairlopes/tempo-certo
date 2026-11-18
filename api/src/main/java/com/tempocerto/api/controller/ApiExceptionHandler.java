package com.tempocerto.api.controller;

import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail entradaInvalida(MethodArgumentNotValidException exception) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Confira os campos informados.");
        problema.setProperty("campos", exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField()).distinct().toList());
        return problema;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail regraDeNegocio(ResponseStatusException exception) {
        return exception.getBody();
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail jsonInvalido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Confira o formato dos dados enviados.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail falhaInesperada(Exception exception) {
        if (exception instanceof ErrorResponse erro && erro.getStatusCode().is4xxClientError()) {
            return ProblemDetail.forStatusAndDetail(erro.getStatusCode(), "Confira o formato da requisição.");
        }
        LOGGER.error("Falha inesperada na API: {}", exception.getClass().getSimpleName());
        // Mensagens do driver podem incluir dados da reserva.
        Sentry.captureMessage("Falha na API: " + exception.getClass().getSimpleName(), io.sentry.SentryLevel.ERROR);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação.");
    }
}
