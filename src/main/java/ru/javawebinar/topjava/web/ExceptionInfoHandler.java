package ru.javawebinar.topjava.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.javawebinar.topjava.util.ValidationUtil;
import ru.javawebinar.topjava.util.exception.ErrorInfo;
import ru.javawebinar.topjava.util.exception.ErrorType;
import ru.javawebinar.topjava.util.exception.IllegalRequestDataException;
import ru.javawebinar.topjava.util.exception.NotFoundException;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import static ru.javawebinar.topjava.util.ValidationUtil.getErrorResponseMsg;
import static ru.javawebinar.topjava.util.exception.ErrorType.APP_ERROR;
import static ru.javawebinar.topjava.util.exception.ErrorType.DATA_ERROR;
import static ru.javawebinar.topjava.util.exception.ErrorType.DATA_NOT_FOUND;
import static ru.javawebinar.topjava.util.exception.ErrorType.VALIDATION_ERROR;

@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ExceptionInfoHandler {
    private static final Logger log = LoggerFactory.getLogger(ExceptionInfoHandler.class);
    public static final String DUPLICATE_EMAIL_MSG = "[email] User with this email already exists";
    public static final String DUPLICATE_DATETIME_MSG = "[dateTime] Meal at this date&time already exists";

    //  http://stackoverflow.com/a/22358422/548473
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ExceptionHandler(NotFoundException.class)
    public ErrorInfo handleError(HttpServletRequest req, NotFoundException e) {
        return logAndGetErrorInfo(req, e, false, DATA_NOT_FOUND, e.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)  // 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ErrorInfo conflict(HttpServletRequest req, DataIntegrityViolationException e) {
        boolean exceptionFromUsers = ValidationUtil.getRootCause(e).getMessage().toLowerCase().contains("users");
        String duplicateMsg = exceptionFromUsers ? DUPLICATE_EMAIL_MSG : DUPLICATE_DATETIME_MSG;
        return logAndGetErrorInfo(req, e, true, DATA_ERROR, duplicateMsg);
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)  // 422
    @ExceptionHandler({
            IllegalRequestDataException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, BindException.class
    })
    public ErrorInfo illegalRequestDataError(HttpServletRequest req, Exception e) {
        List<String> details = new ArrayList<>();
        if (e instanceof BindException) {
            details.addAll(getErrorResponseMsg(((BindException) e).getBindingResult()));
        } else {
            details.add(ValidationUtil.getRootCause(e).getMessage());
        }
        return logAndGetErrorInfo(req, e, false, VALIDATION_ERROR, details.toArray(new String[0]));
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorInfo handleError(HttpServletRequest req, Exception e) {
        return logAndGetErrorInfo(req, e, true, APP_ERROR);
    }

    private static ErrorInfo logAndGetErrorInfo(HttpServletRequest req, Exception e, boolean logException,
                                                ErrorType errorType, String... details) {
        Throwable rootCause = ValidationUtil.getRootCause(e);

        if (logException) {
            log.error(errorType + " at request " + req.getRequestURL(), rootCause);
        } else {
            log.warn("{} at request  {}: {}", errorType, req.getRequestURL(), rootCause.toString());
        }
        return new ErrorInfo(req.getRequestURL(), errorType, ObjectUtils.isEmpty(details)
                                                             ? new String[]{rootCause.toString()}
                                                             : details);
    }
}